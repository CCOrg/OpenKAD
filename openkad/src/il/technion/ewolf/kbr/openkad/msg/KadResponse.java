package il.technion.ewolf.kbr.openkad.msg;

import il.technion.ewolf.kbr.Node;

import com.google.inject.name.Named;

/**
 * Base class for all responses
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public abstract class KadResponse extends KadMessage {

	private static final long serialVersionUID = 5247239397467830857L;

	protected KadResponse(long id, @Named("openkad.local.node") Node src) {
		super(id, src);
	}

}
