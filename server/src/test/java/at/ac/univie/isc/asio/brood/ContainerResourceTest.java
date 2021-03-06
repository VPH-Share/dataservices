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
package at.ac.univie.isc.asio.brood;

import at.ac.univie.isc.asio.Container;
import at.ac.univie.isc.asio.Id;
import at.ac.univie.isc.asio.io.TransientPath;
import com.google.common.collect.ImmutableSet;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import javax.ws.rs.NotSupportedException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;

import static at.ac.univie.isc.asio.jaxrs.ResponseMatchers.hasStatus;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;

public class ContainerResourceTest {
  private final Warden warden = Mockito.mock(Warden.class);

  private ContainerResource subject = new ContainerResource(warden);

  // === REST api

  @Test
  public void should_respond_with_OK_if_disposed_successfully() throws Exception {
    given(warden.dispose(Id.valueOf("test"))).willReturn(true);
    final Response response = subject.deleteContainer(Id.valueOf("test"));
    assertThat(response, hasStatus(Response.Status.OK));
  }

  @Test
  public void should_respond_with_NOT_FOUND_if_target_not_found_on_dispose() throws Exception {
    final Response response = subject.deleteContainer(Id.valueOf("test"));
    assertThat(response, hasStatus(Response.Status.NOT_FOUND));
  }

  @Rule
  public final TransientPath temp = TransientPath.file(new byte[0]);

  @Test
  public void should_respond_with_CREATED_if_deployed_successfully() throws Exception {
    final Response response = subject.createContainer(Id.valueOf("test"), temp.path().toFile(), MediaType.APPLICATION_JSON_TYPE);
    assertThat(response, hasStatus(Response.Status.CREATED));
  }

  @Test(expected = NotSupportedException.class)
  public void should_reject_unsupported_media_type() throws Exception {
    final Response response = subject.createContainer(Id.valueOf("test"), temp.path().toFile(), MediaType.APPLICATION_XML_TYPE);
  }

  @Test
  public void should_respond_with_list_of_deployed_container_ids() throws Exception {
    subject.onDeploy(new ContainerEvent.Deployed(StubContainer.create("first")));
    subject.onDeploy(new ContainerEvent.Deployed(StubContainer.create("second")));
    final ImmutableSet<Id> expected = ImmutableSet.of(Id.valueOf("first"), Id.valueOf("second"));
    final Collection<Id> names = subject.listContainers();
    assertThat(names, Matchers.<Collection<Id>>equalTo(expected));
  }

  @Test
  public void should_respond_with_deployed_container_info() throws Exception {
    final Container expected = StubContainer.create("test");
    subject.onDeploy(new ContainerEvent.Deployed(expected));
    final Container response = subject.findContainer(Id.valueOf("test"));
    assertThat(response, equalTo(expected));
  }

  @Test(expected = Id.NotFound.class)
  public void should_respond_with_NOT_FOUND_if_container_not_present() throws Exception {
    subject.findContainer(Id.valueOf("test"));
  }
}
