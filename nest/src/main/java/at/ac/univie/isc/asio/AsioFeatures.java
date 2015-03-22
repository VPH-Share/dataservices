package at.ac.univie.isc.asio;

/**
 * All feature toggles.
 */
public class AsioFeatures {
  public static final String VPH_METADATA = "asio.feature.vphMetadata";
  public static final String VPH_URI_AUTH = "asio.feature.vphUriAuth";
  public static final String SIMPLE_AUTH = "asio.feature.simpleAuth";

  /**
   * Enable metadata lookup in the vph metadata repository. If enabled, the repository http endpoint
   * may be configured by setting the {@code asio.metadata-repository} property.
   */
  public boolean vphMetadata = false;

  /**
   * Enable authorization from role name embedded in request URI.
   * <p>
   * <strong>WARNING</strong>: If this is enabled, the server is unprotected unless external
   * authentication is provided, e.g. a proxy server.
   * </p>
   */
  public boolean vphUriAuth = false;

  /**
   * Automatically create a user account for each {@link at.ac.univie.isc.asio.security.Role},
   * with name equal to the role name.
   */
  public boolean simpleAuth = false;

  @Override
  public String toString() {
    return "AsioFeatures{" +
        "vphMetadata=" + vphMetadata +
        ", vphUriAuth=" + vphUriAuth +
        ", simpleAuth=" + simpleAuth +
        '}';
  }

  public boolean isVphUriAuth() {
    return vphUriAuth;
  }

  public void setVphUriAuth(final boolean vphUriAuth) {
    this.vphUriAuth = vphUriAuth;
  }

  public boolean isSimpleAuth() {
    return simpleAuth;
  }

  public void setSimpleAuth(final boolean simpleAuth) {
    this.simpleAuth = simpleAuth;
  }

  public boolean isVphMetadata() {
    return vphMetadata;
  }

  public void setVphMetadata(final boolean vphMetadata) {
    this.vphMetadata = vphMetadata;
  }
}