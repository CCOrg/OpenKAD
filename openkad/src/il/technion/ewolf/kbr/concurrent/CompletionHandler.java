package il.technion.ewolf.kbr.concurrent;


/**
 * A generic async task callback
 * 
 * @author eyal.kibbar@gmail.com
 *
 * @param <R> the task's result
 * @param <A> an arbitrary attachment
 */
public interface CompletionHandler<R, A> {
	
	/**
	 * Task was completed successfully
	 * @param result the result
	 * @param attachment any given attachment
	 */
	void completed(R result, A attachment);
	
	/**
	 * Task failed
	 * @param exc failure reason
	 * @param attachment any given attachment
	 */
	void failed(Throwable exc, A attachment);

}
