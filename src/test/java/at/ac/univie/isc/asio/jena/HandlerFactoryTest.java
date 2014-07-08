package at.ac.univie.isc.asio.jena;

import com.google.common.collect.ImmutableList;
import com.hp.hpl.jena.query.Query;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class HandlerFactoryTest {

  public static final MediaType XML_RESULTS = MediaType.valueOf("application/sparql-results+xml");
  private static final MediaType JSON_RESULTS = MediaType.valueOf("application/sparql-results+json");
  private static final MediaType CSV_RESULTS = MediaType.valueOf("text/csv");

  public static final MediaType XML_GRAPH = MediaType.valueOf("application/rdf+xml");
  public static final MediaType JSON_GRAPH = MediaType.valueOf("application/rdf+json");


  @Parameterized.Parameters(name = "#{index} (type {0}) & {1} |= '{2}'")
  public static Iterable<Object[]> data() {
    return ImmutableList.copyOf(new Object[][] {
        // simple RESULT format selection
        {Query.QueryTypeSelect, Arrays.asList(MediaType.APPLICATION_XML_TYPE), XML_RESULTS}
        , {Query.QueryTypeAsk, Arrays.asList(XML_RESULTS), XML_RESULTS}
        , {Query.QueryTypeSelect, Arrays.asList(MediaType.APPLICATION_JSON_TYPE), JSON_RESULTS}
        , {Query.QueryTypeAsk, Arrays.asList(JSON_RESULTS), JSON_RESULTS}
        , {Query.QueryTypeAsk, Arrays.asList(CSV_RESULTS), CSV_RESULTS}
        // simple GRAPH format selection
        , {Query.QueryTypeConstruct, Arrays.asList(MediaType.APPLICATION_XML_TYPE), XML_GRAPH}
        , {Query.QueryTypeDescribe, Arrays.asList(XML_GRAPH), XML_GRAPH}
        , {Query.QueryTypeDescribe, Arrays.asList(MediaType.APPLICATION_JSON_TYPE), JSON_GRAPH}
        , {Query.QueryTypeConstruct, Arrays.asList(JSON_GRAPH), JSON_GRAPH}
        // wildcard selection
        , {Query.QueryTypeAsk, Arrays.asList(MediaType.WILDCARD_TYPE), XML_RESULTS}
        , {Query.QueryTypeDescribe, Arrays.asList(MediaType.WILDCARD_TYPE), XML_GRAPH}
        // priority selection
        , {Query.QueryTypeSelect, Arrays.asList(MediaType.APPLICATION_JSON_TYPE, MediaType.WILDCARD_TYPE), JSON_RESULTS}
        , {Query.QueryTypeConstruct, Arrays.asList(MediaType.APPLICATION_JSON_TYPE, MediaType.WILDCARD_TYPE), JSON_GRAPH}
    });
  }

  @Parameterized.Parameter(value = 0)
  public int queryType;
  @Parameterized.Parameter(value = 1)
  public List<MediaType> accepted;
  @Parameterized.Parameter(value = 2)
  public MediaType expectedFormat;

  @Test
  public void should_yield_appropriate_handler_for_query_type() throws Exception {
    final JenaQueryHandler handler = HandlerFactory.select(queryType, accepted);
    assertThat(handler, is(instanceOf(matchingHandlerType())));
  }

  @Test
  public void should_serialize_to_expected_format() throws Exception {
    final JenaQueryHandler handler = HandlerFactory.select(queryType, accepted);
    assertThat(handler.format(), is(expectedFormat));
  }

  private Class<?> matchingHandlerType() {
    final Class<?> expectedHandlerType;
    switch (queryType) {
      case Query.QueryTypeSelect:
        expectedHandlerType = SelectHandler.class; break;
      case Query.QueryTypeAsk:
        expectedHandlerType = AskHandler.class; break;
      case Query.QueryTypeConstruct:
        expectedHandlerType = ConstructHandler.class; break;
      case Query.QueryTypeDescribe:
        expectedHandlerType = DescribeHandler.class; break;
      default:
        throw new AssertionError("unknown query type : "+ queryType);
    }
    return expectedHandlerType;
  }
}
