package at.ac.univie.isc.asio.container.nest;

import at.ac.univie.isc.asio.tool.TimeoutSpec;
import com.google.common.base.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Set the container timeout to a default value if it is missing.
 */
@Component
final class DefaultTimeout implements Configurer {
  private final TimeoutSpec fallback;

  @Autowired
  public DefaultTimeout(final TimeoutSpec fallback) {
    requireNonNull(fallback, "illegal default timeout");
    this.fallback = fallback;
  }

  @Nonnull
  @Override
  public NestConfig apply(final NestConfig input) {
    final Dataset dataset = input.getDataset();
    dataset.setTimeout(
        Objects.firstNonNull(dataset.getTimeout(), fallback).orIfUndefined(fallback)
    );
    return input;
  }

  @Override
  public String toString() {
    return "DefaultTimeout{" +
        "fallback=" + fallback +
        '}';
  }
}