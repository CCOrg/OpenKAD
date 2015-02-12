package il.technion.ewolf.kbr.openkad.op;

import static ch.lambdaj.Lambda.on;
import static ch.lambdaj.Lambda.sort;
import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.KeyColorComparator;
import il.technion.ewolf.kbr.KeyComparator;
import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.concurrent.CompletionHandler;
import il.technion.ewolf.kbr.openkad.bucket.KBuckets;
import il.technion.ewolf.kbr.openkad.cache.KadCache;
import il.technion.ewolf.kbr.openkad.msg.FindNodeRequest;
import il.technion.ewolf.kbr.openkad.msg.FindNodeResponse;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.msg.StoreMessage;
import il.technion.ewolf.kbr.openkad.net.Communicator;
import il.technion.ewolf.kbr.openkad.net.MessageDispatcher;
import il.technion.ewolf.kbr.openkad.net.filter.IdMessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.TypeMessageFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

/**
 * Find value operation according to the colors algorithm
 * TODO: add a link to the published article
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public class ColorFindValueOperation extends FindValueOperation implements CompletionHandler<KadMessage, Node> {

	// state
	private List<Node> knownClosestNodes;
	private final Set<Node> alreadyQueried;
	private final Set<Node> querying;
	private int nrQueried;
	private Node returnedCachedResults = null;
	private Comparator<Key> colorComparator;
	
	// dependencies
	private final Provider<FindNodeRequest> findNodeRequestProvider;
	private final Provider<MessageDispatcher<Node>> msgDispatcherProvider;
	private final Provider<StoreMessage> storeMessageProvider;
	private final KBuckets kBuckets;
	private final Node localNode;
	private final int kBucketSize;
	private final int nrCandidates;
	private final Communicator kadServer;
	private final KadCache cache;
	private final int nrShare;
	private final int nrColors;
	private final int concurrency;
	
	// testing
	private final AtomicInteger nrLocalCacheHits;
	private final AtomicInteger nrRemoteCacheHits;
	
	@Inject
	ColorFindValueOperation(
			@Named("openkad.local.node") Node localNode,
			@Named("openkad.bucket.kbuckets.maxsize") int kBucketSize,
			@Named("openkad.color.candidates") int nrCandidates,
			@Named("openkad.cache.share") int nrShare,
			@Named("openkad.color.nrcolors") int nrColors,
			@Named("openkad.net.concurrency") int concurrency,
			
			Provider<FindNodeRequest> findNodeRequestProvider,
			Provider<MessageDispatcher<Node>> msgDispatcherProvider,
			Provider<StoreMessage> storeMessageProvider,
			Communicator kadServer,
			KBuckets kBuckets,
			KadCache cache,
			@Named("openkad.testing.nrLocalCacheHits") AtomicInteger nrLocalCacheHits,
			@Named("openkad.testing.nrRemoteCacheHits") AtomicInteger nrRemoteCacheHits) {
		
		this.localNode = localNode;
		this.kBucketSize = kBucketSize;
		this.kBuckets = kBuckets;
		this.findNodeRequestProvider = findNodeRequestProvider;
		this.msgDispatcherProvider = msgDispatcherProvider;
		this.nrCandidates = nrCandidates;
		this.nrShare = nrShare;
		this.storeMessageProvider = storeMessageProvider;
		this.kadServer = kadServer;
		this.cache = cache;
		this.nrColors = nrColors;
		this.concurrency = concurrency;
		
		this.nrLocalCacheHits = nrLocalCacheHits;
		this.nrRemoteCacheHits = nrRemoteCacheHits;
		alreadyQueried = new HashSet<Node>();
		querying = new HashSet<Node>();
	}
	
	
	public int getNrQueried() {
		return nrQueried;
	}
	
	
	private synchronized Node takeUnqueried() {
		for (int i=0; i < knownClosestNodes.size(); ++i) {
			Node n = knownClosestNodes.get(i);
			if (!querying.contains(n) && !alreadyQueried.contains(n)) {
				querying.add(n);
				return n;
			}
		}
		return null;
	}
	
	private synchronized Node takeColorUnqueried() {
		List<Node> allUnqueried = new ArrayList<Node>();
		allUnqueried.addAll(knownClosestNodes);
		allUnqueried.removeAll(querying);
		allUnqueried.removeAll(alreadyQueried);
		
		if (allUnqueried.isEmpty())
			return null;
		
		Node $ = null;
		
		if (allUnqueried.size() > 1) {
			allUnqueried = sort(allUnqueried, on(Node.class).getKey(), colorComparator);
		}
		$ = allUnqueried.get(0);
		
		// if the best we could find is not in the right color, then continue
		// with the normal kademila lookup 
		if ($.getKey().getColor(nrColors) != key.getColor(nrColors))
			return takeUnqueried();
		
		querying.add($);
		return $;
	}
	
	
	private boolean hasMoreToQuery() {
		return !querying.isEmpty() || !alreadyQueried.containsAll(knownClosestNodes);
	}
	
	private void sendFindNode(Node to) {
		FindNodeRequest findNodeRequest = findNodeRequestProvider.get()
			.setSearchCache(true)
			.setKey(key);
		
		msgDispatcherProvider.get()
			.addFilter(new IdMessageFilter(findNodeRequest.getId()))
			.addFilter(new TypeMessageFilter(FindNodeResponse.class))
			.setConsumable(true)
			.setCallback(to, this)
			.send(to, findNodeRequest);
	}
	
	@Override
	public List<Node> doFindValue() {

		List<Node> nodes = cache.search(key);
		if (nodes != null && nodes.size() >= kBucketSize) {
			nrLocalCacheHits.incrementAndGet();
			return nodes;
		}
		
		knownClosestNodes = kBuckets.getClosestNodesByKey(key, kBucketSize);
		knownClosestNodes.add(localNode);
		alreadyQueried.add(localNode);
		KeyComparator keyComparator = new KeyComparator(key);
		colorComparator = new KeyColorComparator(key, nrColors);
		
		List<Node> colorClosest = kBuckets.getClosestNodesByKey(key, nrCandidates);
		querying.addAll(colorClosest);
		for (Node n : colorClosest) {
			sendFindNode(n);
		}
		
		synchronized (this) {
			while (returnedCachedResults == null && !querying.isEmpty()) {
				try {
					wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		int i=0;
		do {
			synchronized(this) {
				knownClosestNodes = sort(knownClosestNodes, on(Node.class).getKey(), keyComparator);
				if (knownClosestNodes.size() >= kBucketSize)
					knownClosestNodes.subList(kBucketSize, knownClosestNodes.size()).clear();
				
				if (returnedCachedResults != null)
					break;
				
				if (!hasMoreToQuery())
					break;
			}
			
			Node n = ((++i % concurrency) == 0) ?
					takeColorUnqueried() :
					takeUnqueried();
			
			if (n != null) {
				sendFindNode(n);
			} else {
				synchronized (this) {
					if (!querying.isEmpty()) {
						try {
							wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
			
		} while (true);
		
		knownClosestNodes = Collections.unmodifiableList(knownClosestNodes);
		
		sendStoreResults(colorClosest);
		
		cache.insert(key, knownClosestNodes);
		
		if (returnedCachedResults != null)
			nrRemoteCacheHits.incrementAndGet();

		synchronized (this) {
			nrQueried = alreadyQueried.size()+querying.size()-1;
		}
		
		return knownClosestNodes;
	}
	
	
	private void sendStoreResults(List<Node> toShareWith) {
		toShareWith.remove(returnedCachedResults);
		if (toShareWith.size() > nrShare)
			toShareWith.subList(nrShare, toShareWith.size()).clear();
		
		StoreMessage storeMessage = storeMessageProvider.get()
			.setKey(key)
			.setNodes(knownClosestNodes);
		for (Node n : toShareWith) {
			// dont send if the remote node has a different color
			if (n.getKey().getColor(nrColors) != key.getColor(nrColors))
				continue;
			try {
				kadServer.send(n, storeMessage);
			} catch (Exception e) {}
		}
	}

	@Override
	public synchronized void completed(KadMessage msg, Node n) {
		notifyAll();
		querying.remove(n);
		alreadyQueried.add(n);
		
		if (returnedCachedResults != null)
			return;
		
		
		List<Node> nodes = ((FindNodeResponse)msg).getNodes();
		nodes.removeAll(querying);
		nodes.removeAll(alreadyQueried);
		nodes.removeAll(knownClosestNodes);
		
		knownClosestNodes.addAll(nodes);
		
		if (((FindNodeResponse)msg).isCachedResults())
			returnedCachedResults = n;
	}
	
	@Override
	public synchronized void failed(Throwable exc, Node n) {
		notifyAll();
		querying.remove(n);
		alreadyQueried.add(n);
	}
}
