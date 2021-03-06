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
import at.ac.univie.isc.asio.junit.Rules;
import at.ac.univie.isc.asio.web.HttpServer;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static at.ac.univie.isc.asio.matcher.RestAssuredMatchers.basicAuthPassword;
import static at.ac.univie.isc.asio.matcher.RestAssuredMatchers.basicAuthUsername;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * Delegation of basic auth credentials via federated SPARQL queries.
 */
public class FeatureCredentialDelegation extends IntegrationTest {

  private final MockHandler remote = new MockHandler();

  @Rule
  public HttpServer http =
      interactions.attached(Rules.httpServer("token-delegation-remote").with("/sparql", remote));

  private String federatedQuery() {
    return Pretty.format("SELECT * WHERE { SERVICE <%ssparql> { ?s ?p ?o }}", http.address());
  }

  // @formatter:off

  @Test
  public void ensure_sends_remote_request_on_mock_query() throws Exception {
    given().role("read").and()
      .formParam("query", federatedQuery())
    .when()
      .post("/sparql")
    .then();
      assertThat(remote.received(), is(notNullValue()));
  }

  @Test
  public void federated_queries_delegate_password() throws Exception {
//    withDelegated("test-user", "test-password")
    given()
      .role("read")
      .delegate("test-user", "test-password")
      .and()
      .formParam("query", federatedQuery())
    .when()
      .post("/sparql")
    .then();
      assertThat(remote.received(), basicAuthPassword("test-password"));
  }

  @Test
  public void username_is_dropped_in_delegated_credentials() throws Exception {
    given()
      .role("read")
      .delegate("test-user", "test-password")
      .and()
      .formParam("query", federatedQuery())
    .when()
      .post("/sparql")
    .then();
      assertThat(remote.received(), basicAuthUsername(""));
  }

  @Test
  public void handles_large_credential_payloads() throws Exception {
    given()
      .role("read")
      .delegate("test-user", Strings.repeat("test", 1000))
      .and()
      .formParam("query", federatedQuery())
    .when()
      .post("/sparql")
    .then();
      assertThat(remote.received(), basicAuthPassword(Strings.repeat("test", 1000)));
  }

  // @formatter:on

  /**
   * Encode username and password credentials for basic authentication
   *
   * @param username principal
   * @param password secret password
   * @return encoded header string
   */
  private String token(final String username, final String password) {
    return "Basic " + BaseEncoding.base64().encode(Payload.encodeUtf8(username + ":" + password));
  }

  /**
   * noop handler, that records exchanges
   */
  private static class MockHandler implements HttpHandler {
    public final AtomicReference<HttpExchange> received = new AtomicReference<>();

    public HttpExchange received() {
      final HttpExchange exchange = received.get();
      assert exchange != null : "no http exchange captures";
      return exchange;
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
      final boolean wasNull = received.compareAndSet(null, exchange);
      assert wasNull : "multiple requests to mock server, lost " + exchange;
      exchange.sendResponseHeaders(500, -1);
      exchange.close();
    }
  }
}
