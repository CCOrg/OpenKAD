package il.technion.ewolf.kbr.openkad.msg;

import il.technion.ewolf.kbr.Node;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Base class for all requests.
 * A request is defined by 2 features:
 * 1. A response should be received for it
 * 2. Only a limited number of outstanding messages (requests which their corresponding
 *    response hasn't been received yet) is allowed.
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public abstract class KadRequest extends KadMessage {

	private static final long serialVersionUID = 7014729033211615669L;

	@Inject
	protected
	KadRequest(long id, @Named("openkad.local.node") Node src) {
		super(id, src);
	}

	public abstract KadResponse generateResponse(Node localNode);
	
}
