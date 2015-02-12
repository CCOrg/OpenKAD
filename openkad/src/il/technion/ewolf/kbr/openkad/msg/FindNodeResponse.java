package il.technion.ewolf.kbr.openkad.msg;

import il.technion.ewolf.kbr.Node;

import java.util.List;

import com.google.inject.name.Named;

/**
 * A findNode response as defined in the kademlia protocol
 * @author eyal.kibbar@gmail.com
 * we extend the find node response in order for nodes to be able to indicate if they are interested in the value for their 
 * cache.
 */
public class FindNodeResponse extends KadResponse {

	private static final long serialVersionUID = 2103126060969733458L;
	private List<Node> nodes;
	private boolean cachedResults;
	// not in openKad - for vision.
	private boolean needed;
	
	
	
	protected FindNodeResponse(long id, @Named("openkad.local.node") Node src) {
		super(id, src);
	}
	
	public FindNodeResponse setNodes(List<Node> nodes) {
		this.nodes = nodes;
		return this;
	}
	
	/* (non-Javadoc)
	 * @see il.technion.ewolf.kbr.openkad.msg.FindNodeResponse#getNodes()
	 */
	public List<Node> getNodes() {
		return nodes;
	}
	
	public FindNodeResponse setCachedResults(boolean cachedResults) {
		this.cachedResults = cachedResults;
		return this;
	}
	
	/* (non-Javadoc)
	 * @see il.technion.ewolf.kbr.openkad.msg.FindNodeResponse#isCachedResults()
	 */
	public boolean isCachedResults() {
		return cachedResults;
	}

	/* (non-Javadoc)
	 * @see il.technion.ewolf.kbr.openkad.msg.FindNodeResponse#isNeeeded()
	 */
	public boolean isNeeeded() {
		return needed;
	}

	/* (non-Javadoc)
	 * @see il.technion.ewolf.kbr.openkad.msg.FindNodeResponse#setNeeeded(boolean)
	 */
	public void setNeeeded(boolean neeeded) {
		this.needed = neeeded;
	}

}
