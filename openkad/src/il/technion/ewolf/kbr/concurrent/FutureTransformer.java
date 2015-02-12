package il.technion.ewolf.kbr.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Transforms one future result to another
 * 
 * @author eyal.kibbar@gmail.com
 *
 * @param <T> The original result type
 * @param <R> The final result type
 */
public abstract class FutureTransformer<T, R> implements Future<R> {

	private final Future<T> f;
	private R result = null;
	private boolean resultValid = false;
	
	public FutureTransformer(Future<T> f) {
		this.f = f;
	}
	
	/**
	 * Called to transform result of type T into the new result type (R).
	 * This method will be called at most once. After the first call, the
	 * result will be cached.
	 * 
	 * @param t original result
	 * @return transformed result
	 * @throws Throwable
	 */
	protected abstract R transform(T t) throws Throwable;
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return f.cancel(mayInterruptIfRunning);
	}

	@Override
	public R get() throws InterruptedException, ExecutionException {
		try {
			return get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
		} catch (Throwable e) {
			throw new ExecutionException(e);
		}
	}

	@Override
	public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		try {
			synchronized(this) {
				if (resultValid == false)
					result = transform(f.get(timeout, unit));
				
				resultValid = true;
				return result;
			}
		} catch (Throwable e) {
			throw new ExecutionException(e);
		}
	}

	@Override
	public boolean isCancelled() {
		return f.isCancelled();
	}

	@Override
	public boolean isDone() {
		return f.isDone();
	}

}
