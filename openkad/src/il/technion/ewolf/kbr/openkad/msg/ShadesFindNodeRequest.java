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
public class ShadesFindNodeRequest extends KadRequest {

	private static final long serialVersionUID = -2437918697668927600L;
	private Key key;
	private boolean searchCache;
	private int requiredColor;
	private boolean isOnlyClosestToKey;
	
	@Inject
	ShadesFindNodeRequest(
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
	
	public ShadesFindNodeRequest setKey(Key key) {
		this.key = key;
		return this;
	}

	@Override
	public ShadesFindNodeResponse generateResponse(@Named("openkad.local.node") Node localNode) {
		return new ShadesFindNodeResponse(getId(), localNode);
	}

	
	public ShadesFindNodeRequest setSearchCache(boolean searchCache) {
		this.searchCache = searchCache;
		return this;
	}
	
	public boolean shouldSearchCache() {
		return searchCache;
	}
	
	public ShadesFindNodeRequest setRequiredColor(int requiredColor) {
		this.requiredColor = requiredColor;
		return this;
	}
	
	public int getRequiredColor() {
		return requiredColor;
	}
	
	public boolean getOnlyClosestToKey() {
		return isOnlyClosestToKey;
	}
	
	public ShadesFindNodeRequest setOnlyClosestToKey(boolean isOnlyClosestToKey) {
		this.isOnlyClosestToKey = isOnlyClosestToKey;
		return this;
	}
	
	
}
