package il.technion.ewolf.kbr.openkad.msg;

import il.technion.ewolf.kbr.Node;

import java.util.List;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * A forward message as defined in the colors protocol
 * TODO: add a link to the published article
 * @author eyal.kibbar@gmail.com
 *
 */
public class ForwardMessage extends KadMessage {

	private static final long serialVersionUID = 101861722605010003L;
	
	private List<Node> nodes;
	private boolean ack = false;
	private boolean nack = false;
	private int pathLength = 0;
	private int findNodeHops = 0;
	
	@Inject
	ForwardMessage(
			@Named("openkad.rnd.id") long id,
			@Named("openkad.local.node") Node src) {
		super(id, src);
	}


	public List<Node> getNodes() {
		return nodes;
	}
	
	public ForwardMessage setNodes(List<Node> nodes) {
		this.nodes = nodes;
		return this;
	}
	
	
	public ForwardMessage setAck() {
		if (isNack())
			throw new IllegalStateException("cannot be both ack and nack");
		this.ack = true;
		return this;
	}
	
	public ForwardMessage setNack() {
		if (isAck())
			throw new IllegalStateException("cannot be both ack and nack");
		this.ack = false;
		return this;
	}
	
	
	public boolean isAck() {
		return ack;
	}
	
	public boolean isNack() {
		return nack;
	}
	
	public int getPathLength() {
		return pathLength;
	}
	
	public ForwardMessage setPathLength(int pathLength) {
		this.pathLength = pathLength;
		return this;
	}
	
	public int getFindNodeHops() {
		return findNodeHops;
	}
	
	public ForwardMessage setFindNodeHops(int findNodeHops) {
		this.findNodeHops = findNodeHops;
		return this;
	}

}
