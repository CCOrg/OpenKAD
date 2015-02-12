package il.technion.ewolf.kbr.openkad.cache;

import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.Node;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class StoppableCache implements KadCache {

	private final AtomicBoolean isChangeable = new AtomicBoolean(true);
	
	private final KadCache realCache;
	
	@Inject
	StoppableCache(@Named("openkad.cache.stoppable.cache") KadCache realCache) {
		this.realCache = realCache;
	}
	
	public void stopUpdating() {
		isChangeable.set(false);
	}
	
	@Override
	public synchronized void insert(Key key, List<Node> nodes) {
		if (isChangeable.get())
			realCache.insert(key, nodes);
	}

	@Override
	public List<Node> search(Key key) {
		return realCache.search(key);
	}

	@Override
	public void clear() {
		realCache.clear();
	}
	
}
