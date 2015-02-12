package il.technion.ewolf.kbr.openkad.msg;

import il.technion.ewolf.kbr.Node;

import java.util.List;

import com.google.inject.name.Named;

/**
 * A findNode response as defined in the kademlia protocol in shades, our response should contain
 * a list of correctly shaded nodes.
 * @author Gil and Yoav
 * we extend the find node response in order for nodes to be able to indicate if they are interested in the value for their 
 * cache.
 */
public class ShadesFindNodeResponse extends KadResponse {

	private static final long serialVersionUID = 2103126060969733458L;
	private List<Node> nodes;
	private List<Node> colorNodes;
	private boolean cachedResults;
	private boolean isNeeded;
	private boolean isPopular;

	protected ShadesFindNodeResponse(long id, @Named("openkad.local.node") Node src) {
		super(id, src);
	}

	public ShadesFindNodeResponse setNodes(List<Node> nodes) {
		this.nodes = nodes;
		return this;
	}

	public ShadesFindNodeResponse setColorNodes(List<Node> colorNodes) {
		this.colorNodes = colorNodes;
		return this;
	}

	/**
	 * 
	 * @return the nodes closest to the request key
	 */
	public List<Node> getNodes() {
		return nodes;
	}

	/**
	 * 
	 * @return the nodes in the color of the request key
	 */
	public List<Node> getColorNodes() {
		return colorNodes;
	}

	public ShadesFindNodeResponse setCachedResults(boolean cachedResults) {
		this.cachedResults = cachedResults;
		return this;
	}

	public boolean isCachedResults() {
		return cachedResults;
	}

	public boolean isNeeded() {
		return isNeeded;
	}

	public ShadesFindNodeResponse setNeeded(boolean isNeeded) {
		this.isNeeded = isNeeded; 
		return this;
	}

	public ShadesFindNodeResponse setIsPopular(boolean isPopular) {
		this.isPopular = isPopular;
		return this;
	}

	public boolean isPopular() {
		return isPopular;
	}

}
