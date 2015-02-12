package il.technion.ewolf.kbr.openkad.net.filter;

import il.technion.ewolf.kbr.openkad.msg.KadMessage;

/**
 * Interface for all message filters
 * @author eyal.kibbar@gmail.com
 *
 */
public interface MessageFilter {

	boolean shouldHandle(KadMessage m);
	
}
