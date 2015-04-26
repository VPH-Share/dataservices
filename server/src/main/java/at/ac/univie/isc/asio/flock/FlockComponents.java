package at.ac.univie.isc.asio.flock;

import at.ac.univie.isc.asio.Flock;
import at.ac.univie.isc.asio.engine.DatasetResource;
import at.ac.univie.isc.asio.engine.Engine;
import at.ac.univie.isc.asio.engine.FixedSelection;
import at.ac.univie.isc.asio.engine.sparql.DefaultJenaFactory;
import at.ac.univie.isc.asio.engine.sparql.JenaEngine;
import at.ac.univie.isc.asio.engine.sparql.JenaFactory;
import at.ac.univie.isc.asio.insight.EventResource;
import at.ac.univie.isc.asio.metadata.DescriptorService;
import at.ac.univie.isc.asio.security.WhoamiResource;
import at.ac.univie.isc.asio.tool.Timeout;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Set;

/**
 * Configure the single dataset of a flock server.
 */
@Configuration
@Flock
@EnableConfigurationProperties(FlockSettings.class)
class FlockComponents {

  @Autowired
  private FlockSettings config;

  @Autowired(required = false)
  private DescriptorService descriptorService;

  @Bean
  public FlockDataset flockDataset() {
    if (descriptorService == null) {
      return FlockDataset.withStaticMetadata(config.getIdentifier());
    } else {
      return FlockDataset.withDynamicMetadata(config.getIdentifier(), descriptorService);
    }
  }

  @Bean
  public FixedSelection fixedEngineRouter(final Set<Engine> engines) {
    return FixedSelection.from(engines);
  }

  @Bean
  public JenaEngine jenaEngine(final JenaFactory factory) {
    return JenaEngine.using(factory, true);
  }

  @Bean
  public JenaFactory simpleJenaState(final Timeout timeout) {
    return new DefaultJenaFactory(ModelFactory.createDefaultModel(), timeout);
  }

  @Bean
  @Primary
  public ResourceConfig flockJerseyConfiguration(final ResourceConfig config) {
    config.setApplicationName("jersey-flock");
    config.register(DatasetResource.class);
    config.register(EventResource.class);
    config.register(WhoamiResource.class);
    return config;
  }
}
