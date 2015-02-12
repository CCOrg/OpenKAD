package il.technion.ewolf.kbr.openkad.msg;

import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.Node;

import java.util.List;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * A forward request as defined in the colors protocol TODO: add a link to the
 * published article
 * 
 * @author eyal.kibbar@gmail.com
 * 
 */
public class ForwardRequest extends KadRequest {

	private static final long serialVersionUID = -1087198782219829035L;

	private Key key;
	private List<Node> bootstrap;

	// TODO: testing only REMOVE B4 PUBLISH
	private boolean isInitiator = false;

	@Inject
	ForwardRequest(@Named("openkad.rnd.id") final long id, @Named("openkad.local.node") final Node src) {
		super(id, src);
	}

	public List<Node> getBootstrap() {
		return this.bootstrap;
	}

	public Key getKey() {
		return this.key;
	}

	public ForwardRequest setBootstrap(final List<Node> bootstrap) {
		this.bootstrap = bootstrap;
		return this;
	}

	public ForwardRequest setKey(final Key key) {
		this.key = key;
		return this;
	}

	public ForwardMessage generateMessage(final Node localNode) {
		return new ForwardMessage(getId(), localNode);
	}

	@Override
	public ForwardResponse generateResponse(final Node localNode) {
		return new ForwardResponse(getId(), localNode);
	}

	// TODO: remove b4 publish
	public ForwardRequest setInitiator() {
		this.isInitiator = true;
		return this;
	}

	public boolean isInitiator() {
		return this.isInitiator;
	}

}
