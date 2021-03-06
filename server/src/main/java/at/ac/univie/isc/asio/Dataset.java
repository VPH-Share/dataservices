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
package at.ac.univie.isc.asio;

import at.ac.univie.isc.asio.metadata.SchemaDescriptor;
import com.hp.hpl.jena.rdf.model.Model;
import rx.Observable;

/**
 * Metadata on a deployed dataset.
 */
public interface Dataset {
  /**
   * Local name of this dataset.
   *
   * @return the local name
   */
  Id name();

  /**
   * An {@code Observable}, that may emit a single descriptor of this dataset on subscription.
   * There may not be metadata available, meaning that the observable will be {@code empty}.
   *
   * @return single or zero element sequence of descriptors
   */
  Observable<SchemaDescriptor> metadata();

  /**
   * The relational table structure of this dataset, if it is backed by a relational database.
   *
   * @return single or zero element sequence of sql definition
   */
  Observable<SqlSchema> definition();

  /**
   * The mapping of relational data to rdf, if this dataset provides such a mapping.
   *
   * @return single or zero element sequence of mapping model
   */
  Observable<Model> mapping();
}
