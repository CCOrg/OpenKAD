package il.technion.ewolf.kbr.openkad.cache;

import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.Node;

import java.util.List;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Same as LRU cache but only inserts keys with the local node's color
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public class ColorLRUKadCache extends LRUKadCache {

	private int nrColors;
	private int myColor;

	@Inject
	ColorLRUKadCache(
			@Named("openkad.cache.size") int size,
			@Named("openkad.bucket.kbuckets.maxsize") int kBucketSize,
			@Named("openkad.color.nrcolors") int nrColors,
			@Named("openkad.local.color") int myColor) {
		super(size, kBucketSize);
		this.nrColors = nrColors;
		this.myColor = myColor;
	}

	
	@Override
	public void insert(Key key, List<Node> nodes) {
		if (isFull() && key.getColor(nrColors) != myColor)
			return;
		super.insert(key, nodes);
	}
}
