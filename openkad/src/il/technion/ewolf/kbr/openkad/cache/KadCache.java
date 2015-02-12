package il.technion.ewolf.kbr.openkad.cache;

import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.Node;

import java.util.List;

/**
 * Caches the results of find node operations
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public interface KadCache {

	public void insert(Key key, List<Node> nodes);
	
	public List<Node> search(Key key);
	
	public void clear();
	
}
