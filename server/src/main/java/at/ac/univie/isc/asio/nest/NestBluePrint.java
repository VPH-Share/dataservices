package at.ac.univie.isc.asio.nest;

import at.ac.univie.isc.asio.Scope;
import at.ac.univie.isc.asio.SqlSchema;
import at.ac.univie.isc.asio.d2rq.D2rqTools;
import at.ac.univie.isc.asio.database.DatabaseInspector;
import at.ac.univie.isc.asio.database.DefinitionService;
import at.ac.univie.isc.asio.database.Jdbc;
import at.ac.univie.isc.asio.engine.sparql.JenaEngine;
import at.ac.univie.isc.asio.engine.sql.JdbcSpec;
import at.ac.univie.isc.asio.engine.sql.JooqEngine;
import at.ac.univie.isc.asio.metadata.DescriptorService;
import at.ac.univie.isc.asio.metadata.SchemaDescriptor;
import at.ac.univie.isc.asio.spring.ExplicitWiring;
import at.ac.univie.isc.asio.tool.Beans;
import at.ac.univie.isc.asio.tool.JdbcTools;
import at.ac.univie.isc.asio.tool.Timeout;
import com.hp.hpl.jena.rdf.model.Model;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import rx.Observable;
import rx.functions.Func0;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

@Configuration
@ExplicitWiring
class NestBluePrint {
  private static final Logger log = getLogger(NestBluePrint.class);

  static final String BEAN_DEFINITION_SOURCE = "definition";
  static final String BEAN_DESCRIPTOR_SOURCE = "metadata";

  @Bean(destroyMethod = "close")
  public JooqEngine jooqEngine(final Jdbc jdbc,
                               final DataSource pool,
                               final Timeout timeout) {
    final JdbcSpec spec = JdbcSpec.connectTo(jdbc.getUrl())
        .authenticateAs(jdbc.getUrl(), jdbc.getPassword())
        .use(timeout).complete();
    return JooqEngine.create(ClosableDataSourceProxy.wrap(pool), spec);
  }

  @Bean(destroyMethod = "close")
  public JenaEngine jenaEngine(final Dataset dataset,
                               final Mapping mapping,
                               final ConnectedDB connection,
                               final Timeout timeout) {
    final Model model = D2rqTools.compile(mapping, connection);
    return JenaEngine.create(model, timeout, dataset.isFederationEnabled());
  }

  @Bean(name = BEAN_DEFINITION_SOURCE)
  public Observable<SqlSchema> definition(final Jdbc jdbc,
                                          final DefinitionService definitionService) {
    return Observable.defer(new CallDefinitionService(definitionService, jdbc.getSchema()));
  }

  @Bean(name = BEAN_DESCRIPTOR_SOURCE)
  public Observable<SchemaDescriptor> metadata(final Dataset dataset,
                                               final DescriptorService descriptorService) {
    return Observable.defer(new CallDescriptorService(descriptorService, dataset.getIdentifier()));
  }

  @Bean
  @ConditionalOnMissingBean
  public DescriptorService nullDescriptorService() {
    log.info(Scope.SYSTEM.marker(), "creating static fallback DescriptorService");
    return new DescriptorService() {
      @Override
      public Observable<SchemaDescriptor> metadata(final URI identifier) {
        return Observable.empty();
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean
  public DefinitionService localDefinitionService(final Jdbc jdbc, final DataSource dataSource) {
    log.info(Scope.SYSTEM.marker(), "creating local DefinitionService from {}", jdbc);
    return DatabaseInspector.create(jdbc.getUrl(), dataSource);
  }

  @Bean(destroyMethod = "close")
  public ConnectedDB d2rqConnection(final Jdbc jdbc) throws SQLException {
    final ConnectedDB connection = D2rqTools.createSqlConnection(jdbc.getUrl(),
        jdbc.getUsername(), jdbc.getPassword(), Beans.asProperties(jdbc.getProperties()));
    connection.switchCatalog(jdbc.getSchema());
    return connection;
  }

  @Bean(destroyMethod = "close")
  @Primary
  public DataSource dataSource(final Dataset dataset,
                               final Jdbc jdbc,
                               final Timeout timeout) {
    final HikariConfig config = JdbcTools.hikariConfig(dataset.getName().asString(), jdbc, timeout);
    return new HikariDataSource(config);
  }

  @Bean
  @Primary
  public Timeout localTimeout(final Dataset dataset, final Timeout global) {
    final Timeout local = dataset.getTimeout();
    log.debug(Scope.SYSTEM.marker(), "choosing timeout (local:{}) (global:{})", local, global);
    return local.orIfUndefined(global);
  }

  // container clean up

  @Autowired(required = false)
  private List<OnClose> closeListeners;
  @Autowired
  private NestConfig config;

  @Bean
  public NestConfig config(final Dataset dataset, final Jdbc jdbc, final Mapping mapping) {
    return NestConfig.create(dataset, jdbc, mapping);
  }

  @PreDestroy
  public void performCleanUp() {
    log.info(Scope.SYSTEM.marker(), "cleaning up destroyed container using {}", closeListeners);
    for (OnClose listener : closeListeners) {
      try {
        listener.cleanUp(config);
      } catch (final RuntimeException e) {
        log.info(Scope.SYSTEM.marker(), "error during container clean up", e);
      }
    }
  }

  // Observable factories as nest static classes to avoid inner classes with implicit references.


  private static class CallDescriptorService implements Func0<Observable<? extends SchemaDescriptor>> {
    private final DescriptorService service;
    private final URI identifier;

    public CallDescriptorService(final DescriptorService serviceRef, final URI identifierRef) {
      this.service = serviceRef;
      this.identifier = identifierRef;
    }

    @Override
    public Observable<? extends SchemaDescriptor> call() {
      return service.metadata(identifier);
    }
  }


  private static class CallDefinitionService implements Func0<Observable<? extends SqlSchema>> {
    private final DefinitionService service;
    private final String schema;

    public CallDefinitionService(final DefinitionService serviceRef, final String schemaRef) {
      this.service = serviceRef;
      this.schema = schemaRef;
    }

    @Override
    public Observable<? extends SqlSchema> call() {
      return service.definition(schema);
    }
  }
}
