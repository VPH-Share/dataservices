package at.ac.univie.isc.asio.container.nest;

import at.ac.univie.isc.asio.Schema;
import at.ac.univie.isc.asio.Scope;
import at.ac.univie.isc.asio.container.Assembler;
import at.ac.univie.isc.asio.container.Container;
import at.ac.univie.isc.asio.d2rq.D2rqConfigModel;
import at.ac.univie.isc.asio.d2rq.D2rqJdbcModel;
import at.ac.univie.isc.asio.d2rq.LoadD2rqModel;
import at.ac.univie.isc.asio.spring.SpringContextFactory;
import com.google.common.io.ByteSource;
import com.hp.hpl.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Create container based on the {@link at.ac.univie.isc.asio.container.nest.NestBluePrint}.
 */
@Component
final class D2rqNestAssembler implements Assembler {
  private static final Logger log = getLogger(D2rqNestAssembler.class);

  private final SpringContextFactory create;
  private final List<Configurer> configurers;

  @Autowired
  public D2rqNestAssembler(final SpringContextFactory create, final List<Configurer> configurers) {
    this.create = create;
    this.configurers = configurers;
  }

  @Override
  public Container assemble(final Schema name, final ByteSource source) {
    log.debug(Scope.SYSTEM.marker(), "assemble <{}> from {}", name, source);
    final Model model = LoadD2rqModel.inferBaseUri().parse(source);
    final NestConfig initial = parse(model);
    initial.getDataset().setName(name);
    log.debug(Scope.SYSTEM.marker(), "initial config for {} : {}", name, initial);
    final NestConfig processed = postProcess(initial, configurers);
    log.debug(Scope.SYSTEM.marker(), "final config for {} : {}", name, initial);
    final AnnotationConfigApplicationContext context = create.named(name.name());
    inject(context, processed);
    log.debug(Scope.SYSTEM.marker(), "assembled container <{}> with {} ({})", name, context.getId(), context);
    return NestContainer.wrap(context, processed);
  }

  // === assembly steps - package visible for testing ==============================================

  final NestConfig parse(final Model model) {
    final D2rqConfigModel d2rq = D2rqConfigModel.wrap(model);
    final Dataset dataset = new Dataset()
        .setIdentifier(d2rq.getIdentifier())
        .setTimeout(d2rq.getTimeout())
        .setFederationEnabled(d2rq.isFederationEnabled());
    final D2rqJdbcModel jdbcConfig = D2rqJdbcModel.parse(d2rq.getMapping());
    final Jdbc jdbc = new Jdbc()
        .setUrl(jdbcConfig.getUrl())
        .setSchema(jdbcConfig.getSchema())
        .setDriver(jdbcConfig.getDriver())
        .setUsername(jdbcConfig.getUsername())
        .setPassword(jdbcConfig.getPassword())
        .setProperties(jdbcConfig.getProperties());
    return NestConfig.create(dataset, jdbc, d2rq.getMapping());
  }

  final NestConfig postProcess(final NestConfig initial, final List<Configurer> configurers) {
    assert initial.getDataset().getName() != null : "implementation error - dataset name not set";
    NestConfig processed = initial;
    for (Configurer configurer : configurers) {
      log.debug(Scope.SYSTEM.marker(), "applying {} to {}", configurer, processed);
      processed = configurer.apply(processed);
    }
    return processed;
  }

  final void inject(final AnnotationConfigApplicationContext context,
                    final NestConfig config) {
    context.register(NestBluePrint.class);
    context.getBeanFactory().registerSingleton("dataset", config.getDataset());
    context.getBeanFactory().registerSingleton("jdbc", config.getJdbc());
    context.getBeanFactory().registerSingleton("mapping", config.getMapping());
  }
}
