package at.ac.univie.isc.asio.config;

import at.ac.univie.isc.asio.jaxrs.AcceptTunnelFilter;
import at.ac.univie.isc.asio.jaxrs.ContentNegotiationDefaultsFilter;
import at.ac.univie.isc.asio.jaxrs.DatasetExceptionMapper;
import at.ac.univie.isc.asio.jaxrs.LogContextFilter;
import at.ac.univie.isc.asio.security.VphAuthFilter;
import at.ac.univie.isc.asio.security.VphTokenExtractor;
import at.ac.univie.isc.asio.transport.ObservableStreamBodyWriter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.cxf.jaxrs.provider.json.JSONProvider;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Application;
import java.util.Set;

@ApplicationPath("/")
public class JaxrsSpec extends Application {

  public static JaxrsSpec create(final Class<?>... resources) {
    return new JaxrsSpec(ImmutableSet.copyOf(resources));
  }

  private final Set<Class<?>> resources;

  private JaxrsSpec(final Set<Class<?>> resources) {
    this.resources = resources;
  }

  /**
   * @return set of resource class names
   */
  @Override
  public Set<Class<?>> getClasses() {
    return resources;
  }

  /**
   * @return set of instantiated providers
   */
  @Override
  public Set<Object> getSingletons() {
    final ImmutableSet.Builder<Object> providers = ImmutableSet.builder();
    providers.addAll(filters());
    providers.addAll(converters());
    return providers.build();
  }

  private ImmutableSet<Object> converters() {
    final JSONProvider json = new JSONProvider();
    json.setNamespaceMap(ImmutableMap.of("http://isc.univie.ac.at/2014/asio/metadata", "asio"));
    return ImmutableSet.of(
        new DatasetExceptionMapper(),
        new ObservableStreamBodyWriter(),
        json);
  }

  private ImmutableSet<ContainerRequestFilter> filters() {
    return ImmutableSet.of(
        new LogContextFilter(),
        new AcceptTunnelFilter(),
        new ContentNegotiationDefaultsFilter(),
        new VphAuthFilter(new VphTokenExtractor()));
  }
}
