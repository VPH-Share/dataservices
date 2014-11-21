package at.ac.univie.isc.asio.jaxrs;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.security.Principal;

/**
 * <strong>Disables authorization</strong> for unit tests.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class DisableAuthorizationFilter implements ContainerRequestFilter {

  @Override
  public void filter(final ContainerRequestContext requestContext) throws IOException {
    requestContext.setSecurityContext(new PermitAllSecurityContext());
  }

  public static final Principal FAKE_PRINCIPAL = new Principal() {
    @Override
    public String getName() {
      return "PermitAllPrincipal";
    }

    @Override
    public String toString() {
      return getName();
    }
  };

  private static final class PermitAllSecurityContext implements SecurityContext {
    @Override
    public Principal getUserPrincipal() {
      return FAKE_PRINCIPAL;
    }

    @Override
    public boolean isUserInRole(final String role) {
      return true;
    }

    @Override
    public boolean isSecure() {
      return false;
    }

    @Override
    public String getAuthenticationScheme() {
      return SecurityContext.BASIC_AUTH;
    }
  }
}