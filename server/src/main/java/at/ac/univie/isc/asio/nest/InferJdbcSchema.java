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
package at.ac.univie.isc.asio.nest;

import at.ac.univie.isc.asio.Brood;
import at.ac.univie.isc.asio.database.Jdbc;
import at.ac.univie.isc.asio.tool.JdbcTools;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.annotation.Nonnull;

/**
 * If no jdbc:schema attribute is set explicitly, attempt to infer the default database from the
 * jdbc url. Works only for mysql connection strings.
 */
@Brood
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InferJdbcSchema implements Configurer {

  @Nonnull
  @Override
  public NestConfig apply(final NestConfig input) {
    final Jdbc jdbc = input.getJdbc();
    if (jdbc.getSchema() == null) {
      jdbc.setSchema(JdbcTools.inferSchema(jdbc.getUrl()).orNull());
    }
    return input;
  }
}
