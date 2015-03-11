package at.ac.univie.isc.asio.integration;

import at.ac.univie.isc.asio.Integration;
import at.ac.univie.isc.asio.junit.Interactions;
import at.ac.univie.isc.asio.junit.Rules;
import at.ac.univie.isc.asio.restassured.ReportingFilter;
import at.ac.univie.isc.asio.sql.Database;
import at.ac.univie.isc.asio.web.EventSource;
import com.google.common.base.Charsets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.config.EncoderConfig;
import com.jayway.restassured.config.MatcherConfig;
import com.jayway.restassured.specification.RequestSpecification;
import de.bechte.junit.runners.context.HierarchicalContextRunner;
import org.apache.http.HttpStatus;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assume.assumeThat;

/**
 * Base for integration tests. Prepares RestAssured config and global rules. Tests can be adapted to
 * deployment specific configuration by manipulating the static fields.
 */
@Category(Integration.class)
@RunWith(HierarchicalContextRunner.class)
public abstract class IntegrationTest implements IntegrationDsl.SpecFactoryCallback {

  @BeforeClass
  public static void restAssuredDefaults() {
    RestAssured.config = RestAssured.config()
        .encoderConfig(EncoderConfig.encoderConfig()
                .defaultContentCharset(Charsets.UTF_8.name()).and()
                .defaultQueryParameterCharset(Charsets.UTF_8.name()).and()
                .appendDefaultContentCharsetToContentTypeIfUndefined(false)
        )
        .matcherConfig(
            new MatcherConfig().errorDescriptionType(MatcherConfig.ErrorDescriptionType.HAMCREST)
        )
    ;
  }

  @BeforeClass
  public static void validateConfiguration() {
    requireNonNull(config, "Global configuration missing. Use IntegrationTest.configure(..) to provide settings.");
    config.validate();
  }

  // === global scope ==============================================================================

  private static final IntegrationSettings config = new IntegrationSettings();

  /**
   * Configure the integration tests.
   */
  public static IntegrationSettings configure() {
    return config;
  }

  // === test case scope ===========================================================================

  @Rule
  public Timeout timeout = Rules.timeout(config.timeoutInSeconds, TimeUnit.SECONDS);
  @Rule
  public Interactions interactions = Rules.interactions();

  // create dsl with global defaults
  private final IntegrationDsl dsl = new IntegrationDsl(this).copy(config.dslDefaults);

  // === test components ===========================================================================

  /**
   * @return the backing test database, if available
   * @throws AssertionError if no database is available
   */
  protected final Database database() {
    if (config.database == null) { throw new AssertionError("missing database configuration"); }
    return config.database;
  }

  /**
   * Create an {@link at.ac.univie.isc.asio.web.EventSource}, listening to the tested asio instance.
   *
   * @return configured event source
   */
  protected final EventSource eventSource() {
    final URI endpoint = config.auth.configureUri(config.serviceBase, "admin").resolve(config.eventService);
    final DefaultHttpClient client = config.auth.configureClient(EventSource.defaultClient(), "admin");
    return EventSource.listenTo(endpoint, client);
  }

  // === assumptions ===============================================================================

  /**
   * Skip test if no endpoint for the given language exists.
   *
   * @param language name of required language
   */
  protected final void ensureLanguageSupported(final String language) {
    final int responseCode =
        given().role("admin").spec().get("/{language}", language).then().extract().statusCode();
    assumeThat(language + " not supported", responseCode, is(not(HttpStatus.SC_NOT_FOUND)));
  }

  /**
   * Skip test if the service is not enforcing authorization, e.g. uses a fixed permission.
   */
  protected final void ensureSecured() {
    assumeThat("service is not secured", config.auth.isAuthorizing(), is(true));
  }

  /**
   * Skip test if the backing database is not configured, e.g. there is none.
   */
  protected final void ensureDatabaseAccessible() {
    assumeThat("database not accessible", config.database, is(not(nullValue())));
  }

  // === fluent test dsl ===========================================================================

  /**
   * Delegate to fluent dsl.
   */
  protected final IntegrationDsl given() {
    return dsl;
  }

  @Override
  public final RequestSpecification requestFrom(final IntegrationDsl dsl) {
    final URI baseUri = dsl.getSchema() == null
        ? config.serviceBase
        : config.serviceBase.resolve(dsl.getSchema());
    final URI authedBaseUri = config.auth.configureUri(baseUri, dsl.getRole());

    RequestSpecBuilder request = new RequestSpecBuilder().setBaseUri(authedBaseUri.toString());
    request = config.auth.configureRequestSpec(request, dsl.getRole());

    final UsernamePasswordCredentials delegated = dsl.getDelegated();
    request = delegated == null
        ? request
        : config.auth.attachCredentials(delegated.getUserName(), delegated.getPassword(), request);

    final ReportingFilter reportingInterceptor = interactions.attached(ReportingFilter.create());
    return RestAssured.given().spec(request.build()).filter(reportingInterceptor);
  }
}