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
import com.google.common.collect.ImmutableTable;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static at.ac.univie.isc.asio.matcher.RestAssuredMatchers.compatibleTo;
import static at.ac.univie.isc.asio.matcher.RestAssuredMatchers.sqlCsvEqualTo;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

/**
 * Extended functionality of SQL protocol endpoints.
 */
@Category(Integration.class)
public class FeatureSql extends IntegrationTest {
  
  private static final String NOOP_UPDATE = "DROP TABLE IF EXISTS test_table_gaga";
  private static final String NOOP_SELECT = "SELECT 1 AS RESULT";

  @Before
  public void skipIfUnsupported() {
    ensureLanguageSupported("sql");
  }

  // @formatter:off

  @Test
  public void deny_update_with_read_permission() throws Exception {
    ensureSecured();
    given().role("read").and()
      .formParam("update", NOOP_UPDATE)
    .when()
      .post("/sql")
    .then()
      .statusCode(is(HttpStatus.SC_FORBIDDEN));
  }

  @Test
  public void deny__USE__command() throws Exception {
    given().role("full").and()
      .formParam("query", "USE information_schema")
    .when()
      .post("/sql")
    .then()
      .statusCode(equalTo(HttpStatus.SC_BAD_REQUEST));
  }

  public class Execute {
    @Test
    public void select() throws Exception {
      given().role("read").and()
        .header(HttpHeaders.ACCEPT, "text/csv")
        .param("query", "SELECT 1 AS RESULT")
      .when()
        .post("/sql")
      .then()
        .statusCode(is(HttpStatus.SC_OK))
        .content(is(sqlCsvEqualTo(ImmutableTable.of(0, "RESULT", "1"))));
    }

    @Test
    public void update() throws Exception {
      given().role("full").and()
        .header(HttpHeaders.ACCEPT, "application/xml")
        .param("update", "DROP TABLE IF EXISTS test_table_gaga")
      .when()
        .post("/sql")
      .then()
        .statusCode(is(HttpStatus.SC_OK))
        .content("sql.head.@statement", is("DROP TABLE IF EXISTS test_table_gaga"))
        .content("sql.update.@affected", is("0"));
    }
  }

  public class Schema {
    @Test
    public void deliver_xml() throws Exception {
      given().role("read").and()
        .header(HttpHeaders.ACCEPT, "application/xml")
      .when()
        .get("/meta/schema")
      .then()
        .statusCode(is(HttpStatus.SC_OK))
        .contentType(compatibleTo("application/xml"));
    }

    @Test
    public void deliver_json() throws Exception {
      given().role("read").and()
        .header(HttpHeaders.ACCEPT, "application/json")
      .when()
        .get("/meta/schema")
      .then()
        .statusCode(is(HttpStatus.SC_OK))
        .contentType(compatibleTo("application/json"));
    }

    @Test
    public void reject_unauthorized_access() throws Exception {
      ensureSecured();
      given().role("none").and()
          .header(HttpHeaders.ACCEPT, "application/json")
      .when()
        .get("/meta/schema")
      .then()
        .statusCode(is(HttpStatus.SC_FORBIDDEN));
    }
  }
  
  public class MediaType {
    @Test
    public void select_for_webrowset() throws Exception {
      given().role("read").and()
        .formParam("query", NOOP_SELECT)
        .header(HttpHeaders.ACCEPT, "application/webrowset+xml")
      .when()
        .post("/sql")
      .then()
        .statusCode(is(HttpStatus.SC_OK))
        .contentType(compatibleTo("application/webrowset+xml"));
    }
  
    @Ignore("sql-results+xml not implemented")
    @Test
    public void select_for_xml() throws Exception {
      given().role("read").and()
        .formParam("query", NOOP_SELECT)
        .header(HttpHeaders.ACCEPT, "application/xml")
      .when()
        .post("/sql")
      .then()
        .statusCode(is(HttpStatus.SC_OK))
        .contentType(compatibleTo("application/sql-results+xml"));
    }
  
    @Ignore("sql-results+json not implemented")
    @Test
    public void select_for_json() throws Exception {
      given().role("read").and()
        .formParam("query", NOOP_SELECT)
        .header(HttpHeaders.ACCEPT, "application/json")
      .when()
        .post("/sql")
      .then()
        .statusCode(is(HttpStatus.SC_OK))
        .contentType(compatibleTo("application/sql-results+json"));
    }

    @Test
    public void update_for_xml() throws Exception {
      given().role("full").and()
        .formParam("update", NOOP_UPDATE)
        .header(HttpHeaders.ACCEPT, "application/xml")
      .when()
        .post("/sql")
      .then()
        .statusCode(is(HttpStatus.SC_OK))
        .contentType(compatibleTo("application/sql-results+xml"));
    }
  
    @Ignore("sql-results+json not implemented")
    @Test
    public void update_for_json() throws Exception {
      given().role("full").and()
        .formParam("update", NOOP_UPDATE)
        .header(HttpHeaders.ACCEPT, "application/json")
      .when()
        .post("/sql")
      .then()
        .statusCode(is(HttpStatus.SC_OK))
        .contentType(compatibleTo("application/sql-results+json"));
    }
  }
}
