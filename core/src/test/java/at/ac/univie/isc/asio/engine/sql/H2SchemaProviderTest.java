package at.ac.univie.isc.asio.engine.sql;

import at.ac.univie.isc.asio.io.Classpath;
import at.ac.univie.isc.asio.Column;
import at.ac.univie.isc.asio.ObjectFactory;
import at.ac.univie.isc.asio.SqlSchema;
import at.ac.univie.isc.asio.Table;
import at.ac.univie.isc.asio.sql.Database;
import com.google.common.collect.Iterables;
import org.jooq.impl.SQLDataType;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.IsEqualIgnoringCase.equalToIgnoringCase;
import static org.junit.Assert.assertThat;

public class H2SchemaProviderTest {
  private final Database db = Database.create("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1").build()
      .execute(Classpath.read("database/SchemaProviderTest-schema.sql"));
  private final H2SchemaProvider subject =
      new H2SchemaProvider(new JdbcFactory<>(db.datasource(), JdbcSpec.connectTo("jdbc:h2:mem:test").complete()));

  @Test
  public void fetches_public_table_metadata() throws Exception {
    final SqlSchema schema = subject.get();
    final List<Table> tables = schema.getTable();
    assertThat(tables, hasSize(1));
  }

  @Test
  public void validate_extracted_metadata() throws Exception {
    final SqlSchema schema = subject.get();
    final Table table = Iterables.getOnlyElement(schema.getTable());
    assertThat(table.getName(), is(equalToIgnoringCase("SAMPLE")));
    assertThat(table.getSchema(), is(equalToIgnoringCase("PUBLIC")));
    assertThat(table.getCatalog(), is(equalToIgnoringCase("TEST")));
  }

  @Test
  public void validate_extracted_columns() throws Exception {
    final ObjectFactory jaxb = new ObjectFactory();
    final SqlSchema schema = subject.get();
    final Table table = Iterables.getOnlyElement(schema.getTable());
    final Column idColumn = jaxb.createColumn()
            .withName("ID")
            .withType(XmlSchemaType.LONG.qname())
            .withSqlType(SQLDataType.INTEGER.getTypeName())
            .withLength(10);
    final Column dataColumn = jaxb.createColumn()
            .withName("DATA")
            .withType(XmlSchemaType.STRING.qname())
            .withSqlType(SQLDataType.VARCHAR.getTypeName())
            .withLength(255);
    assertThat(table.getColumn(), containsInAnyOrder(idColumn, dataColumn));
  }
}
