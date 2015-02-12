package il.technion.ewolf.kbr.openkad.bucket;

import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.openkad.KadNode;

import java.util.Collection;

/**
 * Represents A finate container for nodes
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public interface Bucket {

	/**
	 * Adds a new node to the bucket
	 * @param n the new node
	 */
	public void insert(KadNode n);

	/**
	 * Adds all nodes in bucket to the given collection
	 * @param c the collection the nodes will be added to
	 */
	void addNodesTo(Collection<Node> c);

	/**
	 * Marks a node as dead: the dead node will be replace if 
	 * insert was invoked 
	 * @param n the dead node
	 */
	public void markDead(Node n);
}
