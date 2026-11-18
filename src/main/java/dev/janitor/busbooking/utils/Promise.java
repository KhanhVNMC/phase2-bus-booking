package dev.janitor.busbooking.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import lombok.Getter;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 *
 * A JS Promise-style utility for handling asynchronous results.
 *
 * @param <T> the type of the promised result
 */
public class Promise<T> {
	private final ReentrantLock lock = new ReentrantLock();
		
	private final List<Consumer<T>> successHandlers = new ArrayList<>();
	private final List<Consumer<Exception>> errorHandlers = new ArrayList<>();
	
	@Getter private boolean completed = false;
	private boolean failed = false;

	/** The successful result value, if completed successfully. */
	private T value;
	/** The exception, if completed with failure. */
	private Exception error;

	public static <T> Promise<T> resolved(T value) {
		Promise<T> promise = new Promise<>();
		promise.fulfill(value);
		return promise;
	}

	public static <T> Promise<T> rejected(Exception error) {
		Promise<T> promise = new Promise<>();
		promise.reject(error);
		return promise;
	}
	
	/**
	 * Registers a success handler.
	 *
	 * @param handler the handler to run with the resolved value
	 * @return this promise for chaining
	 */
	public Promise<T> then(Consumer<T> handler) {
		lock.lock();
		try {
			if (!completed) {
				successHandlers.add(wrap(handler));
			} else if (!failed) {
				runHandler(handler, value);
			}
		} finally {
			lock.unlock();
		}
		return this;
	}

	/**
	 * Transforms the resolved value into another promised value.
	 *
	 * @param mapper the value transformation
	 * @param <R> the transformed value type
	 * @return a promise resolved with the transformed value
	 */
	public <R> Promise<R> map(Function<T, R> mapper) {
		Promise<R> next = new Promise<>();
		then(value -> {
			try {
				next.fulfill(mapper.apply(value));
			} catch (Throwable error) {
				next.reject(asException(error));
			}
		});
		Catch(next::reject);
		return next;
	}

	/**
	 * Transforms a rejection while preserving a successful value.
	 *
	 * @param mapper the error transformation
	 * @return a promise with the transformed rejection
	 */
	public Promise<T> mapError(Function<Exception, Exception> mapper) {
		Promise<T> next = new Promise<>();
		then(next::fulfill);
		Catch(error -> {
			try {
				next.reject(mapper.apply(error));
			} catch (Throwable mappedError) {
				next.reject(asException(mappedError));
			}
		});
		return next;
	}

	/**
	 * Registers an error handler to be executed if the promise is rejected.
	 *
	 * @param handler the handler to run with the thrown exception
	 * @return this promise for chaining
	 */
	public Promise<T> Catch(Consumer<Exception> handler) {
		lock.lock();
		try {
			if (!completed) {
				errorHandlers.add(handler);
			} else if (failed) {
				handler.accept(error);
			}
		} finally {
			lock.unlock();
		}
		return this;
	}

	/**
	 * Fulfills this promise with a successful result.
	 * <p>
	 * All registered success handlers will be executed. Further attempts to
	 * complete this promise will result in an {@link IllegalStateException}.
	 *
	 * @param result the resolved value
	 * @throws IllegalStateException if the promise was already completed
	 */
	public void fulfill(T result) {
		lock.lock();
		try {
			if (completed) {
				throw new IllegalStateException("[Promise] Promise already completed!");
			}

			completed = true;
			value = result;

			for (Consumer<T> handler : successHandlers) {
				safeRun(() -> handler.accept(result));
			}

			successHandlers.clear();
			errorHandlers.clear();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Rejects this promise with an exception.
	 * <p>
	 * All registered error handlers will be executed. Further attempts to complete
	 * this promise will result in an {@link IllegalStateException}.
	 *
	 * @param exception the cause of the failure
	 * @throws IllegalStateException if the promise was already completed
	 */
	public void reject(Exception exception) {
		Objects.requireNonNull(exception, "exception");
		lock.lock();
		try {
			if (completed) {
				throw new IllegalStateException("[Promise] Promise already completed!");
			}
			this.completed = true;
			this.failed = true;
			this.error = exception;

			for (Consumer<Exception> handler : errorHandlers) {
				safeRun(() -> handler.accept(exception));
			}
			
			successHandlers.clear();
			errorHandlers.clear();
		} finally {
			lock.unlock();
		}
	}
	
	// literature 101; this wont break your heart (probably)
	public void politelyReject(Exception exception) {
		lock.lock();
		try {
			if (completed) return;
			reject(exception);
		} finally {
			lock.unlock();
		}
	}
	
	/**
	 * Blocks the current thread until the promise is completed.
	 *
	 * <p>
	 * If the promise is fulfilled, the value is returned. If it is rejected, a
	 * {@link RuntimeException} is thrown wrapping the original cause.
	 * </p>
	 *
	 * @return the resolved value
	 * @throws RuntimeException if the promise is rejected or interrupted while
	 *                          waiting
	 */
	public T await() {
		CompletableFuture<T> future = new CompletableFuture<>();

		then(future::complete);
		Catch(future::completeExceptionally);

		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("[Promise] Await interrupted", e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new RuntimeException("[Promise] Await failed: " + cause.getMessage(), cause);
		}
	}

	private static Exception asException(Throwable error) {
		return error instanceof Exception exception ? exception : new RuntimeException(error);
	}

	private Consumer<T> wrap(Consumer<T> handler) {
		return value -> runHandler(handler, value);
	}

	private void runHandler(Consumer<T> handler, T value) {
		safeRun(() -> handler.accept(value));
	}
	
	private void safeRun(Runnable r) {
		try {
			r.run();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
