package at.ac.univie.isc.asio.engine;

import at.ac.univie.isc.asio.DatasetFailureException;
import at.ac.univie.isc.asio.DatasetUsageException;
import at.ac.univie.isc.asio.Language;
import at.ac.univie.isc.asio.admin.Event;
import at.ac.univie.isc.asio.config.JaxrsSpec;
import at.ac.univie.isc.asio.config.TimeoutSpec;
import at.ac.univie.isc.asio.jaxrs.AcceptTunnelFilter;
import at.ac.univie.isc.asio.jaxrs.DisableAuthorizationFilter;
import at.ac.univie.isc.asio.jaxrs.EmbeddedServer;
import at.ac.univie.isc.asio.security.Role;
import at.ac.univie.isc.asio.security.Token;
import at.ac.univie.isc.asio.tool.CaptureEvents;
import at.ac.univie.isc.asio.tool.Rules;
import at.ac.univie.isc.asio.tool.TestTicker;
import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableMultimap;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.ArgumentCaptor;
import rx.Observable;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.OutputStream;
import java.security.Principal;
import java.util.concurrent.TimeUnit;

import static at.ac.univie.isc.asio.jaxrs.ResponseMatchers.hasBody;
import static at.ac.univie.isc.asio.jaxrs.ResponseMatchers.hasStatus;
import static at.ac.univie.isc.asio.tool.EventMatchers.correlated;
import static at.ac.univie.isc.asio.tool.EventMatchers.event;
import static at.ac.univie.isc.asio.tool.EventMatchers.orderedStreamOf;
import static at.ac.univie.isc.asio.tool.IsMultimapContaining.hasEntries;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.CombinableMatcher.both;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProtocolResourceTest {
  private final TimeoutSpec timeout = mock(TimeoutSpec.class);
  private final Command.Factory connector = mock(Command.Factory.class);
  private final Command command = mock(Command.class);

  private final CaptureEvents events = CaptureEvents.create();
  private final Ticker time = TestTicker.create(42);
  private final Supplier<EventReporter> eventBuilder =
      Suppliers.ofInstance(new EventReporter(events.bus(), time));

  private final byte[] PAYLOAD = "{response : success}".getBytes(Charsets.UTF_8);

  @Rule
  public Timeout testTimeout = Rules.timeout(2, TimeUnit.SECONDS);
  @Rule
  public EmbeddedServer server = EmbeddedServer
      .host(JaxrsSpec.create(ProtocolResource.class, DisableAuthorizationFilter.class))
      .resource(new ProtocolResource(connector, timeout, eventBuilder))
      .enableLogging()
      .create();

  private Response response;

  @Before
  public void setup() {
    when(timeout.getAs(any(TimeUnit.class), any(Long.class))).thenReturn(0L);
    // prepare mocks
    when(connector.accept(any(Parameters.class), any(Principal.class))).thenReturn(command);
    when(command.requiredRole()).thenReturn(Role.ANY);
    when(command.properties()).thenReturn(ImmutableMultimap.<String, String>of());
    final Command.Results payloadStreamer = new Command.Results() {
      @Override
      public void write(final OutputStream output) throws IOException, WebApplicationException {
        output.write(PAYLOAD);
      }

      @Override
      public MediaType format() {
        return MediaType.APPLICATION_JSON_TYPE;
      }

      @Override
      public void close() {
      }
    };
    when(command.observe()).thenReturn(Observable.from(payloadStreamer));
  }

  private WebTarget invoke(final Language language) {
    return server.endpoint().path(language.name());
  }

  // ====================================================================================>
  // HAPPY PATH

  @Test
  public void valid_get_operation() throws Exception {
    response = invoke(Language.SQL)
        .queryParam("action", "command")
        .request(MediaType.APPLICATION_JSON)
        .get();
    verifySuccessful(response);
  }

  @Test
  public void valid_form_operation() throws Exception {
    final Form form = new Form("action", "command");
    response = invoke(Language.SQL)
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
    verifySuccessful(response);
  }

  @Test
  public void valid_body_operation() throws Exception {
    response = invoke(Language.SQL)
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.entity("command", MediaType.valueOf("application/sql-action")));
    verifySuccessful(response);
  }

  @Test
  public void valid_schema_operation() throws Exception {
    final WebTarget endpoint = server.endpoint();
    response = endpoint
        .path("sql").path("schema")
        .request(MediaType.APPLICATION_JSON)
        .get();
    assertThat(response, hasStatus(Response.Status.MOVED_PERMANENTLY));
    assertThat(response.getHeaderString(HttpHeaders.LOCATION), CoreMatchers.endsWith("meta/schema"));
  }

  private void verifySuccessful(final Response response) {
    assertThat(response, hasStatus(Response.Status.OK));
    assertThat(response.getMediaType(), is(MediaType.APPLICATION_JSON_TYPE));
    assertThat(response, hasBody(PAYLOAD));
  }

  // ====================================================================================>
  // EVENTING

  @Test
  public void successful_request_events() throws Exception {
    invoke(Language.SQL).request().get();
    assertThat(events.captured(Event.class),
        is(both(orderedStreamOf(event("received"), event("accepted")))
            .and(correlated()))
    );
  }

  @Test
  public void fail_due_to_illegal_parameters_events() throws Exception {
    invoke(Language.SQL).request().post(Entity.text("illegal"));
    assertThat(events.captured(Event.class),
        is(both(orderedStreamOf(event("received"), event("rejected"))).and(correlated()))
    );
  }

  @Test
  public void connector_failing_events() throws Exception {
    when(connector.accept(any(Parameters.class), any(Principal.class))).thenThrow(new IllegalStateException("test"));
    invoke(Language.SQL).request().get();
    assertThat(events.captured(Event.class),
        is(both(orderedStreamOf(event("received"), event("rejected"))).and(correlated()))
    );
  }

  @Test
  public void fail_to_observer_events() throws Exception {
    when(command.observe()).thenThrow(new IllegalStateException("test"));
    invoke(Language.SQL).request().get();
    assertThat(events.captured(Event.class),
        is(both(orderedStreamOf(event("received"), event("accepted"), event("rejected"))).and(correlated()))
    );
  }

  // ====================================================================================>
  // ILLEGAL REQUESTS

  @Test
  public void body_operation_malformed_media_type() throws Exception {
    response = invoke(Language.SQL)
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.entity("command", MediaType.TEXT_PLAIN_TYPE));
    assertThat(response, hasStatus(Response.Status.UNSUPPORTED_MEDIA_TYPE));
  }

  @Test
  public void body_operation_language_mismatch() throws Exception {
    response = invoke(Language.SQL)
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.entity("command", MediaType.valueOf("application/sparql-action")));
    assertThat(response, hasStatus(Response.Status.UNSUPPORTED_MEDIA_TYPE));
  }

  // ====================================================================================>
  // INTERNAL INVOCATION

  private final ArgumentCaptor<Parameters> params = ArgumentCaptor.forClass(Parameters.class);

  @Test
  public void forward_all_parameters_from_get_operation() throws Exception {
    invoke(Language.SQL)
        .queryParam("one", "1")
        .queryParam("two", "2")
        .queryParam("two", "2")
        .request(MediaType.APPLICATION_JSON).get();
    verify(connector).accept(params.capture(), any(Principal.class));
    assertThat(params.getValue().properties(), hasEntries("one", "1"));
    assertThat(params.getValue().properties(), hasEntries("two", "2", "2"));
  }

  @Test
  public void forward_all_parameters_from_form_operation() throws Exception {
    final Form form = new Form("two", "2").param("two", "2").param("one", "1");
    invoke(Language.SQL).request(MediaType.APPLICATION_JSON)
        .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
    verify(connector).accept(params.capture(), any(Principal.class));
    assertThat(params.getValue().properties(), hasEntries("one", "1"));
    assertThat(params.getValue().properties(), hasEntries("two", "2", "2"));
  }

  @Test
  public void forward_parameter_from_body_operation() throws Exception {
    invoke(Language.SQL).request(MediaType.APPLICATION_JSON)
        .post(Entity.entity("1", MediaType.valueOf("application/sql-one")));
    verify(connector).accept(params.capture(), any(Principal.class));
    assertThat(params.getValue().properties(), hasEntries("one", "1"));
  }

  @Test
  public void forward_language() throws Exception {
    invoke(Language.SQL).request(MediaType.APPLICATION_JSON).get();
    verify(connector).accept(params.capture(), any(Principal.class));
    assertThat(params.getValue().language(), is(Language.SQL));
  }

  @Test
  public void forward_acceptable_types() throws Exception {
    invoke(Language.SQL)
        .request(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN).get();
    verify(connector).accept(params.capture(), any(Principal.class));
    assertThat(params.getValue().acceptable(), containsInAnyOrder(
        MediaType.TEXT_PLAIN_TYPE,
        MediaType.APPLICATION_XML_TYPE,
        MediaType.APPLICATION_JSON_TYPE));
  }

  @Test
  public void default_to_xml_if_accept_header_missing() throws Exception {
    invoke(Language.SQL).request().header(HttpHeaders.ACCEPT, null).get();
    verify(connector).accept(params.capture(), any(Principal.class));
    assertThat(params.getValue().acceptable(), containsInAnyOrder(MediaType.APPLICATION_XML_TYPE));
  }

  @Test
  public void replace_accept_header_with_tunneled_type() throws Exception {
    // .queryParam("_type", "xml") works also -> cxf built-in RequestPreprocessor
    invoke(Language.SQL)
        .queryParam(AcceptTunnelFilter.ACCEPT_PARAM_TUNNEL, MediaType.TEXT_HTML)
        .request(MediaType.APPLICATION_JSON).get();
    verify(connector).accept(params.capture(), any(Principal.class));
    assertThat(params.getValue().acceptable(), containsInAnyOrder(MediaType.TEXT_HTML_TYPE));
  }

  private final ArgumentCaptor<Principal> principal = ArgumentCaptor.forClass(Principal.class);

  @Test
  public void forward_authorization_token() throws Exception {
    // DisableAuthFilter authenticates user as Token.ANONYMOUS
    invoke(Language.SQL).request(MediaType.APPLICATION_JSON).get();
    verify(connector).accept(any(Parameters.class), eq(Token.ANONYMOUS));
  }

  // ====================================================================================>
  // INTERNAL ERRORS

  @Test
  public void language_is_not_supported() throws Exception {
    when(connector.accept(any(Parameters.class), any(Principal.class)))
        .thenThrow(new Command.Factory.LanguageNotSupported(Language.SQL));
    response = invoke(Language.SQL).request().get();
    assertThat(response, hasStatus(Response.Status.NOT_FOUND));
  }

  @Test
  public void get_operation_requires_write_role() throws Exception {
    when(command.requiredRole()).thenReturn(Role.WRITE);
    response = invoke(Language.SQL).request().get();
    assertThat(response, hasStatus(Response.Status.FORBIDDEN));
  }

  @Test
  public void command_execution_fails_fatally() throws Exception {
    when(command.observe()).thenThrow(IllegalStateException.class);
    response = invoke(Language.SQL).request().get();
    assertThat(response, hasStatus(Response.Status.INTERNAL_SERVER_ERROR));
  }

  @Test
  public void observable_fails_due_to_client_error() throws Exception {
    when(command.observe())
        .thenReturn(Observable.<Command.Results>error(new DatasetUsageException("test-exception")));
    response = invoke(Language.SQL).request().get();
    assertThat(response, hasStatus(Response.Status.BAD_REQUEST));
  }

  @Test
  public void observable_fails_due_to_internal_error() throws Exception {
    when(command.observe())
        .thenReturn(Observable.<Command.Results>error(new DatasetFailureException(new IllegalStateException())));
    response = invoke(Language.SQL).request().get();
    assertThat(response, hasStatus(Response.Status.INTERNAL_SERVER_ERROR));
  }

  @Test
  public void execution_times_out() throws Exception {
    when(timeout.getAs(any(TimeUnit.class), any(Long.class)))
        .thenReturn(TimeUnit.NANOSECONDS.convert(100, TimeUnit.MILLISECONDS));
    when(command.observe()).thenReturn(Observable.<Command.Results>never());
    response = invoke(Language.SQL).request().get();
    assertThat(response, hasStatus(Response.Status.SERVICE_UNAVAILABLE));
  }

  @Test
  public void serialization_fails_fatally() throws Exception {
    final Command.Results failing = new Command.Results() {
      @Override
      public void write(final OutputStream output) throws IOException, WebApplicationException {
        throw new DatasetFailureException(new IllegalStateException());
      }

      @Override
      public MediaType format() {
        return null;
      }

      @Override
      public void close() {
      }
    };
    when(command.observe()).thenReturn(Observable.just(failing));
    response = invoke(Language.SQL).request().get();
    assertThat(response, hasStatus(Response.Status.INTERNAL_SERVER_ERROR));
  }

  @Test
  public void serialization_fails_with_web_error() throws Exception {
    final Command.Results failing = new Command.Results() {
      @Override
      public void write(final OutputStream output) throws IOException, WebApplicationException {
        throw new WebApplicationException(Response.Status.BAD_REQUEST);
      }

      @Override
      public MediaType format() {
        return null;
      }

      @Override
      public void close() {
      }
    };
    when(command.observe()).thenReturn(Observable.just(failing));
    response = invoke(Language.SQL).request().get();
    assertThat(response, hasStatus(Response.Status.BAD_REQUEST));
  }
}
