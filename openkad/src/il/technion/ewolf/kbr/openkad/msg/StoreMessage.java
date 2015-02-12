package il.technion.ewolf.kbr.openkad.msg;

import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.Node;

import java.util.List;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * A store results message to be inserted to the destination node's cache
 * @author eyal.kibbar@gmail.com
 *
 */
public class StoreMessage extends KadMessage {

	private static final long serialVersionUID = 3908967205635902724L;
	
	private Key key;
	private List<Node> nodes;
	
	
	@Inject
	StoreMessage(
			@Named("openkad.rnd.id") long id,
			@Named("openkad.local.node") Node src) {
		super(id, src);
	}

	public List<Node> getNodes() {
		return nodes;
	}
	
	public Key getKey() {
		return key;
	}
	
	public StoreMessage setKey(Key key) {
		this.key = key;
		return this;
	}
	
	public StoreMessage setNodes(List<Node> nodes) {
		this.nodes = nodes;
		return this;
	}

}
