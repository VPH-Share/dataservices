package at.ac.univie.isc.asio.web;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class SslUtils {
  private SslUtils() {}

  public static KeyStore loadKeystore(final String path, final char[] password) throws
                                                                                InitializationFailure {
    final InputStream source = SslUtils.class.getResourceAsStream(path);
    if (source == null) {
      throw new InitializationFailure(new FileNotFoundException(path + " (keystore source not found on classpath)"));
    }
    try {
      final KeyStore keystore = KeyStore.getInstance("JKS");
      keystore.load(source, password);
      return keystore;
    } catch (GeneralSecurityException | IOException e) {
      throw new InitializationFailure(e);
    }
  }

  public static X509TrustManager createDefaultTrustManager() {
    return createTrustManagerFrom(null);  // will load JVM default keystore
  }

  public static X509TrustManager createTrustManagerFrom(final KeyStore trustStore) {
    final TrustManagerFactory factory;
    try {
      factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      factory.init(trustStore);
    } catch (NoSuchAlgorithmException | KeyStoreException e) {
      throw new InitializationFailure(e);
    }
    for (TrustManager each : factory.getTrustManagers()) {
      if (each instanceof X509TrustManager) {
        return (X509TrustManager) each;
      }
    }
    throw new InitializationFailure("no X509TrustManager found");
  }

  public static final class InitializationFailure extends IllegalStateException {
    public InitializationFailure(final Throwable cause) {
      super(cause);
    }
    public InitializationFailure(final String message) {
      super(message);
    }
  }
}
