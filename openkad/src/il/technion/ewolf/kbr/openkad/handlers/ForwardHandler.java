package il.technion.ewolf.kbr.openkad.handlers;

import static ch.lambdaj.Lambda.filter;
import static ch.lambdaj.Lambda.having;
import static ch.lambdaj.Lambda.on;
import static ch.lambdaj.Lambda.sort;
import static org.hamcrest.Matchers.is;
import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.KeyComparator;
import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.concurrent.CompletionHandler;
import il.technion.ewolf.kbr.openkad.bucket.KadBuckets;
import il.technion.ewolf.kbr.openkad.cache.KadCache;
import il.technion.ewolf.kbr.openkad.msg.ForwardMessage;
import il.technion.ewolf.kbr.openkad.msg.ForwardRequest;
import il.technion.ewolf.kbr.openkad.msg.ForwardResponse;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.net.Communicator;
import il.technion.ewolf.kbr.openkad.net.MessageDispatcher;
import il.technion.ewolf.kbr.openkad.net.filter.IdMessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.MessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.TypeMessageFilter;
import il.technion.ewolf.kbr.openkad.op.FindValueOperation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

/**
 * Handle forward requests according to the forward algorithm TODO: link for
 * published article
 * 
 * @author eyal.kibbar@gmail.com
 * 
 */
public class ForwardHandler extends AbstractHandler {

	private final KadCache cache;
	private final Communicator kadServer;
	private final KadBuckets kBuckets;

	private final Provider<FindValueOperation> findValueOperationProvider;
	private final Provider<ForwardRequest> forwardRequestProvider;
	private final Provider<MessageDispatcher<Void>> msgDispatcherProvider;

	private final Node localNode;
	private final int myColor;
	private final int nrColors;
	private final int kBucketSize;
	private final long timeout;

	private final ExecutorService opExecutor;
	private final ExecutorService forwardExecutor;

	// testing
	private final AtomicInteger nrFindNodesWithWrongColor;
	private final AtomicInteger nrForwardHandling;
	private final AtomicInteger nrForwardHandlingFromInitiator;
	private final AtomicInteger nrShortForwardTimeouts;
	private final AtomicInteger nrNacksSent;
	@Inject
	ForwardHandler(final KadCache cache, final Communicator kadServer, final KadBuckets kBuckets,

	@Named("openkad.op.lastFindValue") final Provider<FindValueOperation> findValueOperationProvider,
			final Provider<ForwardRequest> forwardRequestProvider, final Provider<MessageDispatcher<Void>> msgDispatcherProvider,

			@Named("openkad.local.node") final Node localNode,
			@Named("openkad.local.color") final int myColor,
			@Named("openkad.color.nrcolors") final int nrColors,
			@Named("openkad.bucket.kbuckets.maxsize") final int kBucketSize,
			@Named("openkad.net.forwarded.timeout") final long timeout,

			@Named("openkad.executors.op") final ExecutorService opExecutor,
			@Named("openkad.executors.forward") final ExecutorService forwardExecutor,

			// testing
			@Named("openkad.testing.nrFindNodesWithWrongColor") final AtomicInteger nrFindNodesWithWrongColor,
			@Named("openkad.testing.nrForwardHandling") final AtomicInteger nrForwardHandling,
			@Named("openkad.testing.nrForwardHandlingFromInitiator") final AtomicInteger nrForwardHandlingFromInitiator,
			@Named("openkad.testing.nrShortForwardTimeouts") final AtomicInteger nrShortForwardTimeouts,
			@Named("openkad.testing.nrNacksSent") final AtomicInteger nrNacksSent) {

		super(msgDispatcherProvider);
		this.cache = cache;
		this.kadServer = kadServer;
		this.kBuckets = kBuckets;

		this.findValueOperationProvider = findValueOperationProvider;
		this.forwardRequestProvider = forwardRequestProvider;
		this.msgDispatcherProvider = msgDispatcherProvider;

		this.localNode = localNode;
		this.myColor = myColor;
		this.nrColors = nrColors;
		this.kBucketSize = kBucketSize;
		this.timeout = timeout;

		this.opExecutor = opExecutor;
		this.forwardExecutor = forwardExecutor;

		this.nrFindNodesWithWrongColor = nrFindNodesWithWrongColor;
		this.nrForwardHandling = nrForwardHandling;
		this.nrForwardHandlingFromInitiator = nrForwardHandlingFromInitiator;
		this.nrShortForwardTimeouts = nrShortForwardTimeouts;
		this.nrNacksSent = nrNacksSent;
	}

	private void doFindValue(final ForwardRequest req) throws RejectedExecutionException {
		opExecutor.execute(new Runnable() {

			@Override
			public void run() {
				if (myColor != req.getKey().getColor(nrColors))
					nrFindNodesWithWrongColor.incrementAndGet();

				// System.out.println(localNode+": doing the find node");
				final FindValueOperation op = findValueOperationProvider.get().setBootstrap(req.getBootstrap())
						.setKey(req.getKey());

				final List<Node> results = op.doFindValue();

				// System.out.println(localNode+": finished find node, returning results");

				final ForwardMessage msg = req.generateMessage(localNode).setFindNodeHops(op.getNrQueried()).setPathLength(0)
						.setNodes(results);

				// System.out.println(localNode+": sending "+results+" back to "+req.getSrc());

				try {
					kadServer.send(req.getSrc(), msg);
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void completed(final KadMessage msg, final Void nothing) {
		final ForwardRequest req = (ForwardRequest) msg;

		nrForwardHandling.incrementAndGet();
		if (req.isInitiator()) // TODO: remove before publish
			nrForwardHandlingFromInitiator.incrementAndGet();

		// System.out.println(localNode+": recved forward request from "+req.getSrc());

		// check the cache first
		final List<Node> cachedResults = cache.search(req.getKey());
		if (cachedResults != null) {
			System.out.println(localNode + ": cache hit !");
			try {
				kadServer.send(req.getSrc(), req.generateResponse(localNode).setNodes(cachedResults));
			} catch (final IOException e) {
				// could not send back the results
				// nothing to do
			}
			return;
		}

		// no cached result
		// continue with the operation
		assert (cachedResults == null);
		// System.out.println(localNode+": result was not in cache");

		// either forward to someone else or do the job myself
		if (myColor == req.getKey().getColor(nrColors))
			// i need to perform the find value because i have
			// the right color
			// System.out.println(localNode+": I have the right color");
			doFindValueAndSendAckOrNack(req);
		else
			// i am in the wrong color, forward the request to
			// someone else, preferably with the correct color
			doForwardAndSendAckOrNack(req);
	}

	private MessageDispatcher<Void> generateExpectDispatcher(final ForwardRequest incomingReq, final ForwardRequest outgoingReq) {

		return msgDispatcherProvider.get().setConsumable(true).addFilter(new IdMessageFilter(outgoingReq.getId()))
				.addFilter(new TypeMessageFilter(ForwardMessage.class)).setTimeout(timeout, TimeUnit.MILLISECONDS)
				.setCallback(null, new CompletionHandler<KadMessage, Void>() {

					@Override
					public void failed(final Throwable exc, final Void nothing) {
						// nothing to do
					}

					@Override
					public void completed(final KadMessage msg, final Void nothing) {
						// forward back to src
						final ForwardMessage res = (ForwardMessage) msg;
						// System.out.println(localNode+": "+res.getSrc()+" had an answer: "+res.getNodes());

						if (res.isNack())
							sendBackNack(incomingReq, res.getNodes());
						// doFindValueOrSendNack(incomingReq, res.getNodes());

						// response contains real results !!
						// send them back to the requester !
						try {
							kadServer.send(
									incomingReq.getSrc(),
									incomingReq.generateMessage(localNode).setFindNodeHops(res.getFindNodeHops())
											.setPathLength(1 + res.getPathLength()).setNodes(res.getNodes()));

						} catch (final IOException e) {
							e.printStackTrace();
						}
					}
				}).register();
	}

	private void doFindValueAndSendAckOrNack(final ForwardRequest req) {
		// do the find value myself
		try {
			doFindValue(req);

			kadServer.send(req.getSrc(), req.generateResponse(localNode).setAck());

		} catch (final RejectedExecutionException e) {
			// i'm too busy to do the find value myself
			// send back a nack
			sendBackNack(req, new ArrayList<Node>(1));
		} catch (final IOException e) {
			// failed to send ack
			// nothing to do
		}
	}

	void sendBackNack(final ForwardRequest req, List<Node> bootstrap) {
		final List<Node> bucket = kBuckets.getClosestNodesByKey(req.getKey(), kBucketSize);
		bucket.addAll(bootstrap);
		bootstrap = mergeBootstraps(req.getBootstrap(), bucket, req.getKey());
		try {
			this.kadServer.send(req.getSrc(), req.generateMessage(localNode).setNodes(bootstrap).setNack());
			nrNacksSent.incrementAndGet();

		} catch (final IOException e1) {
			// could not send back the nack
			// nothing to do
		}
	}

	private List<Node> mergeBootstraps(final List<Node> b1, final List<Node> b2, final Key key) {
		return mergeBootstraps(b1, b2, key, new ArrayList<Node>(1));
	}

	private List<Node> mergeBootstraps(final List<Node> b1, final List<Node> b2, final Key key, final List<Node> exclude) {
		final Set<Node> bothAndUniq = new HashSet<Node>();
		bothAndUniq.addAll(b1);
		bothAndUniq.addAll(b2);
		bothAndUniq.removeAll(exclude);

		if (bothAndUniq.isEmpty())
			return new ArrayList<Node>(1);

		final List<Node> $ = sort(bothAndUniq, on(Node.class).getKey(), new KeyComparator(key));
		if ($.size() > kBucketSize)
			$.subList(kBucketSize, $.size()).clear();
		return $;
	}

	private void sendForwardRequest(final ForwardRequest incomingReq, final ForwardRequest outgoingReq,
			final MessageDispatcher<Void> expectDispatcher, final Node nextHop) {

		msgDispatcherProvider.get().setConsumable(true).addFilter(new IdMessageFilter(outgoingReq.getId()))
				.addFilter(new TypeMessageFilter(ForwardResponse.class))
				.setCallback(null, new CompletionHandler<KadMessage, Void>() {

					@Override
					public void failed(final Throwable exc, final Void nothing) {
						// no need to wait for result, it will
						// never arrive
						expectDispatcher.cancel(exc);
						nrShortForwardTimeouts.incrementAndGet();
						kBuckets.markAsDead(nextHop);

						sendBackNack(incomingReq, new ArrayList<Node>(1));
						// doFindValueOrSendNack(incomingReq,
						// incomingReq.getBootstrap());
					}

					@Override
					public void completed(final KadMessage msg, final Void nothing) {
						// we got the response from the next hop
						// hopefully it will do the job
						// if not, I will do the job
						final ForwardResponse res = (ForwardResponse) msg;

						if (res.isAck()) {
							// System.out.println(localNode+": remote node is calculating for me");
							// next hop got the request and is resolving it
							// an answer should arrive soon to the expectMessage
							// handler (the answer can either nack or the
							// results)

						} else if (res.isNack())
							// do the op myself with the nack x-tra nodes
							// doFindValueOrSendNack(incomingReq,
							// res.getNodes());
							sendBackNack(incomingReq, res.getNodes());
						else {
							// response is neither ack nor nack
							// the remote node had an answer !!
							// System.out.println(localNode+": remote node had an answer in its cache");
							// no need to expect a message, we already got the
							// results
							expectDispatcher.cancel(new CancellationException());

							// return it to the requester
							try {
								kadServer.send(incomingReq.getSrc(), incomingReq.generateMessage(localNode).setPathLength(0)
										.setNodes(res.getNodes()));

							} catch (final IOException e) {
								// could not send back the results
								// nothing to do
								e.printStackTrace();
							}
						}
					}
				}).send(nextHop, outgoingReq);
	}

	/**
	 * finds the next hop according to its color If there is a node with the
	 * right color, return it otherwise, if there is a node with a better key
	 * than me, return it if there isn't, return null
	 * 
	 * @param bootstraps
	 * @return
	 */
	private Node calcNextHop(final List<Node> bootstraps, final Key key) {
		if (bootstraps.isEmpty())
			return null;

		// search the bootstraps for a node with the correct color
		final int keyColor = key.getColor(nrColors);
		final List<Node> correctColorNodes = filter(having(on(Node.class).getKey().getColor(nrColors), is(keyColor)), bootstraps);

		if (!correctColorNodes.isEmpty())
			// we found some nodes with the correct color !
			// return one of them
			return correctColorNodes.get(0);

		// we did not find any node with the correct color
		// find a nodes with the best key
		final Node $ = bootstraps.get(0);

		final KeyComparator comparator = new KeyComparator(key);
		// check that the node with the best key has a better key than me
		return comparator.compare(localNode.getKey(), $.getKey()) <= 0 ? null : $;
	}

	private void doForwardAndSendAckOrNack(final ForwardRequest incomingReq) {
		// forward the request to someone else
		System.out.println(localNode + ": I dont have the right color");
		final List<Node> myBootstrap = kBuckets.getAllFromBucket(incomingReq.getKey());
		if (myBootstrap.isEmpty()) {
			// if we cannot continue advancing to target, do the find node
			// myself
			System.out.println(localNode + ": could not adavnce to target, do the find node myself");
			doFindValueAndSendAckOrNack(incomingReq);
			return;
		}

		final List<Node> mergedBootstraps = mergeBootstraps(incomingReq.getBootstrap(), myBootstrap, incomingReq.getKey());

		final ForwardRequest outgoingReq = forwardRequestProvider.get().setKey(incomingReq.getKey()).setBootstrap(mergedBootstraps);

		final Node nextHop = calcNextHop(mergedBootstraps, incomingReq.getKey());
		if (nextHop == null) {
			System.out.println(localNode + ": no suitable next hop was found");
			doFindValueAndSendAckOrNack(incomingReq);
			return;
		}

		System.out.println(localNode + ": forwarding to " + nextHop);

		// expect a message from nextHop
		final MessageDispatcher<Void> expectMessage = generateExpectDispatcher(incomingReq, outgoingReq);

		try {
			// send a request to next hop
			// if next hop returns a result, the expect message
			// will be canceled and the result will be sent back
			// to the requester
			// if the next hop only returns an ACK, we will continue
			// waiting for the result in the expect message.
			forwardExecutor.execute(new Runnable() {
				@Override
				public void run() {
					sendForwardRequest(incomingReq, outgoingReq, expectMessage, nextHop);
				}
			});
			// message is scheduled for forwarding
			// send ack back to the requester
			try {
				kadServer.send(incomingReq.getSrc(), incomingReq.generateResponse(localNode).setAck());
			} catch (final IOException e) {
				// failed to send ack
				// nothing to do
			}

		} catch (final RejectedExecutionException e) {
			expectMessage.cancel(e);
			// could not forward request
			// send nack
			sendBackNack(incomingReq, kBuckets.getClosestNodesByKey(incomingReq.getKey(), kBucketSize));
		}
	}

	@Override
	public void failed(final Throwable exc, final Void nothing) {
		// should never b here
	}

	@Override
	protected Collection<MessageFilter> getFilters() {
		return Arrays.asList(new MessageFilter[]{new TypeMessageFilter(ForwardRequest.class)});
	}

}
