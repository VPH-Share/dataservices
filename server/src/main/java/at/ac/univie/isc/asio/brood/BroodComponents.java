package at.ac.univie.isc.asio.brood;

import at.ac.univie.isc.asio.AsioFeatures;
import at.ac.univie.isc.asio.AsioSettings;
import at.ac.univie.isc.asio.Brood;
import at.ac.univie.isc.asio.database.MysqlUserRepository;
import at.ac.univie.isc.asio.engine.DatasetHolder;
import at.ac.univie.isc.asio.platform.FileSystemConfigStore;
import at.ac.univie.isc.asio.tool.JdbcTools;
import at.ac.univie.isc.asio.tool.Timeout;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Components required for the container management in brood.
 */
@Configuration
@Brood
class BroodComponents {

  @Autowired
  private AsioSettings config;

  @Bean
  public FileSystemConfigStore fileSystemConfigStore(final Timeout timeout) {
    return new FileSystemConfigStore(Paths.get(config.getHome()), timeout);
  }

  @Bean
  @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.INTERFACES)
  public DatasetHolder activeDataset() {
    return new DatasetHolder();
  }

  @Bean
  @Primary
  public ResourceConfig broodJerseyConfiguration(final ResourceConfig jersey) {
    jersey.setApplicationName("jersey-brood");
    jersey.register(ApiResource.class);
    if (config.feature.isVphUriAuth()) {
      jersey.register(UriBasedRoutingResource.class);
    } else {
      jersey.register(DefaultRoutingResource.class);
    }
    return jersey;
  }

  /** include custom properties in base-line config */
  @Bean
  @ConfigurationProperties("asio.hikari")
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public HikariConfig baseHikariConfig(final Timeout timeout) {
    final HikariConfig config = new HikariConfig();
    config.setConnectionTimeout(timeout.getAs(TimeUnit.MILLISECONDS, 0));
    return config;
  }

  @Bean
  @ConditionalOnProperty(AsioFeatures.MULTI_TENANCY)
  public MysqlUserRepository userRepository(final DataSource datasource) {
    return new MysqlUserRepository(datasource, config.jdbc.getPrivileges());
  }

  @Bean
  @ConditionalOnProperty(AsioFeatures.GLOBAL_DATASOURCE)
  public DataSource globalDatasource(final HikariConfig base) {
    final HikariConfig config = JdbcTools.populate(base, "global", this.config.jdbc);
    config.setCatalog(null);
    // expect infrequent usage
    config.setMaximumPoolSize(5);
    config.setMinimumIdle(2);
    return new HikariDataSource(config);
  }
}
