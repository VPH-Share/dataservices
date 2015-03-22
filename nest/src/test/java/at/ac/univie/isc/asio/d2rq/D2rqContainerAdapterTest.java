package at.ac.univie.isc.asio.d2rq;

import at.ac.univie.isc.asio.Schema;
import at.ac.univie.isc.asio.container.ConfigStore;
import at.ac.univie.isc.asio.container.ContainerSettings;
import at.ac.univie.isc.asio.io.Classpath;
import com.google.common.io.ByteSource;
import com.hp.hpl.jena.rdf.model.Model;
import org.junit.Test;
import org.springframework.dao.DataAccessException;

import java.net.URI;

import static at.ac.univie.isc.asio.junit.IsIsomorphic.isomorphicWith;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class D2rqContainerAdapterTest {
  private final ByteSource mapping = Classpath.load("d2rq-container/input-mapping.ttl");
  private final StubConfigStore store = new StubConfigStore();
  private Model input = readModel(mapping);

  private final D2rqContainerAdapter subject = D2rqContainerAdapter.from(input);

  private Model readModel(final ByteSource raw) {
    return LoadD2rqModel.inferBaseUri().parse(raw);
  }

  @Test
  public void should_populate_settings() throws Exception {
    final ContainerSettings settings = subject.translate(Schema.valueOf("test-schema"), store);
    assertThat(settings.getName(), equalTo(Schema.valueOf("test-schema")));
    assertThat(settings.getIdentifier(), equalTo("http://localhost:8080/asio/"));
    assertThat(settings.getTimeout(), equalTo(2000L));
    assertThat(settings.getSparql().getD2rBaseUri(), equalTo(URI.create("http://localhost:8080/asio/")));
    assertThat(settings.getSparql().getD2rMappingLocation(), equalTo(URI.create("asio://test")));
    assertThat(settings.getSparql().isFederation(), equalTo(false));
    assertThat(settings.getDatasource().getJdbcUrl(), equalTo("jdbc:mysql://localhost/test"));
    assertThat(settings.getDatasource().getUsername(), equalTo("username"));
    assertThat(settings.getDatasource().getPassword(), equalTo("password"));
  }

  @Test
  public void should_serialize_without_server_and_with_empty_database() throws Exception {
    subject.translate(Schema.valueOf("test-schema"), store);
    final Model cleaned = readModel(store.saved);
    final Model expected = readModel(Classpath.load("d2rq-container/cleaned-mapping.ttl"));
    assertThat(cleaned, isomorphicWith(expected));
  }

  private static class StubConfigStore implements ConfigStore {
    ByteSource saved;

    @Override
    public URI save(final String qualifier, final String name, final ByteSource content) throws DataAccessException {
      saved = content;
      return URI.create("asio://test");
    }

    @Override
    public void clear(final String qualifier) throws DataAccessException {}
  }
}