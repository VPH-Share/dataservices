package at.ac.univie.isc.asio.tool;

import com.google.common.io.Resources;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Create layered {@link java.util.Properties} with default values defined at (in order of precedence):
 *  - .properties file on the classpath
 *  - system properties
 *  - external .properties file
 *  - command line
 */
public final class PropertyHierarchy {
  private static final Logger log = getLogger(PropertyHierarchy.class);

  /** key of the property pointing to an external .properties file */
  public static final String EXTERNAL_LOCATION_PROPERTY = "config_location";

  private final Properties merged;

  public PropertyHierarchy(final String classpathLocation) {
    this.merged = createHierarchy(classpathLocation);
  }

  /** the merged property hierarchiy */
  public Properties get() {
    return merged;
  }

  /** add all non-positional arguments as property, expects --key=value or --toggle format */
  public PropertyHierarchy parseCommandLine(final String[] arguments) {
    log.debug("parsing cli arguments {}", (Object) arguments);
    for (final String arg : arguments) {
      if (arg.startsWith("--")) {
        final String option = arg.substring(2);
        if (!option.isEmpty()) {
          include(option);
        }
      }
    }
    return this;
  }

  private void include(final String arg) {
    final String[] parts = arg.split("=", 2);
    if (parts.length == 1) {  // toggle
      log.debug("found cli toggle {}", parts[0]);
      merged.setProperty(parts[0], "true");
    } else {  // key-value argument
      log.debug("found cli option {} with value {}", parts[0], parts[1]);
      merged.setProperty(parts[0], parts[1]);
    }
  }

  private Properties createHierarchy(final String classpathLocation) {
    Properties hierarchy;
    try {
      hierarchy = loadFromClasspath(classpathLocation);
      hierarchy = loadFromSystem(hierarchy);
      final String externalLocation = hierarchy.getProperty(EXTERNAL_LOCATION_PROPERTY);
      if (externalLocation != null) {
        hierarchy = loadExternal(externalLocation, hierarchy);
      }
    } catch (IOException e) {
      throw new IllegalStateException("failed to load property hierarchy", e);
    }
    return hierarchy;
  }

  private Properties loadFromClasspath(final String location) throws IOException {
    log.debug("loading properties from {}", location);
    final Properties props = new Properties();
    final URL resource = Resources.getResource(location);
    try (final InputStream stream = resource.openStream()) {
      props.load(stream);
    }
    return props;
  }

  private Properties loadFromSystem(final Properties fallback) {
    log.debug("loading system properties");
    final Properties props = new Properties(fallback);
    props.putAll(System.getenv());
    props.putAll(System.getProperties());
    return props;
  }

  private Properties loadExternal(final String path, final Properties fallback) throws IOException {
    log.debug("loading external properties from {}", path);
    final Properties props = new Properties(fallback);
    try (final InputStream stream = Files.newInputStream(Paths.get(path))) {
      props.load(stream);
    }
    return props;
  }
}