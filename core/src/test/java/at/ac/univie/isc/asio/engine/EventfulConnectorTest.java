package at.ac.univie.isc.asio.engine;

import at.ac.univie.isc.asio.TestTicker;
import at.ac.univie.isc.asio.tool.CaptureEvents;
import at.ac.univie.isc.asio.tool.Reactive;
import com.google.common.io.ByteStreams;
import org.junit.Test;
import org.mockito.Mockito;
import rx.Observable;
import rx.functions.Actions;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.OutputStream;

import static at.ac.univie.isc.asio.tool.EventMatchers.event;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

public class EventfulConnectorTest {
  public static final Parameters NULL_PARAMS = ParametersBuilder.with(Language.SQL).build();
  public static final StreamedResults DUMMY_RESULTS = new StreamedResults(MediaType.WILDCARD_TYPE) {
    @Override
    protected void doWrite(final OutputStream output) throws IOException {

    }
  };

  private final CaptureEvents events = CaptureEvents.create();
  private final EventReporter report = new EventReporter(events.bus(), TestTicker.create(1));
  private final Connector delegate = Mockito.mock(Connector.class);

  private final EventfulConnector subject = EventfulConnector.around(report, delegate);

  @Test
  public void wrapped_results_should_not_be_altered() throws Exception {
    when(delegate.accept(NULL_PARAMS)).thenReturn(Observable.just(DUMMY_RESULTS));
    final StreamedResults results = subject.accept(NULL_PARAMS).toBlocking().single();
    assertThat(results, sameInstance(DUMMY_RESULTS));
  }

  @Test
  public void should_emit_initial_received_event() throws Exception {
    when(delegate.accept(NULL_PARAMS)).thenReturn(Observable.<StreamedResults>empty());
    subject.accept(NULL_PARAMS);
    assertThat(events.captured(), hasItem(event("request", "received")));
  }

  @Test
  public void should_emit_executed_on_after_yielding_streamed_results() throws Exception {
    when(delegate.accept(NULL_PARAMS)).thenReturn(Observable.just(DUMMY_RESULTS));
    subject.accept(NULL_PARAMS).subscribe();
    assertThat(events.captured(), hasItem(event("request", "executed")));
  }

  @Test
  public void should_emit_failed_on_error() throws Exception {
    when(delegate.accept(NULL_PARAMS)).thenReturn(Observable.<StreamedResults>error(new IllegalStateException("test")));
    subject.accept(NULL_PARAMS).subscribe(Actions.empty(), Reactive.ignoreErrors());
    assertThat(events.captured(), hasItem(event("request", "failed")));
  }

  @Test
  public void should_emit_completed_after_streaming_ended() throws Exception {
    when(delegate.accept(NULL_PARAMS)).thenReturn(Observable.just(DUMMY_RESULTS));
    subject.accept(NULL_PARAMS).toBlocking().single().write(ByteStreams.nullOutputStream());
    assertThat(events.captured(), hasItem(event("request", "completed")));
  }

  @Test
  public void should_emit_failed_when_stream_throws() throws Exception {
    final StreamedResults failing = new StreamedResults(MediaType.WILDCARD_TYPE) {
      @Override
      protected void doWrite(final OutputStream output) throws IOException {
        throw new IllegalStateException();
      }
    };
    when(delegate.accept(NULL_PARAMS)).thenReturn(Observable.just(failing));
    try {
      subject.accept(NULL_PARAMS).toBlocking().single().write(ByteStreams.nullOutputStream());
    } catch (Exception ignored) {}
    assertThat(events.captured(), hasItem(event("request", "failed")));
  }
}