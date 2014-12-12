package at.ac.univie.isc.asio.config;

import at.ac.univie.isc.asio.SqlSchema;
import at.ac.univie.isc.asio.database.Schema;
import at.ac.univie.isc.asio.database.SchemaFactory;
import at.ac.univie.isc.asio.engine.*;
import at.ac.univie.isc.asio.engine.d2rq.D2rqSpec;
import at.ac.univie.isc.asio.engine.d2rq.LoadD2rqModel;
import at.ac.univie.isc.asio.engine.sql.SqlSchemaBuilder;
import at.ac.univie.isc.asio.insight.EventLoggerBridge;
import at.ac.univie.isc.asio.insight.EventStream;
import at.ac.univie.isc.asio.jaxrs.AppSpec;
import at.ac.univie.isc.asio.metadata.*;
import at.ac.univie.isc.asio.spring.SpringByteSource;
import at.ac.univie.isc.asio.tool.Duration;
import at.ac.univie.isc.asio.tool.TimeoutSpec;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Ticker;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.hp.hpl.jena.rdf.model.Model;
import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.feature.LoggingFeature;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.spring.SpringResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.context.WebApplicationContext;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import javax.servlet.ServletContext;
import javax.ws.rs.ext.RuntimeDelegate;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.*;

@Configuration
@PropertySource(value = {"classpath:/asio.properties"})
public class AsioConfiguration {
  /* slf4j-logger */
  private static final Logger log = LoggerFactory.getLogger(AsioConfiguration.class);
  private static final Marker SCOPE_SYSTEM = at.ac.univie.isc.asio.Scope.SYSTEM.marker();

  @Autowired
  private Environment env;

  // asio backend components

  // JAX-RS
  @Bean(destroyMethod = "shutdown")
  public Bus cxf() {
    return new SpringBus();
  }

  @Bean(destroyMethod = "stop")
  @DependsOn("cxf")
  public Server jaxrsServer() {
    final JAXRSServerFactoryBean factory = RuntimeDelegate.getInstance().createEndpoint(
        AppSpec.create(ProtocolResource.class, MetadataResource.class),
        JAXRSServerFactoryBean.class
    );
    log.info(SCOPE_SYSTEM, "publishing jaxrs endpoint at <{}>", factory.getAddress());
    // Use spring managed resource instances
    factory.setResourceProvider(ProtocolResource.class, protocolResourceProvider());
    factory.setResourceProvider(MetadataResource.class, metadataResourceProvider());
    factory.getFeatures().add(new LoggingFeature());  // TODO make configurable
    return factory.create();
  }

  @Bean
  public SpringResourceFactory protocolResourceProvider() {
    return new SpringResourceFactory("protocolResource");
  }

  @Bean
  public SpringResourceFactory metadataResourceProvider() {
    return new SpringResourceFactory("metadataResource");
  }

  @Bean
  @Scope(BeanDefinition.SCOPE_PROTOTYPE)
  public ProtocolResource protocolResource(final Command.Factory registry) {
    return new ProtocolResource(registry, globalTimeout(), eventBuilder());
  }

  @Bean
  public MetadataResource metadataResource(final MetadataService service, final Schema schema) {
    return new MetadataResource(new Supplier<DatasetMetadata>() {
      @Override
      public DatasetMetadata get() {
        return service.fetch(schema).onExceptionResumeNext(Observable.from(StaticMetadata.NOT_AVAILABLE)).toBlocking().single();
      }
    }, new Supplier<SqlSchema>() {
      @Override
      public SqlSchema get() {
        return service.relationalSchema(schema).toBlocking().single();
      }
    });
  }

  // asio jaxrs components
  @Bean
  public Command.Factory registry(final Schema schema) {
    log.info(SCOPE_SYSTEM, "using engines {}", schema.engines());
    final Scheduler scheduler = Schedulers.from(workerPool());
    final EngineRegistry engineRegistry = new EngineRegistry(scheduler, schema.engines());
    return new EventfulCommandDecorator(engineRegistry, eventBuilder());
  }

  @Bean
  public Schema defaultSchema(final SchemaFactory create, final ResourceLoader loader) {
    final String mappingLocation = env.getProperty("asio.d2r.mapping", "config.ttl");
    final Resource resource = loader.getResource(mappingLocation);
    final SpringByteSource turtle = SpringByteSource.asByteSource(resource);
    final Model configuration = new LoadD2rqModel().parse(turtle);
    final D2rqSpec d2rq = D2rqSpec.wrap(configuration);
    return create.fromD2rq(d2rq);
  }

  @Bean
  public MetadataService metadataService() {
    final boolean contactRemote = env.getProperty("asio.meta.enable", Boolean.class, Boolean.FALSE);
    final URI repository = URI.create(env.getRequiredProperty("asio.meta.repository"));
    final AtosMetadataService proxy = new AtosMetadataService(repository);
    return new MetadataService(proxy, contactRemote);
  }

  @Bean
  public SchemaFactory schemaFactory() {
    return new SchemaFactory(env);
  }

  @Bean
  @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.INTERFACES)
  public Supplier<EventReporter> eventBuilder() {
    final EventReporter eventReporter = new EventReporter(eventBus(), Ticker.systemTicker());
    return Suppliers.ofInstance(eventReporter);
  }

  @Bean
  public EventStream eventStream() {
    final EventStream stream =
        new EventStream(Schedulers.io(), Duration.create(1L, TimeUnit.SECONDS), 50);
    eventBus().register(stream);
    return stream;
  }

  @Bean
  public EventLoggerBridge eventLogger() {
    final EventLoggerBridge bridge = new EventLoggerBridge();
    eventBus().register(bridge);
    return bridge;
  }

  @Bean
  public EventBus eventBus() {
    return new AsyncEventBus("asio-events", eventWorker());
  }

  @Bean(destroyMethod = "shutdownNow")
  public ExecutorService eventWorker() {
    final ThreadFactory factory =
        new ThreadFactoryBuilder()
            .setNameFormat("asio-events-%d")
            .build();
    return Executors.newSingleThreadScheduledExecutor(factory);
  }

  @Bean(destroyMethod = "shutdownNow")
  public ExecutorService workerPool() {
    final ThreadFactory factory =
        new ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("asio-worker-%d")
            .build();
    final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(30);
    return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue, factory);
  }

  @Bean
  public TimeoutSpec globalTimeout() {
    Long timeout = env.getProperty("asio.timeout", Long.class, -1L);
    TimeoutSpec spec = TimeoutSpec.from(timeout, TimeUnit.SECONDS);
    log.info(SCOPE_SYSTEM, "using timeout {}", spec);
    return spec;
  }
}
