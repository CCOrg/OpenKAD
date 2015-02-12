package il.technion.ewolf.kbr.openkad.bucket;

import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.concurrent.CompletionHandler;
import il.technion.ewolf.kbr.openkad.KadNode;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.msg.PingRequest;
import il.technion.ewolf.kbr.openkad.msg.PingResponse;
import il.technion.ewolf.kbr.openkad.net.MessageDispatcher;
import il.technion.ewolf.kbr.openkad.net.filter.IdMessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.TypeMessageFilter;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

/**
 * A bucket with the following policy:
 * when inserting a node do the following:
 * 1. if the node is already in the bucket, move it to be the last
 * 2. if the node is not in the bucket and the bucket is not full, move it to be the last in the bucket
 * 3. if the node is not in the bucket and the bucket is full, ping the first node in the bucket:
 *  a. if it returned a ping, move it to be the last in bucket and don't insert the given node
 *  b. if it did not returned a ping, remove it from the bucket and insert the given node as last
 *  
 * @author eyal.kibbar@gmail.com
 *
 */
public class StableBucket implements Bucket {

	// state
	private final List<KadNode> bucket;

	//dependencies
	private final int maxSize;
	private final long validTimespan;
	private final Provider<PingRequest> pingRequestProvider;
	private final Provider<MessageDispatcher<Void>> msgDispatcherProvider;
	private final ExecutorService pingExecutor;

	@Inject
	public StableBucket(
			int maxSize,
			@Named("openkad.bucket.valid_timespan") long validTimespan,
			@Named("openkad.executors.ping") ExecutorService pingExecutor,
			Provider<PingRequest> pingRequestProvider,
			Provider<MessageDispatcher<Void>> msgDispatcherProvider) {

		this.maxSize = maxSize;
		this.bucket = new LinkedList<KadNode>();
		this.validTimespan = validTimespan;
		this.pingExecutor = pingExecutor;
		this.pingRequestProvider = pingRequestProvider;
		this.msgDispatcherProvider = msgDispatcherProvider;
	}

	@Override
	public synchronized void insert(final KadNode n) {
		int i = bucket.indexOf(n);
		if (i != -1) {
			// found node in bucket

			// if heard from n (it is possible to insert n i never had
			// contact with simply by hearing about from another node)
			if (bucket.get(i).getLastContact() < n.getLastContact()) {
				KadNode s = bucket.remove(i);
				s.setNodeWasContacted(n.getLastContact());
				bucket.add(s);
			}
		} else if (bucket.size() < maxSize) {
			// not found in bucket and there is enough room for n
			bucket.add(n);

		} else {
			// n is not in bucket and bucket is full

			// don't bother to insert n if I never recved a msg from it
			if (n.hasNeverContacted())
				return;

			// check the first node, ping him if no one else is currently pinging
			KadNode inBucketReplaceCandidate = bucket.get(0);

			// the first node was only inserted indirectly (meaning, I never recved
			// a msg from it !) and I did recv a msg from n.
			if (inBucketReplaceCandidate.hasNeverContacted()) {
				bucket.remove(inBucketReplaceCandidate);
				bucket.add(n);
				return;
			}

			// ping is still valid, don't replace
			if (inBucketReplaceCandidate.isPingStillValid(validTimespan))
				return;

			// send ping and act accordingly
			if (inBucketReplaceCandidate.lockForPing()) {
				sendPing(bucket.get(0), n);
			}
		}
	}

	private void sendPing(final KadNode inBucket, final KadNode replaceIfFailed) {

		final PingRequest pingRequest = pingRequestProvider.get();

		final MessageDispatcher<Void> dispatcher = msgDispatcherProvider.get()
				.setConsumable(true)
				.addFilter(new IdMessageFilter(pingRequest.getId()))
				.addFilter(new TypeMessageFilter(PingResponse.class))
				.setCallback(null, new CompletionHandler<KadMessage, Void>() {
					@Override
					public void completed(KadMessage msg, Void nothing) {
						// ping was recved
						inBucket.setNodeWasContacted();
						inBucket.releasePingLock();
						synchronized (StableBucket.this) {
							if (bucket.remove(inBucket)) {
								bucket.add(inBucket);
							}
						}
					}
					@Override
					public void failed(Throwable exc, Void nothing) {
						// ping was not recved
						synchronized (StableBucket.this) {
							// try to remove the already in bucket and 
							// replace it with the new candidate that we
							// just heard from.
							if (bucket.remove(inBucket)) { 
								// successfully removed the old node that
								// did not answer my ping

								// try insert the new candidate
								if (!bucket.add(replaceIfFailed)) {
									// candidate was already in bucket
									// return the inBucket to be the oldest node in
									// the bucket since we don't want our bucket
									// to shrink unnecessarily
									bucket.add(0, inBucket);
								}
							}
						}
						inBucket.releasePingLock();
					}
				});


		try {
			pingExecutor.execute(new Runnable() {

				@Override
				public void run() {
					dispatcher.send(inBucket.getNode(), pingRequest);
				}
			});
		} catch (Exception e) {
			inBucket.releasePingLock();
		}
	}

	@Override
	public synchronized void markDead(Node n) {
		for (int i=0; i < bucket.size(); ++i) {
			KadNode kadNode = bucket.get(i);
			if (kadNode.getNode().equals(n)) {
				// mark dead an move to front
				kadNode.markDead();
				bucket.remove(i);
				bucket.add(0, kadNode);
			}
		}
	}

	@Override
	public synchronized void addNodesTo(Collection<Node> c) {
		for (KadNode n : bucket) {
			c.add(n.getNode());
		}
	}

	@Override
	public synchronized String toString() {
		return bucket.toString();
	}

}
