/*
 * #%L
 * asio common
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
package at.ac.univie.isc.asio.tool;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subscriptions.Subscriptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

import static java.util.Objects.requireNonNull;

/**
 * RxJava support utilities.
 */
public final class Reactive {
  private Reactive() { /* utility class */ }

  /**
   * Create an {@link rx.functions.Action0 action}, which does nothing.
   * @return An empty {@code Action0}, that does nothing.
   */
  @Nonnull
  public static Action0 noop() {
    return EMPTY_ACTION_0;
  }

  private static final Action0 EMPTY_ACTION_0 = new Action0() {
    @Override
    public void call() { /* noop */ }
  };

  /**
   * Create an {@link rx.functions.Action1 error handler}, which ignores errors.
   * @return An empty {@code Action1<Throwable>}, that does nothing.
   */
  @Nonnull
  public static Action1<Throwable> ignoreErrors() {
    return EMPTY_ACTION_1;
  }

  private static final Action1<Throwable> EMPTY_ACTION_1 = new Action1<Throwable>() {
    @Override
    public void call(final Throwable o) {}
  };

  /**
   * Synchronously consume the given {@code Observable} and convert the single contained element
   * into an {@code Optional}.
   * If the {@code source} is empty, {@link Optional#absent()} is returned.
   *
   * @param source a reactive sequence
   * @param <ELEMENT> type of source contents
   * @return optional single element of the source
   * @throws IllegalArgumentException if the source has more than one element
   */
  public static <ELEMENT> Optional<ELEMENT> asOptional(final Observable<ELEMENT> source) {
    return source.map(new ConvertToOptional<ELEMENT>()).toBlocking().singleOrDefault(Optional.<ELEMENT>absent());
  }

  private static class ConvertToOptional<ELEMENT> implements Func1<ELEMENT, Optional<ELEMENT>> {
    @Override
    public Optional<ELEMENT> call(final ELEMENT element) {
      return Optional.fromNullable(element);
    }
  }

  /**
   * Create a function, which replaces empty or null input collections with given default collection.
   * @param replacement default to be returned on empty or null input
   * @param <CONTAINER> container type
   * @param <ELEMENTS> type of container contents
   * @return replacement function
   */
  @Nonnull
  public static <CONTAINER extends Collection<ELEMENTS>, ELEMENTS> Func1<CONTAINER, CONTAINER> replaceEmptyWith(final CONTAINER replacement) {
    return new ReplaceEmpty<>(replacement);
  }

  private static final class ReplaceEmpty<CONTAINER extends Collection<ELEMENTS>, ELEMENTS> implements Func1<CONTAINER, CONTAINER> {
    private final CONTAINER fallback;

    private ReplaceEmpty(final CONTAINER fallback) {
      this.fallback = requireNonNull(fallback);
    }

    @Nonnull
    @Override
    public CONTAINER call(@Nullable final CONTAINER given) {
      if (given == null || given.isEmpty()) {
        return fallback;
      } else {
        return given;
      }
    }
  }

  /**
   * Convert a {@code ListenableFuture} into an {@code Observable}.
   * The observable will emit a single item, the result of the future. Subscribing does not block.
   * The subscriber <strong>may</strong> be invoked synchronously on the subscribing thread or
   * asynchronously from the thread, which completes the future. Unsubscribing will
   * {@link com.google.common.util.concurrent.ListenableFuture#cancel(boolean) cancel} the future.
   *
   * @see com.google.common.util.concurrent.ListenableFuture
   * @see rx.internal.operators.OnSubscribeToObservableFuture
   *
   * @param future task which should be observed
   * @param <T> return type of the future
   * @return subscription function
   */
  @Nonnull
  public static <T> ToObservableListenableFuture<T> listeningFor(@Nonnull final ListenableFuture<T> future) {
    return new ToObservableListenableFuture<>(future);
  }

  @VisibleForTesting
  static class ToObservableListenableFuture<T> implements Observable.OnSubscribe<T> {
    private final ListenableFuture<T> future;

    private ToObservableListenableFuture(final ListenableFuture<T> future) {
      this.future = requireNonNull(future);
    }

    @Override
    public void call(final Subscriber<? super T> subscriber) {
      subscriber.add(Subscriptions.create(new Action0() {
        @Override
        public void call() {
          future.cancel(true);
        }
      }));
      final FutureCallback<T> callback = new FutureCallback<T>() {
        @Override
        public void onSuccess(@Nullable final T result) {
          if (subscriber.isUnsubscribed()) { return; }
          subscriber.onNext(result);
          subscriber.onCompleted();
        }

        @Override
        public void onFailure(@Nonnull final Throwable t) {
          if (subscriber.isUnsubscribed()) { return; }
          Throwable cause = t;
          if (t instanceof ExecutionException) {
            cause = (t.getCause() != null) ? t.getCause() : t;
          }
          subscriber.onError(cause);
        }
      };
      if (!subscriber.isUnsubscribed()) {
        Futures.addCallback(future, callback, MoreExecutors.sameThreadExecutor());
      }
    }
  }
}
