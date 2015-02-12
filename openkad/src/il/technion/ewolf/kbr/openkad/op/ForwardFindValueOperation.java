package il.technion.ewolf.kbr.openkad.op;

import static ch.lambdaj.Lambda.on;
import static ch.lambdaj.Lambda.sort;
import il.technion.ewolf.kbr.KeyColorComparator;
import il.technion.ewolf.kbr.KeyComparator;
import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.openkad.bucket.KBuckets;
import il.technion.ewolf.kbr.openkad.cache.KadCache;
import il.technion.ewolf.kbr.openkad.msg.ForwardMessage;
import il.technion.ewolf.kbr.openkad.msg.ForwardRequest;
import il.technion.ewolf.kbr.openkad.msg.ForwardResponse;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.net.MessageDispatcher;
import il.technion.ewolf.kbr.openkad.net.filter.IdMessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.TypeMessageFilter;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

public class ForwardFindValueOperation extends FindValueOperation {

	// state
	private int nrQueried = 0;
	private List<Node> bootstrap;

	// dependencies
	private final int kBucketSize;
	private final int nrCandidates;
	private final int nrColors;
	private final int myColor;
	// private final int nrShare;
	private final long timeout;
	private final Node localNode;

	private final Provider<ForwardRequest> forwardRequestProvider;
	private final Provider<MessageDispatcher<Void>> msgDispatcherProvider;
	private final Provider<FindValueOperation> findValueOperationProvider;
	// private final Provider<StoreMessage> storeMessageProvider;

	private final KBuckets kBuckets;
	private final KadCache cache;
	// private final KadServer kadServer;

	// testing
	private final AtomicInteger nrLongTimeouts;
	private final List<Integer> hopsToResultHistogram;
	private final List<Integer> findNodeHopsHistogram;
	private final AtomicInteger maxHopsToResult;
	private final AtomicInteger remoteCacheHits;
	private final AtomicInteger localCacheHits;
	private final AtomicInteger nrNacks;
	private final AtomicInteger nrFindNodesWithWrongColor;

	@Inject
	ForwardFindValueOperation(
			@Named("openkad.bucket.kbuckets.maxsize") final int kBucketSize,
			@Named("openkad.color.candidates") final int nrCandidates,
			@Named("openkad.color.nrcolors") final int nrColors,
			@Named("openkad.local.color") final int myColor,
			// @Named("openkad.cache.share") int nrShare,
			@Named("openkad.net.forwarded.timeout") final long timeout,
			@Named("openkad.local.node") final Node localNode,

			final Provider<ForwardRequest> forwardRequestProvider,
			final Provider<MessageDispatcher<Void>> msgDispatcherProvider,
			@Named("openkad.op.lastFindValue") final Provider<FindValueOperation> findValueOperationProvider,
			// Provider<StoreMessage> storeMessageProvider,

			final KBuckets kBuckets,
			final KadCache cache,
			// KadServer kadServer,

			// testing
			@Named("openkad.testing.nrLongTimeouts") final AtomicInteger nrLongTimeouts,
			@Named("openkad.testing.hopsToResultHistogram") final List<Integer> hopsToResultHistogram,
			@Named("openkad.testing.findNodeHopsHistogram") final List<Integer> findNodeHopsHistogram,
			@Named("openkad.testing.maxHopsToResult") final AtomicInteger maxHopsToResult,
			@Named("openkad.testing.remoteCacheHits") final AtomicInteger remoteCacheHits,
			@Named("openkad.testing.remoteCacheHits") final AtomicInteger localCacheHits,
			@Named("openkad.testing.nrNacks") final AtomicInteger nrNacks,
			@Named("openkad.testing.nrFindNodesWithWrongColor") final AtomicInteger nrFindNodesWithWrongColor) {

		this.kBucketSize = kBucketSize;
		this.nrCandidates = nrCandidates;
		this.nrColors = nrColors;
		this.myColor = myColor;
		// this.nrShare = nrShare;
		this.timeout = timeout;
		this.localNode = localNode;

		this.forwardRequestProvider = forwardRequestProvider;
		this.msgDispatcherProvider = msgDispatcherProvider;
		this.findValueOperationProvider = findValueOperationProvider;
		// this.storeMessageProvider = storeMessageProvider;

		this.kBuckets = kBuckets;
		this.cache = cache;
		// this.kadServer = kadServer;

		this.nrLongTimeouts = nrLongTimeouts;
		this.hopsToResultHistogram = hopsToResultHistogram;
		this.findNodeHopsHistogram = findNodeHopsHistogram;
		this.maxHopsToResult = maxHopsToResult;
		this.remoteCacheHits = remoteCacheHits;
		this.localCacheHits = localCacheHits;
		this.nrNacks = nrNacks;
		this.nrFindNodesWithWrongColor = nrFindNodesWithWrongColor;
	}

	@Override
	public int getNrQueried() {
		return this.nrQueried;
	}

	private List<Node> sendForwardRequest(final Node to, final ForwardRequest req) throws CancellationException,
			InterruptedException, ExecutionException {
		// System.out.println(localNode+": forwarding to "+to);

		final Future<KadMessage> requestFuture = this.msgDispatcherProvider.get().setConsumable(true)
				.addFilter(new IdMessageFilter(req.getId())).addFilter(new TypeMessageFilter(ForwardResponse.class))
				.futureSend(to, req);

		final ForwardResponse res = (ForwardResponse) requestFuture.get();
		if (res.isAck()) {
			// System.out.println(localNode+": remote node return ack");

		} else if (res.isNack()) {
			// System.out.println(localNode+": remote node return nack");

			this.nrNacks.incrementAndGet();
			this.bootstrap.addAll(res.getNodes());

			// try to avoid sending more messages to this node
			this.kBuckets.markAsDead(res.getSrc());

			// Logical throw to indicate result was a nack.
			// will be caught outside to cancel the expect.
			throw new CancellationException("nack");

		} else {
			assert (res.getNodes() != null);
			this.remoteCacheHits.incrementAndGet();
			// System.out.println(localNode+": cache hit");

			// we had a cache hit !
			// no need to wait for future messages
			final int hopsToResult = 1;

			if (hopsToResult > this.maxHopsToResult.get())
				this.maxHopsToResult.set(hopsToResult);
			this.hopsToResultHistogram.add(hopsToResult);
			return res.getNodes();

		}

		return null;
	}

	private List<Node> waitForResults(final Future<KadMessage> expectMessage) throws InterruptedException, ExecutionException,
			CancellationException {
		final ForwardMessage msg = (ForwardMessage) expectMessage.get();

		if (msg.isNack()) {
			this.nrNacks.incrementAndGet();
			this.bootstrap.addAll(msg.getNodes());
			this.kBuckets.markAsDead(msg.getSrc());

			throw new CancellationException("nack");

		} else if (msg.getNodes() != null) {
			// remote node has calculated the results for me
			final int hopsToResult = 1 + msg.getPathLength();

			if (hopsToResult > this.maxHopsToResult.get())
				this.maxHopsToResult.set(hopsToResult);

			this.hopsToResultHistogram.add(hopsToResult);

			if (msg.getFindNodeHops() != 0)
				this.findNodeHopsHistogram.add(msg.getFindNodeHops());
			else
				this.remoteCacheHits.incrementAndGet();

			// System.out.println(localNode+": remote node has calculated the results for me");
			return msg.getNodes();

		} else
			// remote node has returned null, move on to the
			// next candidate
			// System.out.println(localNode+": remote node has returned null, move on to the next candidate");
			this.nrNacks.incrementAndGet();

		return null;
	}

	@Override
	public List<Node> doFindValue() {

		final List<Node> nodes = this.cache.search(this.key);
		if (nodes != null)
			return nodes;

		this.bootstrap = this.kBuckets.getClosestNodesByKey(this.key, this.kBucketSize);
		this.bootstrap.add(this.localNode);
		final List<Node> candidates = sort(this.bootstrap, on(Node.class).getKey(), new KeyColorComparator(this.key, this.nrColors));

		if (this.myColor == this.key.getColor(this.nrColors)) {
			this.hopsToResultHistogram.add(0);
			return doFindNode();
		}

		// List<Node> colorCandidates = kBuckets.getNodesFromColorBucket(key);
		// candidates.removeAll(colorCandidates);
		// candidates.addAll(0, colorCandidates);
		if (candidates.size() > this.nrCandidates)
			candidates.subList(this.nrCandidates, candidates.size()).clear();

		do {
			if (candidates.isEmpty()) {
				this.hopsToResultHistogram.add(0);
				return doFindNode();
			}

			final Node n = candidates.remove(0);
			++this.nrQueried;

			if (n.equals(this.localNode)) {
				this.hopsToResultHistogram.add(0);
				return doFindNode();
			}

			// sort and cut the bootstrap
			this.bootstrap = sort(this.bootstrap, on(Node.class).getKey(), new KeyComparator(this.key));
			if (this.bootstrap.size() > this.kBucketSize)
				this.bootstrap.subList(this.kBucketSize, this.bootstrap.size()).clear();

			final ForwardRequest req = this.forwardRequestProvider.get().setInitiator() // TODO:
																						// remove
																						// b4
																						// publish
					.setBootstrap(this.bootstrap).setKey(this.key);

			final Future<KadMessage> expectMessage = this.msgDispatcherProvider.get().setConsumable(true)
					.addFilter(new IdMessageFilter(req.getId())).addFilter(new TypeMessageFilter(ForwardMessage.class))
					.setTimeout(this.timeout, TimeUnit.MILLISECONDS).futureRegister();

			// send the forward request and wait for result/ack/nack
			try {
				final List<Node> results = sendForwardRequest(n, req);
				if (results != null) {
					expectMessage.cancel(false);
					// shareResults(colorCandidates, results);
					return results;
				}
				// results is null, that means the remote node will
				// calculate the result for me

			} catch (final Exception e) {
				System.out.println(this.localNode + ": failed recv ack or nack from remote node");
				expectMessage.cancel(false);
				this.kBuckets.markAsDead(n);
				continue;
			}

			// wait for the result to arrive
			try {
				final List<Node> results = waitForResults(expectMessage);
				if (results != null)
					// shareResults(colorCandidates, results);
					return results;

			} catch (final Exception e) {
				System.out.println(this.localNode + ": failed to recv expected message");
				this.kBuckets.markAsDead(n);
				this.nrLongTimeouts.incrementAndGet();
			}

		} while (true);
	}

	/*
	 * private void shareResults(List<Node> toShareWith, List<Node> results) {
	 * if (toShareWith.size() > nrShare) toShareWith.subList(nrShare,
	 * toShareWith.size()).clear();
	 * 
	 * StoreMessage storeMessage = storeMessageProvider.get() .setKey(key)
	 * .setNodes(results); for (Node n : toShareWith) { // dont send if the
	 * remote node has a different color if (n.getKey().getColor(nrColors) !=
	 * key.getColor(nrColors)) continue; try { kadServer.send(n, storeMessage);
	 * } catch (Exception e) {} } }
	 */

	private List<Node> doFindNode() {

		if (this.myColor != this.key.getColor(this.nrColors))
			this.nrFindNodesWithWrongColor.incrementAndGet();
		final FindValueOperation op = this.findValueOperationProvider.get().setBootstrap(this.bootstrap).setKey(this.key);

		final List<Node> $ = op.doFindValue();

		this.nrQueried += op.getNrQueried();
		System.out.println("nrQueried: " + this.nrQueried);

		if (op.getNrQueried() == 0)
			this.localCacheHits.incrementAndGet();

		return $;
	}
}
