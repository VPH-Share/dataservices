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

import at.ac.univie.isc.asio.*;
import at.ac.univie.isc.asio.tool.Closer;
import at.ac.univie.isc.asio.tool.StatefulMonitor;
import at.ac.univie.isc.asio.tool.Timeout;
import com.google.common.base.Optional;
import com.google.common.io.ByteSource;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.Ordered;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Orchestrate container creation and destruction. On application start all stored configuration
 * items are scanned for containers, that need to be deployed. On shutdown all deployed containers
 * are dropped (but not disposed).
 */
@Brood
class Warden implements SmartLifecycle {
  private static final Logger log = getLogger(Warden.class);

  /**
   * identifier of configuration files originating from this warden
   */
  static final String CONFIG_SUFFIX = "config";

  private final Catalog catalog;
  private final Assembler assembler;
  private final ConfigStore config;
  private final StatefulMonitor monitor;

  @Autowired
  Warden(final Catalog catalog, final Assembler assembler, final ConfigStore config, final Timeout timeout) {
    log.info(Scope.SYSTEM.marker(), "warden loaded, config-store={}, assembler={}", config, assembler);
    this.catalog = catalog;
    this.config = config;
    this.assembler = assembler;
    monitor = StatefulMonitor.withMaximalWaitingTime(timeout);
  }

  @Override
  public String toString() {
    return "Warden{" +
        "assembler=" + assembler +
        ", config=" + config +
        '}';
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public int getPhase() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  @Override
  public boolean isRunning() {
    return monitor.isActive();
  }

  // === internal api

  /**
   * Assemble a container from the given configuration data and deploy it with the given id.
   *
   * @param target id of the new container
   * @param source raw configuration data of the deployed container
   */
  void assembleAndDeploy(final Id target, final ByteSource source) {
    log.debug(Scope.SYSTEM.marker(), "create container <{}>", target);
    monitor.ensureActive(); // fail fast before doing a costly assembly
    final Container container = assembler.assemble(target, source);
    monitor.atomic(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        dispose(target);
        final URI location = config.save(target.asString(), CONFIG_SUFFIX, source);
        log.debug(Scope.SYSTEM.marker(), "saved configuration at <{}>", location);
        container.activate();
        log.debug(Scope.SYSTEM.marker(), "activated {} as <{}>", container, target);
        final Optional<Container> replaced = catalog.deploy(container);
        cleanUpIfNecessary(replaced);
        assert !replaced.isPresent() : "container was present on deploying of " + target;
        return null;
      }
    });
  }

  /**
   * If present undeploy and dispose the container with given name.
   *
   * @param target name of target container
   * @return true if the target container was present and has been dropped, false if not present
   */
  boolean dispose(final Id target) {
    log.debug(Scope.SYSTEM.marker(), "dispose container <{}>", target);
    return monitor.atomic(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        final Optional<Container> dropped = catalog.drop(target);
        cleanUpIfNecessary(dropped);
        config.clear(target.asString());
        return dropped.isPresent();
      }
    });
  }

  // === lifecycle implementation ==================================================================

  /**
   * Start up this Warden. Each configuration item find in the backing
   * {@link ConfigStore persistent store} is read, a container assembled from it and deployed to
   * the {@link Catalog}.
   *
   * @throws IllegalArgumentException if the warden is already running
   */
  @Override
  public void start() {
    log.info(Scope.SYSTEM.marker(), "starting", this);
    monitor.activate(new StatefulMonitor.Action() {
      @Override
      public void run() throws Exception {
        final Map<String, ByteSource> found = config.findAllWithIdentifier(CONFIG_SUFFIX);
        log.debug(Scope.SYSTEM.marker(), "found configurations of {}", found.keySet());
        for (Map.Entry<String, ByteSource> current : found.entrySet()) {
          final Id name = Id.valueOf(current.getKey());
          log.info(Scope.SYSTEM.marker(), "deploying <{}> on startup", name);
          final Optional<Container> replaced = deployQuietly(name, current.getValue());
          cleanUpIfNecessary(replaced);
        }
      }
    });
  }

  /**
   * Stop this Warden. All currently deployed containers are cleared from the {@link Catalog} and
   * closed. As long as this is stopped, no containers may be
   * {@link #assembleAndDeploy(Id, ByteSource) deployed} or {@link #dispose(Id) disposed}.
   *
   * @throws IllegalMonitorStateException if the warden is not running
   */
  @Override
  public void stop() {
    log.info(Scope.SYSTEM.marker(), "stopping");
    monitor.disable(new StatefulMonitor.Action() {
      @Override
      public void run() throws Exception {
        for (final Container container : catalog.clear()) {
          log.debug(Scope.SYSTEM.marker(), "closing {} on stop", container.name());
          Closer.quietly(container);
        }
      }
    });
  }

  /**
   * Stop the Warden asynchronously, same as calling {@link #stop()}, then run the supplied
   * callback.
   */
  @Override
  public void stop(final Runnable callback) {
    stop();
    callback.run();
  }

  /**
   * Attempt to assembled, activate and deploy a container with given name and config,
   * but suppress any errors. Return any replaced, if one was present.
   */
  private Optional<Container> deployQuietly(final Id name, final ByteSource config) {
    try {
      final Container container = assembler.assemble(name, config);
      container.activate();
      return catalog.deploy(container);
    } catch (final Exception cause) {
      log.error(Scope.SYSTEM.marker(), "quiet deployment of a container <{}> failed", name, cause);
      return Optional.absent();
    }
  }

  /** if present close the given container */
  private void cleanUpIfNecessary(final Optional<Container> dropped) {
    if (dropped.isPresent()) {
      final Container container = dropped.get();
      log.debug(Scope.SYSTEM.marker(), "cleaning up {}", container);
      Closer.quietly(container);
    }
  }
}
