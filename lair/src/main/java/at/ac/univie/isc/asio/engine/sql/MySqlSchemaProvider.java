package at.ac.univie.isc.asio.engine.sql;

import at.ac.univie.isc.asio.SqlSchema;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.jooq.Catalog;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.Schema;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

import static org.jooq.impl.DSL.field;

public class MySqlSchemaProvider implements Supplier<SqlSchema> {
  /** MySQL system schemas */
  public static final Set<String> INTERNAL_SCHEMA =
      ImmutableSet.of("mysql", "information_schema", "performance_schema");
  /** {@code SELECT DATABASE()} yields database currently in use */
  private static final Field<String> ACTIVE_DATABASE = field("DATABASE()", String.class).as("schema");

  private final DataSource db;

  public MySqlSchemaProvider(final DataSource db) {
    this.db = db;
  }

  @Override
  public SqlSchema get() {
    final SqlSchemaBuilder builder = SqlSchemaBuilder.create().noCatalog();
    try (final Connection conn = db.getConnection()) {
      final DSLContext jooq = DSL.using(conn, SQLDialect.MYSQL);
      final Schema schema = findActiveSchema(jooq);
      builder.switchSchema(schema);
      for (final org.jooq.Table<?> sqlTable : schema.getTables()) {
        builder.add(sqlTable);
      }
    } catch (SQLException e) {
      throw new H2SchemaProvider.RepositoryFailure(e);
    }
    return builder.build();
  }

  private Schema findActiveSchema(final DSLContext jooq) throws SQLException {
    final String activeSchemaName = jooq.select(ACTIVE_DATABASE).fetchOne(ACTIVE_DATABASE);
    if (INTERNAL_SCHEMA.contains(activeSchemaName.toLowerCase(Locale.ENGLISH))) {
      throw new SQLException("access to internal schema <"+ activeSchemaName +"> not allowed");
    }
    final Catalog dummyCatalog = Iterables.getOnlyElement(jooq.meta().getCatalogs());
    return dummyCatalog.getSchema(activeSchemaName);
  }
}
