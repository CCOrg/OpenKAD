package il.technion.ewolf.kbr.openkad.handlers;

import il.technion.ewolf.kbr.openkad.cache.KadCache;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.msg.StoreMessage;
import il.technion.ewolf.kbr.openkad.net.MessageDispatcher;
import il.technion.ewolf.kbr.openkad.net.filter.MessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.TypeMessageFilter;

import java.util.Arrays;
import java.util.Collection;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Handle store messages by inserting the content to the cache
 * @author eyal.kibbar@gmail.com
 *
 */
public class StoreHandler extends AbstractHandler {

	private final KadCache cache;
	
	@Inject
	StoreHandler(
			Provider<MessageDispatcher<Void>> msgDispatcherProvider,
			KadCache cache) {
		super(msgDispatcherProvider);
		this.cache = cache;
	}

	@Override
	public void completed(KadMessage msg, Void attachment) {
		StoreMessage storeMsg = (StoreMessage)msg;
		cache.insert(storeMsg.getKey(), storeMsg.getNodes());
	}

	@Override
	public void failed(Throwable exc, Void attachment) {
		// should never b here
	}

	@Override
	protected Collection<MessageFilter> getFilters() {
		return Arrays.asList(new MessageFilter[] {
				new TypeMessageFilter(StoreMessage.class)
		});
	}

}
