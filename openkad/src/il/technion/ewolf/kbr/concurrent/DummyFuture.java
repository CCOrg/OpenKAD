package il.technion.ewolf.kbr.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A future initiated with the result
 * This class is used to mock future when we already have the results
 * but need to return a future. One example is result found in cache.
 * 
 * @author eyal.kibbar@gmail.com
 *
 * @param <T> any arbitrary result type
 */
public class DummyFuture<T> implements Future<T> {

	private final T result;
	private final Throwable exc;
	
	/**
	 * Initiate with a result
	 * @param result the result to be returned when <code>get</code> is invoked 
	 */
	public DummyFuture(T result) {
		this.result = result;
		this.exc = null;
	}
	
	/**
	 * Initiate with exception
	 * @param exc the exception to be thrown when <code>get</code> is invoked (wrapped
	 * in <code>ExecutionException</code>
	 */
	public DummyFuture(Throwable exc) {
		this.result = null;
		this.exc = exc;
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return true;
	}

	@Override
	public T get() throws InterruptedException, ExecutionException {
		if (exc != null)
			throw new ExecutionException(exc);
		return result;
	}

	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		if (exc != null)
			throw new ExecutionException(exc);
		return result;
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public boolean isDone() {
		return true;
	}

}
