package il.technion.ewolf.kbr.openkad.msg;

import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.Node;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * A findNode request as defined in the kademlia protocol
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public class FindNodeRequest extends KadRequest {

	private static final long serialVersionUID = -7084922793331210968L;
	private Key key;
	private boolean searchCache;
	
	@Inject
	FindNodeRequest(
			@Named("openkad.rnd.id") long id,
			@Named("openkad.local.node") Node src) {
		super(id, src);
	}
	/**
	 * 
	 * @return the key we are searching
	 */
	public Key getKey() {
		return key;
	}
	
	public FindNodeRequest setKey(Key key) {
		this.key = key;
		return this;
	}

	@Override
	public FindNodeResponse generateResponse(@Named("openkad.local.node") Node localNode) {
		return new FindNodeResponse(getId(), localNode);
	}

	
	public FindNodeRequest setSearchCache(boolean searchCache) {
		this.searchCache = searchCache;
		return this;
	}
	
	public boolean shouldSearchCache() {
		return searchCache;
	}
	

}
