package at.ac.univie.isc.asio.acceptance;

import static javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static javax.ws.rs.core.Response.Status.Family.familyOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.ext.form.Form;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import at.ac.univie.isc.asio.tool.FunctionalTest;


@Category(FunctionalTest.class)
public class SparqlQueryTest extends AcceptanceHarness {

  private static final String TEST_QUERY = "SELECT DISTINCT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 10";

  @Override
  protected URI getTargetUrl() {
    return AcceptanceHarness.READ_ACCESS.resolve("sparql");
  }

  @Test
  public void valid_query_as_uri_param() throws Exception {
    client.accept(CSV).query(PARAM_QUERY, TEST_QUERY);
    response = client.get();
    verify(response);
  }

  @Test
  public void valid_query_as_form_param() throws Exception {
    client.accept(CSV);
    final Form values = new Form();
    values.set(PARAM_QUERY, TEST_QUERY);
    response = client.form(values);
    verify(response);
  }

  @Test
  public void valid_query_as_payload() throws Exception {
    client.accept(CSV).type("application/sql-query");
    response = client.post(TEST_QUERY);
    verify(response);
  }

  @Test
  public void delivers_csv() throws Exception {
    client.accept(CSV).query(PARAM_QUERY, TEST_QUERY);
    response = client.get();
    assertEquals(SUCCESSFUL, familyOf(response.getStatus()));
    assertTrue(CSV.isCompatible(response.getMediaType()));
  }

  @Test
  public void bad_query_parameter() throws Exception {
    client.accept(APPLICATION_XML_TYPE).query(PARAM_QUERY, "");
    response = client.get();
    assertEquals(CLIENT_ERROR, familyOf(response.getStatus()));
  }

  @Test
  public void inacceptable_media_type() throws Exception {
    client.accept(MediaType.valueOf("test/notexisting")).query(PARAM_QUERY, TEST_QUERY);
    response = client.get();
    assertEquals(CLIENT_ERROR, familyOf(response.getStatus()));
  }

  private void verify(final Response response) {
    assertEquals(SUCCESSFUL, familyOf(response.getStatus()));
    assertTrue(CSV.isCompatible(response.getMediaType()));
  }
}