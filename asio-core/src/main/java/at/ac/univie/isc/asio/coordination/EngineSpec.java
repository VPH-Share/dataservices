package at.ac.univie.isc.asio.coordination;

import java.util.Set;

import at.ac.univie.isc.asio.DatasetOperation.SerializationFormat;

public interface EngineSpec {

  public static enum Type {
    SQL, SPARQL
  }

  Type type();

  Set<SerializationFormat> supports();
}
