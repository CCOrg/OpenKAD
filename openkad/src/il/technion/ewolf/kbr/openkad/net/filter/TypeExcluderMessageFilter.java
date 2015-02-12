package il.technion.ewolf.kbr.openkad.net.filter;

import il.technion.ewolf.kbr.openkad.msg.KadMessage;

/**
 * Rejects all messages in the given class
 * @author eyal.kibbar@gmail.com
 *
 */
public class TypeExcluderMessageFilter implements MessageFilter {

	private final Class<? extends KadMessage> clazz;
	
	public TypeExcluderMessageFilter(Class<? extends KadMessage> clazz) {
		this.clazz = clazz;
	}
	@Override
	public boolean shouldHandle(KadMessage m) {
		return !m.getClass().equals(clazz);
	}

}
