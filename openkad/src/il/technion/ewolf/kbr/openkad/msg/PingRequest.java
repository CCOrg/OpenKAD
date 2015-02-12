package il.technion.ewolf.kbr.openkad.msg;

import il.technion.ewolf.kbr.Node;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * A ping request as defined in the kademlia protocol 
 * @author eyal.kibbar@gmail.com
 *
 */
public class PingRequest extends KadRequest {

	private static final long serialVersionUID = 4646089493549742900L;


	@Inject
	PingRequest(
			@Named("openkad.rnd.id") long id,
			@Named("openkad.local.node") Node src) {
		super(id, src);
	}

	
	@Override
	public PingResponse generateResponse(@Named("openkad.local.node") Node localNode) {
		return new PingResponse(getId(), localNode);
	}
	
}
