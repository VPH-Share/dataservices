package at.ac.univie.isc.asio.engine;

import at.ac.univie.isc.asio.DatasetException;
import at.ac.univie.isc.asio.security.Permission;
import at.ac.univie.isc.asio.tool.TimeoutSpec;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscription;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.ConnectionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.container.TimeoutHandler;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.concurrent.TimeUnit;

@Path("/{language}")
public class ProtocolResource {
  private static final Logger log = LoggerFactory.getLogger(ProtocolResource.class);

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  public ProtocolResource() {
    throw new AssertionError("attempt to use non-managed resource");
  }

  private final Command.Factory connector;
  private final TimeoutSpec timeout;
  private final EventReporter report;

  public ProtocolResource(final Command.Factory connector, final TimeoutSpec timeout,
                          final Supplier<EventReporter> scopedEventReporter) {
    this.connector = connector;
    this.timeout = timeout;
    report = scopedEventReporter.get();
  }

  @Context
  private Request request;
  @Context
  private SecurityContext security;
  @Context
  private HttpHeaders headers;

  @PathParam("language")
  private Language language;

  @GET
  public void acceptQuery(@Context final UriInfo uri, @Suspended final AsyncResponse async) {
    final Parameters handler = ParseJaxrsParameters
        .with(language)
        .argumentsFrom(uri.getQueryParameters())
        .including(headers).collect();
    process(async, handler);
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public void acceptForm(final MultivaluedMap<String, String> formParameters,
                         @Suspended final AsyncResponse async) {
    final Parameters handler = ParseJaxrsParameters
        .with(language)
        .argumentsFrom(formParameters)
        .including(headers).collect();
    process(async, handler);
  }

  @POST
  public void acceptBody(final String body,
                         @HeaderParam(HttpHeaders.CONTENT_TYPE) MediaType contentType,
                         @Suspended final AsyncResponse async) {
    Parameters handler = ParseJaxrsParameters
        .with(language)
        .body(body, contentType)
        .including(headers).collect();
    process(async, handler);
  }

  @Path("/schema")
  @GET
  @Deprecated // use MetadataResource#schema directly.
  public Response serveSchema(@Context final UriInfo uri) {
    final URI redirect = uri.getBaseUriBuilder().path(Permission.READ.name()).path("meta/schema").build();
    return Response.status(Response.Status.MOVED_PERMANENTLY)
        .header(HttpHeaders.LOCATION, redirect).build();
  }

  private void process(final AsyncResponse async, final Parameters params) {
    try {
      report
          .with(params)
          .and("http-headers", headers.getRequestHeaders().toString())
          .event(EventReporter.RECEIVED);
      params.failIfNotValid();
      final Command executable = connector.accept(params, security.getUserPrincipal());
      report
          .with(executable)
          .event(EventReporter.ACCEPTED);
      CheckCommandAuthorization.with(security, request).check(executable);
      final Subscription subscription = executable
          .observe()
          .subscribe(CommandObserver.bridgeTo(async));
      final SubscriptionCleaner cleaner = new SubscriptionCleaner(subscription);
      async.register(cleaner);
      async.setTimeoutHandler(cleaner);
      async.setTimeout(timeout.getAs(TimeUnit.NANOSECONDS, 0L), TimeUnit.NANOSECONDS);
    } catch (final Throwable error) {
      report
          .with(error)
          .event(EventReporter.REJECTED);
      resumeWithError(async, error);
    }
  }

  /**
   * log the occurred error and resume the response if it is still suspended.
   */
  private void resumeWithError(final AsyncResponse response, final Throwable error) {
    final Throwable wrapped = DatasetException.wrapIfNecessary(error);
    final boolean errorSent = response.resume(wrapped);
    if (!errorSent) { log.warn("request failed - could not send error response"); }
    if (DatasetException.isRegular(error)) {
      //noinspection ThrowableResultOfMethodCallIgnored
      final Throwable root = Throwables.getRootCause(error);
      log.warn("request failed - {}", root.getMessage());
    } else {
      log.error("request failed - {}", error.getMessage(), error);
    }
  }

  private static class SubscriptionCleaner implements TimeoutHandler, CompletionCallback, ConnectionCallback {
    private final Subscription subscription;

    public SubscriptionCleaner(final Subscription subscription) {
      this.subscription = subscription;
    }

    @Override
    public void handleTimeout(final AsyncResponse asyncResponse) {
      asyncResponse.resume(new ServiceUnavailableException("execution time limit exceeded"));
      subscription.unsubscribe();
    }

    @Override
    public void onComplete(final Throwable throwable) {
      subscription.unsubscribe();
    }

    @Override
    public void onDisconnect(final AsyncResponse disconnected) {
      subscription.unsubscribe();
    }
  }
}
