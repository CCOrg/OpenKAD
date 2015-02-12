package il.technion.ewolf.kbr.openkad.handlers;

import il.technion.ewolf.kbr.concurrent.CompletionHandler;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.net.MessageDispatcher;
import il.technion.ewolf.kbr.openkad.net.filter.MessageFilter;

import java.util.Collection;

import com.google.inject.Provider;

/**
 * Base class for all incoming message handlers
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public abstract class AbstractHandler implements CompletionHandler<KadMessage, Void>{

	private final Provider<MessageDispatcher<Void>> msgDispatcherProvider;
	
	protected AbstractHandler(Provider<MessageDispatcher<Void>> msgDispatcherProvider) {
		this.msgDispatcherProvider = msgDispatcherProvider;
	}
	
	/**
	 * @return all the filters associated with this handler
	 */
	protected abstract Collection<MessageFilter> getFilters();
	
	/**
	 * Register this handler for start receiving messages
	 */
	public void register() {
		MessageDispatcher<Void> dispatcher = msgDispatcherProvider.get();
		
		for (MessageFilter filter : getFilters()) {
			dispatcher.addFilter(filter);
		}
		
		dispatcher
			.setConsumable(false)
			.setCallback(null, this)
			.register();
	}
}
