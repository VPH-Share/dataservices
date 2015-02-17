package at.ac.univie.isc.asio;

import at.ac.univie.isc.asio.web.EventSource;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import org.apache.http.HttpResponse;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static at.ac.univie.isc.asio.web.EventMatchers.whereData;
import static at.ac.univie.isc.asio.web.EventMatchers.whereType;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;

/**
 * Emitting events from protocol operations.
 */
@SuppressWarnings("unchecked")
@Category(Integration.class)
@RunWith(Parameterized.class)
public class FeatureEvents extends IntegrationTest {

  @Parameterized.Parameters(name = "{index} : {0}-{1}")
  public static Iterable<Object[]> variants() {
    // { language, operation, noop_command, required_permission }
    return Arrays.asList(new Object[][] {
        {"sql", "query", "SELECT 1"},
        {"sql", "update", "DROP TABLE IF EXISTS test_gaga_12345"},
        {"sparql", "query", "ASK {}"},
    });
  }

  @Parameterized.Parameter(0)
  public String language;
  @Parameterized.Parameter(1)
  public String operation;
  @Parameterized.Parameter(2)
  public String noop;

  private final EventSource monitor = EventSource.listenTo(serviceAddress("admin").resolve("meta/events"));

  @Before
  public void ensureLanguageSupported() {
    ensureLanguageSupported(language);
  }

  @After
  public void disconnectEventSource() {
    monitor.close();
  }

  @Test
  public void emit_subscribed_on_connecting() throws Exception {
    final EventSource.MessageEvent received = monitor.events().take(1).toBlocking().single();
    assertThat(received, whereType(is("stream")));
    assertThat(received, whereData(is("subscribed")));
  }

  @Test
  public void successful_query_event_sequence() throws Exception {
    monitor.connection().subscribeOn(Schedulers.io()).subscribe(new Action1<HttpResponse>() {
      @Override
      public void call(final HttpResponse aVoid) {
        withPermission("admin").param(operation, noop).post("/{language}", language);
      }
    });
    final List<EventSource.MessageEvent> received =
        monitor.events().skip(1).take(3).toList().toBlocking().single();
    assertThat(received,
        both(hasItems(subject("received"), subject("executed"), subject("completed")))
            .and(is(correlated()))
    );
  }

  @Test
  public void failed_query_event_sequence() throws Exception {
    monitor.connection().subscribeOn(Schedulers.io()).subscribe(new Action1<HttpResponse>() {
      @Override
      public void call(final HttpResponse aVoid) {
        final String commandWithInvalidSyntax = Hashing.md5().hashString(noop, Charsets.UTF_8).toString();
        withPermission("admin").param(operation, commandWithInvalidSyntax).post("/{language}", language);;
      }
    });
    final List<EventSource.MessageEvent> received =
        monitor.events().skip(1).take(2).toList().toBlocking().single();
    assertThat(received,
        both(hasItems(subject("received"), subject("failed"))).and(is(correlated()))
    );
  }

  @Test
  public void rejected_query_event_sequence() throws Exception {
    monitor.connection().subscribeOn(Schedulers.io()).subscribe(new Action1<HttpResponse>() {
      @Override
      public void call(final HttpResponse aVoid) {
        withPermission("admin").param(operation).post("/{language}", language);
      }
    });
    final List<EventSource.MessageEvent> received =
        monitor.events().skip(1).take(2).toList().toBlocking().single();
    assertThat(received,
        both(hasItems(subject("received"), subject("failed"))).and(is(correlated()))
    );
  }

  private Matcher<EventSource.MessageEvent> subject(final String subject) {
    return whereData(containsString("\"subject\":\"" + subject + "\""));
  }

  private Matcher<Iterable<? extends EventSource.MessageEvent>> correlated() {
    return new CorrelationMatcher();
  }

  private static class CorrelationMatcher extends TypeSafeMatcher<Iterable<? extends EventSource.MessageEvent>> {
    @Override
    protected boolean matchesSafely(final Iterable<? extends EventSource.MessageEvent> item) {
      final Iterable<String> ids =
          Iterables.transform(item, new Function<EventSource.MessageEvent, String>() {
            @Nullable
            @Override
            public String apply(@Nullable final EventSource.MessageEvent input) {
              return extractCorrelation(input.data());
            }
          });
      return everyItem(equalTo(Iterables.getFirst(ids, null))).matches(ids);
    }

    private String extractCorrelation(final String message) {
      final java.util.regex.Matcher regex =
          Pattern.compile(".*\"correlation\":\"(.+?)\".*").matcher(message);
      if (regex.matches() && regex.groupCount() == 1) {
        return regex.group(1);
      }
      throw new AssertionError("either no or too many correlations in " + message);
    }

    @Override
    public void describeTo(final Description description) {
      description.appendText("correlated event sequence");
    }
  }
}
