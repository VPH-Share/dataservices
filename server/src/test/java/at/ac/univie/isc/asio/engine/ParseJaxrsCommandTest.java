/*
 * #%L
 * asio server
 * %%
 * Copyright (C) 2013 - 2015 Research Group Scientific Computing, University of Vienna
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package at.ac.univie.isc.asio.engine;

import at.ac.univie.isc.asio.Id;
import at.ac.univie.isc.asio.Language;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import java.util.Arrays;
import java.util.Collections;

import static at.ac.univie.isc.asio.junit.IsMultimapContaining.hasEntries;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ParseJaxrsCommandTest {
  private final HttpHeaders headers = mock(HttpHeaders.class);

  @Rule
  public ExpectedException error = ExpectedException.none();

  private final ParseJaxrsCommand parser = ParseJaxrsCommand.with(Language.valueOf("TEST"));
  private Command params;

  @Before
  public void setup() {
    when(headers.getAcceptableMediaTypes()).thenReturn(Collections.<MediaType>emptyList());
    when(headers.getRequestHeaders()).thenReturn(new MultivaluedHashMap<String, String>());
  }

  @Test
  public void should_set_language_param() throws Exception {
    params = ParseJaxrsCommand.with(Id.valueOf("test"), Language.valueOf("test")).collect();
    assertThat(params.language(), is(Language.valueOf("test")));
  }

  @Test
  public void should_set_schema_param() throws Exception {
    params = ParseJaxrsCommand.with(Id.valueOf("test"), Language.valueOf("test")).collect();
    assertThat(params.schema(), is(Id.valueOf("test")));
  }

  @Test
  public void should_add_all_params_from_a_map() throws Exception {
    final MultivaluedHashMap<String, String> map = new MultivaluedHashMap<>();
    map.putSingle("single", "value");
    map.put("multiple", Arrays.asList("one", "two"));
    params = parser.argumentsFrom(map).withHeaders(headers).collect();
    assertThat(params.properties(), hasEntries("multiple", "one", "two"));
    assertThat(params.require("single"), is("value"));
  }

  @Test
  public void should_add_body_param() throws Exception {
    params = parser.body("command", MediaType.valueOf("application/test-query")).withHeaders(headers).collect();
    assertThat(params.require("query"), is("command"));
  }

  @Test
  public void should_fail_on_language_mismatch() throws Exception {
    params = parser.body("command", MediaType.valueOf("application/sql-query")).withHeaders(headers).collect();
    error.expect(NotSupportedException.class);
    params.failIfNotValid();
  }

  @Test
  public void should_fail_on_malformed_content_type() throws Exception {
    params = parser.body("command", MediaType.valueOf("application/json")).withHeaders(headers).collect();
    error.expect(NotSupportedException.class);
    params.failIfNotValid();
  }

  @Test
  public void should_fail_on_null_body() throws Exception {
    params = parser.body(null, MediaType.valueOf("application/test-update")).withHeaders(headers).collect();
    error.expect(BadRequestException.class);
    params.failIfNotValid();
  }

  @Test
  public void should_fail_on_null_content_type() throws Exception {
    params = parser.body("test", null).withHeaders(headers).collect();
    error.expect(NotSupportedException.class);
    params.failIfNotValid();
  }

  @Test
  public void should_fail_on_null_headers() throws Exception {
    params = parser.withHeaders(null).collect();
    error.expect(NullPointerException.class);
    params.failIfNotValid();
  }


  @Test
  public void should_add_accepted_from_headers() throws Exception {
    when(headers.getAcceptableMediaTypes())
        .thenReturn(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM_TYPE, MediaType.TEXT_XML_TYPE));
    params = parser.withHeaders(headers).collect();
    assertThat(params.acceptable(),
        containsInAnyOrder(MediaType.APPLICATION_OCTET_STREAM_TYPE, MediaType.TEXT_XML_TYPE));
  }
}
