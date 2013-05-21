package at.ac.univie.isc.asio.acceptance;

import static javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static javax.ws.rs.core.Response.Status.Family.familyOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.form.Form;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.ac.univie.isc.asio.FunctionalTest;
import at.ac.univie.isc.asio.JaxrsClientProvider;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;

@Category(FunctionalTest.class)
public class SqlQueryTest {

	/* slf4j-logger */
	final static Logger log = LoggerFactory.getLogger(SqlQueryTest.class);

	private static final URI SERVER_URL = URI
			.create("http://localhost:8080/v1/asio/query");

	private static final String PARAM_QUERY = "query";
	private static final String COUNT_QUERY = "SELECT COUNT(*) FROM person";
	private static final String SCAN_QUERY = "SELECT * FROM person";

	private static final String PERSON_CSV_REFERENCE = "/reference/person_full.csv";
	private static final String PERSON_XML_REFERENCE = "/reference/person_full.xml";

	private static final MediaType CSV = MediaType.valueOf("text/csv")
			.withCharset(Charsets.UTF_8.name());
	private static final MediaType XML = MediaType.APPLICATION_XML_TYPE
			.withCharset(Charsets.UTF_8.name());

	private WebClient client;
	private Response response;

	@Rule public JaxrsClientProvider provider = new JaxrsClientProvider(
			SERVER_URL);

	@Before
	public void setUp() {
		client = provider.getClient();
	}

	@Test
	public void valid_query_as_uri_param() throws Exception {
		client.accept(APPLICATION_XML_TYPE).query(PARAM_QUERY, COUNT_QUERY);
		response = client.get();
		verify(response);
	}

	@Test
	public void valid_query_as_form_param() throws Exception {
		client.accept(APPLICATION_XML_TYPE);
		final Form values = new Form();
		values.set(PARAM_QUERY, COUNT_QUERY);
		response = client.form(values);
		verify(response);
	}

	@Test
	public void valid_query_as_payload() throws Exception {
		client.accept(APPLICATION_XML_TYPE).type("application/sql-query");
		response = client.post(COUNT_QUERY);
		verify(response);
	}

	@Ignore
	@Test
	public void delivers_csv() throws Exception {
		client.accept(CSV).query(PARAM_QUERY, SCAN_QUERY);
		response = client.get();
		assertEquals(SUCCESSFUL, familyOf(response.getStatus()));
		assertTrue(CSV.isCompatible(response.getMediaType()));
		final InputStream content = (InputStream) response.getEntity();
		final String contentText = CharStreams.toString(new InputStreamReader(
				content, "UTF-8"));
		final InputStream expected = this.getClass().getResourceAsStream(
				PERSON_CSV_REFERENCE);
		final String expectedText = CharStreams.toString(new InputStreamReader(
				expected, "UTF-8"));
		assertEquals("response body not matching expected query result",
				expectedText, contentText);
	}

	@Ignore
	@Test
	public void delivers_xml() throws Exception {
		client.accept(MediaType.APPLICATION_XML).query(PARAM_QUERY, SCAN_QUERY);
		response = client.get();
		assertEquals(SUCCESSFUL, familyOf(response.getStatus()));
		assertTrue(XML.isCompatible(response.getMediaType()));
		final InputStream content = (InputStream) response.getEntity();
		final String contentText = CharStreams.toString(new InputStreamReader(
				content, "UTF-8"));
		final InputStream expected = this.getClass().getResourceAsStream(
				PERSON_XML_REFERENCE);
		final String expectedText = CharStreams.toString(new InputStreamReader(
				expected, "UTF-8"));
		assertEquals("response body not matching expected query result",
				expectedText, contentText);
	}

	@Test
	public void bad_query_parameter() throws Exception {
		client.accept(APPLICATION_XML_TYPE).query(PARAM_QUERY, "");
		response = client.get();
		assertEquals(CLIENT_ERROR, familyOf(response.getStatus()));
	}

	private void verify(final Response response) {
		assertEquals(SUCCESSFUL, familyOf(response.getStatus()));
		assertTrue(APPLICATION_XML_TYPE.isCompatible(response.getMediaType()));
	}
}
