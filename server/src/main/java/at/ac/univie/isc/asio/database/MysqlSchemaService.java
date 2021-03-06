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
package at.ac.univie.isc.asio.database;

import at.ac.univie.isc.asio.Id;
import at.ac.univie.isc.asio.SqlSchema;
import at.ac.univie.isc.asio.engine.sql.SqlSchemaBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.dao.DataAccessResourceFailureException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

/**
 * Inspect MySQL database metadata. Note: A {@code schema} is called {@code database} in MySQL.
 * This service implementation accesses the database via a fixed connection pool, but changes
 * the database to the requested one before exploring.
 */
public final class MysqlSchemaService implements RelationalSchemaService {

  /** MySQL system schemas */
  public static final Set<String> INTERNAL_SCHEMA =
      ImmutableSet.of("mysql", "information_schema", "performance_schema");

  private final DataSource pool;

  public MysqlSchemaService(final DataSource pool) {
    this.pool = pool;
  }

  @Override
  public SqlSchema explore(final Id target) throws Id.NotFound {
    final SqlSchemaBuilder builder = SqlSchemaBuilder.create().noCatalog();
    try (final Connection connection = pool.getConnection()) {
      final DSLContext jooq = DSL.using(connection, SQLDialect.MYSQL);
      final org.jooq.Schema schema = findActiveSchema(jooq, target);
      builder.switchSchema(schema);
      for (final org.jooq.Table<?> sqlTable : schema.getTables()) {
        builder.add(sqlTable);
      }
    } catch (final SQLException e) {
      throw new DataAccessResourceFailureException(e.getMessage(), e);
    }
    return builder.build();
  }

  private org.jooq.Schema findActiveSchema(final DSLContext jooq, final Id target) throws SQLException {
    final String activeSchemaName = target.asString();
    if (INTERNAL_SCHEMA.contains(activeSchemaName.toLowerCase(Locale.ENGLISH))) {
      throw new Id.NotFound(target);
    }
    final org.jooq.Schema schema = Iterables.getOnlyElement(jooq.meta().getCatalogs()).getSchema(activeSchemaName);
    if (schema == null) {
      throw new Id.NotFound(target);
    }
    return schema;
  }
}
