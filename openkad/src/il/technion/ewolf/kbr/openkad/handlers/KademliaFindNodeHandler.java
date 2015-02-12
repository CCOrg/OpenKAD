package il.technion.ewolf.kbr.openkad.handlers;

import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.openkad.bucket.KBuckets;
import il.technion.ewolf.kbr.openkad.cache.KadCache;
import il.technion.ewolf.kbr.openkad.msg.FindNodeRequest;
import il.technion.ewolf.kbr.openkad.msg.FindNodeResponse;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.net.Communicator;
import il.technion.ewolf.kbr.openkad.net.MessageDispatcher;
import il.technion.ewolf.kbr.openkad.net.filter.MessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.TypeMessageFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

/**
 * Handle find node requests by giving the known closest nodes to the requested
 * key from the KBuckets data structure
 * 
 * @author eyal.kibbar@gmail.com
 * 
 */
public class KademliaFindNodeHandler extends AbstractHandler implements FindNodeHandler {
	private final Communicator kadServer;
	private final Node localNode;
	private final KadCache cache;
	private final KBuckets kBuckets;
	private final int kBucketSize;

	private final AtomicInteger nrFindnodeHits;
	private final AtomicInteger nrFindnodeMiss;

	@Inject
	KademliaFindNodeHandler(final Provider<MessageDispatcher<Void>> msgDispatcherProvider, final Communicator kadServer,
			@Named("openkad.local.node") final Node localNode, final KadCache cache, final KBuckets kBuckets,
			@Named("openkad.bucket.kbuckets.maxsize") final int kBucketSize,

			@Named("openkad.testing.nrFindnodeHits") final AtomicInteger nrFindnodeHits,
			@Named("openkad.testing.nrFindnodeMiss") final AtomicInteger nrFindnodeMiss) {

		super(msgDispatcherProvider);
		this.kadServer = kadServer;
		this.localNode = localNode;
		this.cache = cache;
		this.kBuckets = kBuckets;
		this.kBucketSize = kBucketSize;

		this.nrFindnodeHits = nrFindnodeHits;
		this.nrFindnodeMiss = nrFindnodeMiss;
	}

	@Override
	public void completed(final KadMessage msg, final Void attachment) {

		final FindNodeRequest findNodeRequest = ((FindNodeRequest) msg);
		final FindNodeResponse findNodeResponse = findNodeRequest.generateResponse(this.localNode).setCachedResults(false);

		List<Node> cachedResults = null;

		if (!findNodeRequest.shouldSearchCache())
			findNodeResponse.setNodes(this.kBuckets.getClosestNodesByKey(findNodeRequest.getKey(), this.kBucketSize));
		else {
			// requester ask to search in cache
			cachedResults = this.cache.search(findNodeRequest.getKey());

			if (cachedResults == null) {
				this.nrFindnodeMiss.incrementAndGet();
				findNodeResponse.setNodes(this.kBuckets.getClosestNodesByKey(findNodeRequest.getKey(), this.kBucketSize));
			} else {
				this.nrFindnodeHits.incrementAndGet();
				findNodeResponse.setNodes(new ArrayList<Node>(cachedResults)).setCachedResults(true);

			}
		}

		try {
			this.kadServer.send(msg.getSrc(), findNodeResponse);
		} catch (final IOException e) {
			// could not send back a response
			// nothing to do
			e.printStackTrace();
		}
	}

	@Override
	public void failed(final Throwable exc, final Void attachment) {
		// should never b here
		exc.printStackTrace();
	}

	@Override
	protected Collection<MessageFilter> getFilters() {
		// only accept FindNodeRequests messages
		return Arrays.asList(new MessageFilter[]{new TypeMessageFilter(FindNodeRequest.class)});
	}
}
