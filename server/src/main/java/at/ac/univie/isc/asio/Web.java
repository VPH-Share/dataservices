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

import at.ac.univie.isc.asio.insight.ExplorerPageRedirectFilter;
import at.ac.univie.isc.asio.jaxrs.ContentNegotiationDefaultsFilter;
import at.ac.univie.isc.asio.jaxrs.ContentNegotiationOverrideFilter;
import at.ac.univie.isc.asio.jaxrs.VndErrorMapper;
import at.ac.univie.isc.asio.metadata.JenaModelWriter;
import at.ac.univie.isc.asio.tool.ExpandingQNameSerializer;
import at.ac.univie.isc.asio.tool.MediaTypeSerializer;
import at.ac.univie.isc.asio.tool.PrincipalMixin;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.threetenbp.ThreeTenModule;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.filter.HiddenHttpMethodFilter;

import javax.servlet.DispatcherType;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import java.security.Principal;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

@Configuration
class Web {
  private static final Logger log = getLogger(Web.class);

  @Value("${spring.jersey.debug:false}")
  private boolean debug;

  @Autowired
  private AsioSettings config;

  @ApplicationPath("/")
  private static class Application extends ResourceConfig { /* just carries annotation */ }

  @Bean
  public ResourceConfig baseJerseyConfiguration(final ObjectMapper mapper,
                                                final Set<ContainerRequestFilter> filters) {
    final ResourceConfig config = new Application();
    config.register(VndErrorMapper.class);
    config.register(JenaModelWriter.class);
    log.info(Scope.SYSTEM.marker(), "registering jersey filters {}", filters);
    config.registerInstances(filters.toArray());
    log.info(Scope.SYSTEM.marker(), "registering jackson mapper {}", mapper);
    config.registerInstances(new ObjectMapperProvider(mapper));
    if (debug) {
      config.registerInstances(new LoggingFilter());
    }
    return config;
  }

  @Bean
  public ContentNegotiationDefaultsFilter contentNegotiationDefaultsFilter() {
    return new ContentNegotiationDefaultsFilter(config.api.defaultMediaType, config.api.defaultLanguage);
  }

  @Bean
  public ContentNegotiationOverrideFilter contentNegotiationOverrideFilter() {
    return new ContentNegotiationOverrideFilter(config.api.overrideAcceptParameter);
  }

  /**
   * Order the redirecting filter before the spring security filter chain
   */
  public static final int REDIRECT_FILTER_ORDER = SecurityProperties.DEFAULT_FILTER_ORDER - 5;

  @Bean
  public FilterRegistrationBean explorerPageRedirectFilter(@Value("${server.servletPath}") final String staticBasePath) {
    final ExplorerPageRedirectFilter filter = ExplorerPageRedirectFilter.withStaticPath(staticBasePath);
    final FilterRegistrationBean registration = new FilterRegistrationBean(filter);
    registration.addUrlPatterns("/*");
    registration.setDispatcherTypes(DispatcherType.REQUEST);
    registration.setAsyncSupported(true);
    registration.setOrder(REDIRECT_FILTER_ORDER);
    registration.setName("explorer-redirect-filter");
    return registration;
  }

  @Bean
  public FilterRegistrationBean disableHiddenHttpMethodFilter(final HiddenHttpMethodFilter filter) {
    // HiddenHttpMethodFilter consumes POST form payload and breaks jersey parsing downstream
    final FilterRegistrationBean registration = new FilterRegistrationBean(filter);
    registration.setEnabled(false);
    return registration;
  }

  // modules to be picked up by JacksonAutoConfiguration

  @Bean
  public ThreeTenModule threeTenModule() {
    return new ThreeTenModule();
  }

  @Bean
  @Primary
  public ObjectMapper customObjectMapper(final Jackson2ObjectMapperBuilder builder) {
    // create customized from preconfigured builder
    return builder.createXmlMapper(false)
        .serializers(new ExpandingQNameSerializer(), new MediaTypeSerializer())
        .mixIn(Principal.class, PrincipalMixin.class)
        .build();
  }

  /**
   * Needed to let jackson jaxrs provider find the spring managed ObjectMapper.
   */
  @Provider
  static final class ObjectMapperProvider implements ContextResolver<ObjectMapper> {
    private final ObjectMapper mapper;

    ObjectMapperProvider(final ObjectMapper mapper) {
      this.mapper = mapper;
    }

    @Override
    public ObjectMapper getContext(final Class<?> type) {
      return mapper;
    }
  }
}
