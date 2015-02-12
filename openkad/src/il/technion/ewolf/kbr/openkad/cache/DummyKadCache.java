package il.technion.ewolf.kbr.openkad.cache;

import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.Node;

import java.util.List;

/**
 * A default stupid implementation of KadCache that stores nothing
 * @author eyal.kibbar@gmail.com
 *
 */
public class DummyKadCache implements KadCache {

	@Override
	public void insert(Key key, List<Node> nodes) {
	}

	@Override
	public List<Node> search(Key key) {
		return null;
	}

	@Override
	public void clear() {
	}

}
