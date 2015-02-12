package il.technion.ewolf.kbr.openkad.op;

import static ch.lambdaj.Lambda.on;
import static ch.lambdaj.Lambda.sort;
import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.KeyComparator;
import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.concurrent.CompletionHandler;
import il.technion.ewolf.kbr.openkad.bucket.KBuckets;
import il.technion.ewolf.kbr.openkad.msg.FindNodeRequest;
import il.technion.ewolf.kbr.openkad.msg.FindNodeResponse;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.net.MessageDispatcher;
import il.technion.ewolf.kbr.openkad.net.filter.IdMessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.TypeMessageFilter;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

/**
 * Find node operation as defined in the kademlia algorithm
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public class KadFindNodeOperation implements CompletionHandler<KadMessage, Node>, FindNodeOperation {

	// state
	private List<Node> knownClosestNodes;
	private Key key;
	private final Set<Node> alreadyQueried;
	private final Set<Node> querying;
	private int nrQueried;
	
	// dependencies
	private final Provider<FindNodeRequest> findNodeRequestProvider;
	private final Provider<MessageDispatcher<Node>> msgDispatcherProvider;
	private final int kBucketSize;
	private final KBuckets kBuckets;
	private final Node localNode;
	
	@Inject
	KadFindNodeOperation(
			@Named("openkad.local.node") Node localNode,
			@Named("openkad.bucket.kbuckets.maxsize") int kBucketSize,
			Provider<FindNodeRequest> findNodeRequestProvider,
			Provider<MessageDispatcher<Node>> msgDispatcherProvider,
			KBuckets kBuckets) {
		
		this.localNode = localNode;
		this.kBucketSize = kBucketSize;
		this.kBuckets = kBuckets;
		this.findNodeRequestProvider = findNodeRequestProvider;
		this.msgDispatcherProvider = msgDispatcherProvider;
		
		alreadyQueried = new HashSet<Node>();
		querying = new HashSet<Node>();
	}
	
	/* (non-Javadoc)
	 * @see il.technion.ewolf.kbr.openkad.op.FindNodeOperation#setKey(il.technion.ewolf.kbr.Key)
	 */
	@Override
	public FindNodeOperation setKey(Key key) {
		this.key = key;
		return this;
	}
	
	/* (non-Javadoc)
	 * @see il.technion.ewolf.kbr.openkad.op.FindNodeOperation#getNrQueried()
	 */
	@Override
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
	
	private boolean hasMoreToQuery() {
		return !querying.isEmpty() || !alreadyQueried.containsAll(knownClosestNodes);
	}
	
	private void sendFindNode(Node to) {
		FindNodeRequest findNodeRequest = findNodeRequestProvider.get()
			.setSearchCache(false)
			.setKey(key);
		
		msgDispatcherProvider.get()
			.addFilter(new IdMessageFilter(findNodeRequest.getId()))
			.addFilter(new TypeMessageFilter(FindNodeResponse.class))
			.setConsumable(true)
			.setCallback(to, this)
			.send(to, findNodeRequest);
	}
	
	/* (non-Javadoc)
	 * @see il.technion.ewolf.kbr.openkad.op.FindNodeOperation#doFindNode()
	 */
	@Override
	public List<Node> doFindNode() {

		knownClosestNodes = kBuckets.getClosestNodesByKey(key, kBucketSize);
		knownClosestNodes.add(localNode);
		alreadyQueried.add(localNode);
		KeyComparator keyComparator = new KeyComparator(key);
		
		do {
			Node n = takeUnqueried();
			
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
			
			synchronized(this) {
				knownClosestNodes = sort(knownClosestNodes, on(Node.class).getKey(), keyComparator);
				if (knownClosestNodes.size() >= kBucketSize)
					knownClosestNodes.subList(kBucketSize, knownClosestNodes.size()).clear();
				
				if (!hasMoreToQuery())
					break;
			}
			
		} while (true);

		knownClosestNodes = Collections.unmodifiableList(knownClosestNodes);
		
		synchronized (this) {
			nrQueried = alreadyQueried.size()-1 + querying.size();
		}
		
		return knownClosestNodes;
	}
	
	/* (non-Javadoc)
	 * @see il.technion.ewolf.kbr.openkad.op.FindNodeOperation#completed(il.technion.ewolf.kbr.openkad.msg.KadMessage, il.technion.ewolf.kbr.Node)
	 */
	@Override
	public synchronized void completed(KadMessage msg, Node n) {
		notifyAll();
		querying.remove(n);
		alreadyQueried.add(n);
		
		List<Node> nodes = ((FindNodeResponse)msg).getNodes();
		nodes.removeAll(querying);
		nodes.removeAll(alreadyQueried);
		nodes.removeAll(knownClosestNodes);
		
		knownClosestNodes.addAll(nodes);
	}
	
	/* (non-Javadoc)
	 * @see il.technion.ewolf.kbr.openkad.op.FindNodeOperation#failed(java.lang.Throwable, il.technion.ewolf.kbr.Node)
	 */
	@Override
	public synchronized void failed(Throwable exc, Node n) {
		notifyAll();
		querying.remove(n);
		alreadyQueried.add(n);
		
	}
}
