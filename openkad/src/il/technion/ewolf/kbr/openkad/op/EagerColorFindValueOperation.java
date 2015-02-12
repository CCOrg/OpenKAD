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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

/**
 * Find value operation according to the colors algorithm. Does not wait for
 * response before continuing with the operation TODO: add a link to the
 * published article
 * 
 * @author eyal.kibbar@gmail.com
 * 
 */
public class EagerColorFindValueOperation extends FindValueOperation implements CompletionHandler<KadMessage, Node> {

	// state
	private List<Node> knownClosestNodes;
	private final Set<Node> alreadyQueried;
	private final Set<Node> querying;
	private Node returnedCachedResults = null;
	private final List<Node> lastSentTo;
	private Comparator<Key> colorComparator;
	private KeyComparator keyComparator;
	private final List<Node> firstSentTo;
	private final AtomicInteger nrMsgsSent;

	// dependencies
	private final Provider<FindNodeRequest> findNodeRequestProvider;
	private final Provider<MessageDispatcher<Node>> msgDispatcherProvider;
	private final Provider<StoreMessage> storeMessageProvider;
	private final KBuckets kBuckets;
	private final Node localNode;
	private final int kBucketSize;
	private final Communicator kadServer;
	private final KadCache cache;
	private final int nrShare;
	private final int nrColors;
	private final int myColor;
	// testing
	private final AtomicInteger nrLocalCacheHits;
	private final AtomicInteger nrRemoteCacheHits;

	@Inject
	EagerColorFindValueOperation(@Named("openkad.local.node") final Node localNode,
			@Named("openkad.bucket.kbuckets.maxsize") final int kBucketSize, @Named("openkad.cache.share") final int nrShare,
			@Named("openkad.color.nrcolors") final int nrColors, @Named("openkad.local.color") final int myColor,

			final Provider<FindNodeRequest> findNodeRequestProvider, final Provider<MessageDispatcher<Node>> msgDispatcherProvider,
			final Provider<StoreMessage> storeMessageProvider, final Communicator kadServer, final KBuckets kBuckets,
			final KadCache cache, @Named("openkad.testing.nrLocalCacheHits") final AtomicInteger nrLocalCacheHits,
			@Named("openkad.testing.nrRemoteCacheHits") final AtomicInteger nrRemoteCacheHits) {

		this.localNode = localNode;
		this.kBucketSize = kBucketSize;
		this.kBuckets = kBuckets;
		this.findNodeRequestProvider = findNodeRequestProvider;
		this.msgDispatcherProvider = msgDispatcherProvider;
		this.nrShare = nrShare;
		this.storeMessageProvider = storeMessageProvider;
		this.kadServer = kadServer;
		this.cache = cache;
		this.nrColors = nrColors;
		this.myColor = myColor;

		this.nrLocalCacheHits = nrLocalCacheHits;
		this.nrRemoteCacheHits = nrRemoteCacheHits;

		this.alreadyQueried = new HashSet<Node>();
		this.querying = new HashSet<Node>();
		this.lastSentTo = new LinkedList<Node>();
		this.firstSentTo = new ArrayList<Node>();
		this.nrMsgsSent = new AtomicInteger();
	}

	@Override
	public int getNrQueried() {
		return this.nrMsgsSent.get();
	}

	private synchronized Node takeUnqueried() {
		for (int i = 0; i < this.knownClosestNodes.size(); ++i) {
			final Node n = this.knownClosestNodes.get(i);
			if (!this.querying.contains(n) && !this.alreadyQueried.contains(n)) {
				this.querying.add(n);
				return n;
			}
		}
		return null;
	}

	private synchronized Node takeColorUnqueried() {
		List<Node> allUnqueried = new ArrayList<Node>();
		allUnqueried.addAll(this.knownClosestNodes);
		allUnqueried.removeAll(this.querying);
		allUnqueried.removeAll(this.alreadyQueried);

		if (allUnqueried.isEmpty())
			return null;

		Node $ = null;

		if (allUnqueried.size() > 1)
			allUnqueried = sort(allUnqueried, on(Node.class).getKey(), this.colorComparator);
		$ = allUnqueried.get(0);

		// if the best we could find is not in the right color, then continue
		// with the normal kademila lookup
		if ($.getKey().getColor(this.nrColors) != this.key.getColor(this.nrColors))
			return takeUnqueried();

		this.querying.add($);
		return $;
	}

	private boolean hasMoreToQuery() {
		return !this.querying.isEmpty() || !this.alreadyQueried.containsAll(this.knownClosestNodes);
	}
	
	private boolean trySendFindNode(final Node to) {
		final FindNodeRequest findNodeRequest = this.findNodeRequestProvider.get().setSearchCache(true).setKey(this.key);

		return this.msgDispatcherProvider.get().addFilter(new IdMessageFilter(findNodeRequest.getId()))
		.addFilter(new TypeMessageFilter(FindNodeResponse.class)).setConsumable(true).setCallback(to, this)
		.trySend(to, findNodeRequest);
	}

	private void sendFindNode(final Node to) {
		final FindNodeRequest findNodeRequest = this.findNodeRequestProvider.get().setSearchCache(true).setKey(this.key);

		this.msgDispatcherProvider.get().addFilter(new IdMessageFilter(findNodeRequest.getId()))
		.addFilter(new TypeMessageFilter(FindNodeResponse.class)).setConsumable(true).setCallback(to, this)
		.send(to, findNodeRequest);
	}

	private void sortKnownClosestNodes() {
		this.knownClosestNodes = sort(this.knownClosestNodes, on(Node.class).getKey(), this.keyComparator);
		if (this.knownClosestNodes.size() >= this.kBucketSize)
			this.knownClosestNodes.subList(this.kBucketSize, this.knownClosestNodes.size()).clear();
	}

	@Override
	public List<Node> doFindValue() {

		final List<Node> nodes = this.cache.search(this.key);
		if (nodes != null && nodes.size() >= this.kBucketSize) {
			this.nrLocalCacheHits.incrementAndGet();
			return nodes;
		}

		this.keyComparator = new KeyComparator(this.key);
		this.knownClosestNodes = this.kBuckets.getClosestNodesByKey(this.key, this.kBucketSize);
		this.knownClosestNodes.add(this.localNode);
		final Collection<Node> bootstrap = getBootstrap();
		bootstrap.removeAll(this.knownClosestNodes);
		this.knownClosestNodes.addAll(bootstrap);
		sortKnownClosestNodes();
		this.alreadyQueried.add(this.localNode);

		final KeyComparator keyComparator = new KeyComparator(this.key);
		this.colorComparator = new KeyColorComparator(this.key, this.nrColors);

		do {
			final Node node = takeColorUnqueried();

			synchronized (this) {
				// check if finished already
				if (!hasMoreToQuery() || this.returnedCachedResults != null)
					break;

				if (node != null){
					if(!trySendFindNode(node)){
						try {
							if(this.querying.size() == 1)
							{
								//If only I try to send I send and block
								this.nrMsgsSent.incrementAndGet();
								sendFindNode(node);
							}
							else
							{
								//If there are pending msgs, i wait for their reply
								this.querying.remove(node);
								wait();
							}
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					else
					{
						this.nrMsgsSent.incrementAndGet();
					}
				}
				else
				{
					if (!this.querying.isEmpty())
						try {
							wait();
						} catch (final InterruptedException e) {
							e.printStackTrace();
						}
				}
			}
		} while (true);

		this.knownClosestNodes = Collections.unmodifiableList(this.knownClosestNodes);

		// only share if i dont have the right color
		if (this.myColor != this.key.getColor(this.nrColors))
			sendStoreResults(this.lastSentTo);

		this.cache.insert(this.key, this.knownClosestNodes);

		if (this.returnedCachedResults != null)
			this.nrRemoteCacheHits.incrementAndGet();

		return this.knownClosestNodes;
	}

	private void sendStoreResults(final List<Node> toShareWith) {
		toShareWith.remove(this.returnedCachedResults);
		if (toShareWith.size() > this.nrShare)
			toShareWith.subList(this.nrShare, toShareWith.size()).clear();

		final StoreMessage storeMessage = this.storeMessageProvider.get().setKey(this.key).setNodes(this.knownClosestNodes);

		for (final Node n : toShareWith) {
			// dont send if the remote node has a different color
			if (n.getKey().getColor(this.nrColors) != this.key.getColor(this.nrColors))
				continue;
			try {
				this.kadServer.send(n, storeMessage);
			} catch (final Exception e) {
			}
		}
	}

	@Override
	public synchronized void completed(final KadMessage msg, final Node n) {
		notifyAll();
		this.querying.remove(n);
		this.alreadyQueried.add(n);

		if (this.returnedCachedResults != null)
			return;

		final List<Node> nodes = ((FindNodeResponse) msg).getNodes();
		nodes.removeAll(this.querying);
		nodes.removeAll(this.alreadyQueried);
		nodes.removeAll(this.knownClosestNodes);
		this.knownClosestNodes.addAll(nodes);
		sortKnownClosestNodes();

		if (((FindNodeResponse) msg).isCachedResults()) {
			this.returnedCachedResults = n;
			return;
		}

		if (n.getKey().getColor(this.nrColors) == this.key.getColor(this.nrColors)) {
			if (this.firstSentTo.size() < this.nrShare)
				this.firstSentTo.add(n);

			this.lastSentTo.add(n);
			if (this.lastSentTo.size() > this.nrShare)
				this.lastSentTo.remove(0);
		}
	}

	@Override
	public synchronized void failed(final Throwable exc, final Node n) {
		notifyAll();
		this.querying.remove(n);
		this.alreadyQueried.add(n);
	}
}
