package il.technion.ewolf.kbr.openkad.msg;

import il.technion.ewolf.kbr.Node;

import java.io.Serializable;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Base class for all openkad messages.
 * All messages must be in this package
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public abstract class KadMessage implements Serializable {

	private static final long serialVersionUID = -6975403100655787398L;
	private final long id;
	private final Node src;
	@Inject
	KadMessage(long id, @Named("openkad.local.node") Node src) {
		this.id = id;
		this.src = src;
	}
	
	
	public Node getSrc() {
		return src;
	}
	
	public long getId() {
		return id;
	}
	
}
