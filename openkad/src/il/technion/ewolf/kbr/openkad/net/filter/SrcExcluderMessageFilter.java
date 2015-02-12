package il.technion.ewolf.kbr.openkad.net.filter;

import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;

/**
 * Rejects all messages from src other than the given src
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public class SrcExcluderMessageFilter implements MessageFilter {

	private final Node src;
	
	public SrcExcluderMessageFilter(Node src) {
		this.src = src;
	}
	
	
	
	@Override
	public boolean shouldHandle(KadMessage m) {
		return !src.equals(m.getSrc());
	}
	
}
