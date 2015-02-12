package il.technion.ewolf.kbr.openkad.cache;

import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.Node;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Caches nodes according to the FIFO policy
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public class FifoKadCache implements KadCache {

	// dependencies
	private final int size;
	
	// state
	private final List<CacheEntry> cache;
	private final Map<Key, CacheEntry> entryFromKey;
	
	protected class CacheEntry {
		private final List<Node> nodes;
		private final Key key;
		
		CacheEntry(List<Node> nodes, Key key) {
			this.nodes = nodes;
			this.key = key;
		}
		
		public List<Node> getNodes() {
			return nodes;
		}
		
		public Key getKey() {
			return key;
		}
	}
	
	@Inject
	FifoKadCache(
			@Named("openkad.cache.size") int size) {
		
		this.size = size;
		this.cache = new LinkedList<CacheEntry>();
		this.entryFromKey = new HashMap<Key, CacheEntry>();
	}
	
	@Override
	public synchronized void insert(Key key, List<Node> nodes) {
		CacheEntry cacheEntry = entryFromKey.get(key);
		if (cacheEntry != null) {
			cache.remove(cacheEntry);
			
			insertEntry(new CacheEntry(nodes, key)); 
			
			
		} else { // not in cache

			// make room for entry
			if (cache.size() == size) {
				CacheEntry e = cache.remove(0);
				entryFromKey.remove(e.getKey());
			}
			insertEntry(new CacheEntry(nodes, key));
		}
	}

	private void insertEntry(CacheEntry e) {
		cache.add(e);
		entryFromKey.put(e.getKey(), e);
	}
	
	@Override
	public synchronized List<Node> search(Key key) {
		CacheEntry cacheEntry = entryFromKey.get(key);
		return cacheEntry == null ? null : cacheEntry.getNodes();
	}

	@Override
	public synchronized void clear() {
		entryFromKey.clear();
		cache.clear();
	}

}
