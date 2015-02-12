package il.technion.ewolf.kbr.openkad.op;

import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.Node;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all the find value operations
 * @author eyal.kibbar@gmail.com
 *
 */
public abstract class FindValueOperation {

	protected Key key;
	
	private Collection<Node> bootstrap;
	
	protected FindValueOperation() {
		bootstrap = Collections.emptySet();
	}
	
	public FindValueOperation setKey(Key key) {
		this.key = key;
		return this;
	}
	
	public FindValueOperation setBootstrap(Collection<Node> bootstrap) {
		this.bootstrap = bootstrap;
		return this;
	}
	
	protected Collection<Node> getBootstrap() {
		return bootstrap;
	}
	
	public abstract int getNrQueried();
	
	public abstract List<Node> doFindValue();
	
}
