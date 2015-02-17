package at.ac.univie.isc.asio;

import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.specification.RequestSpecification;

import java.net.URI;

import static java.util.Objects.requireNonNull;

/**
 * Deployment specific configuration for rest-assured integration tests.
 */
public abstract class AsioSpec {
  /**
   * Authorization is applied by including the permission in the request URI.
   *
   * @param address base service address
   * @return uri based asio spec
   */
  public static AsioSpec withUriAuthorization(final URI address) {
    return new UriBased(address);
  }

  /**
   * Authorization is skipped.
   *
   * @param address base service address
   * @return asio spec without authorization
   */
  public static AsioSpec withoutAuthorization(final URI address) {
    return new NoAuth(address);
  }

  private final URI root;

  private AsioSpec(final URI root) {
    this.root = requireNonNull(root);
  }

  protected URI getRoot() {
    return root;
  }

  /**
   * Create rest-assured request to the service root with given permission.
   *
   * @param permission required for the test request
   * @return initialized request specification
   */
  public RequestSpecification requestWith(final String permission) {
    requireNonNull(permission);
    final RequestSpecBuilder request = new RequestSpecBuilder().setBaseUri(root);
    final RequestSpecBuilder authorized = authorize(request, permission);
    return authorized.build();
  }

  protected abstract RequestSpecBuilder authorize(final RequestSpecBuilder spec, final String permission);

  public abstract URI authorizedAddress(final String permission);

  public abstract boolean isAuthorizing();

  /**
   * Use the URI based authorization mechanism
   */
  private static class UriBased extends AsioSpec {
    private UriBased(final URI root) {
      super(root);
    }

    @Override
    protected RequestSpecBuilder authorize(final RequestSpecBuilder spec, final String permission) {
      return spec.setBaseUri(authorizedAddress(permission));
    }

    @Override
    public URI authorizedAddress(final String permission) {
      return this.getRoot().resolve(permission + "/");
    }

    @Override
    public boolean isAuthorizing() {
      return true;
    }
  }


  /**
   * Do not modify requests, no authorization is applied
   */
  private static class NoAuth extends AsioSpec {
    private NoAuth(final URI root) {
      super(root);
    }

    @Override
    protected RequestSpecBuilder authorize(final RequestSpecBuilder spec, final String ignored) {
      return spec;
    }

    @Override
    public URI authorizedAddress(final String permission) {
      return this.getRoot();
    }

    @Override
    public boolean isAuthorizing() {
      return false;
    }
  }
}