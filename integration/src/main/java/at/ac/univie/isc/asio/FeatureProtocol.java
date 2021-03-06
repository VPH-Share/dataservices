/*
 * #%L
 * asio integration
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
package at.ac.univie.isc.asio;

import at.ac.univie.isc.asio.integration.IntegrationTest;
import at.ac.univie.isc.asio.io.Payload;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.URLEncoder;
import java.util.Arrays;

import static at.ac.univie.isc.asio.matcher.RestAssuredMatchers.compatibleTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assume.assumeThat;

/**
 * Verify compliance of an endpoint with the generalized protocol specification.
 */
@Category(Integration.class)
@RunWith(Parameterized.class)
public class FeatureProtocol extends IntegrationTest {

  @Parameterized.Parameters(name = "{index} : {0}-{1}")
  public static Iterable<Object[]> variants() {
    // { language, operation, noop_command, required_permission }
    return Arrays.asList(new Object[][] {
        {"sql", "query", "SELECT 1", "read"},
        {"sql", "update", "DROP TABLE IF EXISTS test_gaga_12345", "full"},
        {"sparql", "query", "ASK {}", "read"},
    });
  }

  @Parameterized.Parameter(0)
  public String language;
  @Parameterized.Parameter(1)
  public String operation;
  @Parameterized.Parameter(2)
  public String noop;
  @Parameterized.Parameter(3)
  public String permission;

  private void ensureReadOnly() {
    assumeThat("modifying operations not allowed via GET", operation, is("query"));
  }

  @Before
  public void ensureEndpointForLanguageExists() {
    ensureLanguageSupported(language);
  }

  // @formatter:off

  // === valid operations ==========================================================================

  @Test
  public void valid_via_http_GET() throws Exception {
    ensureReadOnly();
    given().role(permission).and()
      .param(operation,  noop)
    .when()
      .get("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_OK));
  }

  @Test
  public void valid_via_http_form_submission() throws Exception {
    given().role(permission).and()
      .param(operation, noop)
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_OK));
  }

  @Test
  public void valid_via_http_POST_with_raw_payload() throws Exception {
    final String operationContentType = Pretty.format("application/%s-%s", language, operation);
    given().role(permission).and()
      .body(Payload.encodeUtf8(noop))
      .contentType(operationContentType)
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_OK));
  }


  // === content negotiation =======================================================================

  @Test
  public void support_xml_response_format() throws Exception {
    given().role(permission).and()
      .param(operation, noop)
      .header(HttpHeaders.ACCEPT, "application/xml")
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_OK))
      .contentType(compatibleTo("application/xml"));
  }

  @Test
  public void support_json_response_format() throws Exception {
    // TODO : implement json formats
    assumeThat("sql json formats not implemented", language, is(not("sql")));
    given().role(permission).and()
      .param(operation, noop)
      .header(HttpHeaders.ACCEPT, "application/json")
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_OK))
      .contentType(compatibleTo("application/json"));
  }

  @Test
  public void support_csv_response_format() throws Exception {
    given().role(permission).and()
      .param(operation, noop)
      .header(HttpHeaders.ACCEPT, "text/csv")
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_OK))
      .contentType(compatibleTo("text/csv"));
  }

  @Test
  public void defaults_to_xml_content_if_no_accept_header_given() throws Exception {
    given().role(permission).and()
      .param(operation, noop)
    .when()
      .post("/{language}", language)
    .then()
      .contentType(compatibleTo("application/xml"));
  }

  @Test
  public void defaults_to_xml_content_if_wildcard_accept_header_given() throws Exception {
    given().role(permission).and()
      .header(HttpHeaders.ACCEPT, "*/*")
      .param(operation, noop)
    .when()
      .post("/{language}", language)
    .then()
      .contentType(compatibleTo("application/xml"));
  }

  @Test
  public void override_accepted_header_using_asio_query_parameter() throws Exception {
    given().role(permission).and()
      .header(HttpHeaders.ACCEPT, "application/json")
      .param(operation, noop)
      .queryParam("x-asio-accept", "text/csv")
    .when()
      .post("/{language}", language)
    .then()
      .contentType(compatibleTo("text/csv"));
  }

  @Test
  public void override_accepted_header_using_cxf_query_parameter() throws Exception {
    given().role(permission).and()
      .header(HttpHeaders.ACCEPT, "application/json")
      .param(operation, noop)
      .queryParam("_type", "text/csv")
    .when()
      .post("/{language}", language)
    .then()
      .contentType(compatibleTo("text/csv"));
  }

  @Test
  public void reject_unacceptable_media_type() throws Exception {
    given().role(permission).and()
      .header(HttpHeaders.ACCEPT, "image/jpeg")
      .param(operation, noop)
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_NOT_ACCEPTABLE));
  }

  // === invalid operations ========================================================================

  @Test
  public void reject_unsupported_language() throws Exception {
    given().role(permission).and()
      .param(operation, noop)
    .when()
      .get("/unknown-language")
    .then()
      .statusCode(is(HttpStatus.SC_NOT_FOUND));
  }

  @Test
  public void reject_insufficient_permission() throws Exception {
    ensureSecured();
    assumeThat("operation requires no permission", permission, is(not("none")));
    given().role("none").and()
      .param(operation, noop)
    .when()
      .get("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_FORBIDDEN));
  }

  @Test
  public void reject_modifying_operation_via_http_GET() throws Exception {
    assumeThat("not a modifying operation", operation, is("update"));
    given().role(permission).and()
      .param(operation, noop)
    .when()
      .get("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_FORBIDDEN));
  }

  @Test
  @Ignore("FIXME not implemented")
  public void reject_unknown_permission() throws Exception {
    given().role("unknown").and()
      .param(operation, noop)
    .when()
      .get("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_NOT_FOUND));
  }

  @Test
  public void reject_operation_via_http_PUT() throws Exception {
    given().role(permission).and()
      .param(operation, noop)
    .when()
      .put("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_METHOD_NOT_ALLOWED));
  }

  @Test
  public void reject_operation_via_http_DELETE() throws Exception {
    given().role(permission).and()
      .param(operation, noop)
    .when()
      .delete("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_METHOD_NOT_ALLOWED));
  }

  @Test
  public void reject_empty_query_parameter_value() throws Exception {
    given().role(permission).and()
      .param(operation, "")
    .when()
      .get("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void reject_empty_form_parameter_value() throws Exception {
    given().role(permission).and()
      .param(operation, "")
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void reject_empty_payload_parameter_value() throws Exception {
    final String operationContentType = Pretty.format("application/%s-%s", language, operation);
    given().role(permission).and()
      .content(new byte[] {})
      .contentType(operationContentType)
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void reject_duplicated_query_parameter() throws Exception {
    ensureReadOnly();
    given().role(permission).and()
      .param(operation, noop, noop)
    .when()
      .get("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void reject_duplicated_form_parameter() throws Exception {
    given().role(permission).and()
      .param(operation, noop, noop)
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  public void reject_malformed_payload_content_type() throws Exception {
    given().role(permission).and()
      .content(Payload.encodeUtf8(noop))
      .contentType("text/plain")
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE));
  }

  @Test
  @Ignore("cxf ignores missing content-type") // FIXME : strict mode
  public void reject_form_submission_without_form_content_type() throws Exception {
    final String form = URLEncoder.encode(operation + "=" + noop, Charsets.UTF_8.name());
    given().role(permission).and() // rest assured cannot serialize without content type
      .body(Payload.encodeUtf8(form))
      .header(HttpHeaders.CONTENT_TYPE, "")
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE));
  }

  @Test
  @Ignore("cf infers form type")
  public void reject_post_where_content_type_is_missing() throws Exception {
    given().role(permission).and()
      .body(Payload.encodeUtf8(noop))
      .header(HttpHeaders.CONTENT_TYPE, "")
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE));
  }

  @Test
  @Ignore("cxf handles charset")  // FIXME : strict mode
  public void reject_non_utf8_encoded_payload() throws Exception {
    final String operationContentType =
        Pretty.format("application/%s-%s; charset=UTF-16", language, operation);
    given().role(permission).and()
      .body(noop.getBytes(Charsets.UTF_16))
      .contentType(operationContentType)
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE));
  }

  @Test
  @Ignore("sql errors not translated")
  public void reject_invalid_syntax_payload() throws Exception {
    // hashing the given no-op should produce an illegal command
    given().role(permission).and()
      .param(operation, Hashing.md5().hashString(noop, Charsets.UTF_8).toString())
    .when()
      .post("/{language}", language)
    .then()
      .statusCode(is(HttpStatus.SC_BAD_REQUEST));
  }
}
