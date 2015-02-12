package il.technion.ewolf.kbr.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Convert a CompletionHandler into a future.
 * the get method simply blocks until the callback was called
 * 
 * @author eyal.kibbar@gmail.com
 *
 * @param <T> the result type
 * @param <A> any arbitrary attachment
 */
public class FutureCallback<T, A> implements Future<T>, CompletionHandler<T, A> {

	private T result = null;
	private Throwable exc = null;
	private boolean isDone = false;
	private boolean isCancelled = false;
	
	@Override
	public synchronized void completed(T result, A attachment) {
		if (isCancelled() || isDone())
			return;
		this.result = result;
		isDone = true;
		notifyAll();
	}

	
	@Override
	public synchronized void failed(Throwable exc, A attachment) {
		if (isCancelled() || isDone())
			return;
		this.exc = exc;
		isDone = true;
		notifyAll();
	}

	@Override
	public synchronized boolean cancel(boolean mayInterruptIfRunning) {
		isCancelled = true;
		return true;
	}

	@Override
	public synchronized T get() throws InterruptedException, ExecutionException {
		try {
			return get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			throw new AssertionError();
		}
	}
	
	@Override
	public synchronized T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		if (isCancelled())
			throw new CancellationException();
		
		if (isDone()) {
			if (exc != null)
				throw new ExecutionException(exc);
			return result;
		}
		
		wait(unit.toMillis(timeout));
		
		if (isDone()) {
			if (exc != null)
				throw new ExecutionException(exc);
			return result;
		} else {
			throw new TimeoutException();
		}
	}

	@Override
	public synchronized boolean isCancelled() {
		return isCancelled;
	}

	@Override
	public synchronized boolean isDone() {
		if (isCancelled())
			return false;
		return isDone;
	}

}
