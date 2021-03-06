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
package at.ac.univie.isc.asio.matcher;

import com.sun.net.httpserver.HttpExchange;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static com.google.common.io.BaseEncoding.base64;

class ExpectCredentials extends TypeSafeMatcher<HttpExchange> {

  private final Matcher<String> username;
  private final Matcher<String> password;

  ExpectCredentials(final Matcher<String> username, final Matcher<String> password) {
    this.username = username;
    this.password = password;
  }

  @Override
  protected boolean matchesSafely(final HttpExchange item) {
    final String authorization = item.getRequestHeaders().getFirst("Authorization");
    if (authorization != null && authorization.startsWith("Basic ")) {
      final String decoded = new String(base64().decode(authorization.substring(6)));
      final String[] parts = decoded.split(":");
      return username.matches(parts[0]) && password.matches(parts[1]);
    }
    return false;
  }

  @Override
  protected void describeMismatchSafely(final HttpExchange item, final Description mismatchDescription) {
    mismatchDescription.appendText(" was ");
    final String authorization = item.getRequestHeaders().getFirst("Authorization");
    if (authorization != null && authorization.startsWith("Basic ")) {
      mismatchDescription.appendValue("Basic " + new String(base64().decode(authorization.substring(6))));
    } else {
      mismatchDescription.appendValue(authorization);
    }
  }

  @Override
  public void describeTo(final Description description) {
    description
        .appendText("basic authentication with username ").appendDescriptionOf(username)
        .appendText(" and password ").appendDescriptionOf(password);
  }
}
