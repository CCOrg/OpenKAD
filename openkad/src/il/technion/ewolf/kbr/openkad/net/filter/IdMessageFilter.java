package il.technion.ewolf.kbr.openkad.net.filter;

import il.technion.ewolf.kbr.openkad.msg.KadMessage;

/**
 * Rejects all messages with id different from the given ID
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public class IdMessageFilter implements MessageFilter {
	
	private final long id;
	
	public IdMessageFilter(long id) {
		this.id = id;
	}

	@Override
	public boolean shouldHandle(KadMessage m) {
		return m.getId() == id;
	}
}
