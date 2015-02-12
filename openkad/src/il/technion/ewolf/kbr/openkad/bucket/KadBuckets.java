package il.technion.ewolf.kbr.openkad.bucket;

import static ch.lambdaj.Lambda.on;
import static ch.lambdaj.Lambda.sort;
import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.KeyColorComparator;
import il.technion.ewolf.kbr.KeyComparator;
import il.technion.ewolf.kbr.KeyFactory;
import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.concurrent.CompletionHandler;
import il.technion.ewolf.kbr.openkad.KadNode;
import il.technion.ewolf.kbr.openkad.NodeStorage;
import il.technion.ewolf.kbr.openkad.msg.FindNodeResponse;
import il.technion.ewolf.kbr.openkad.msg.ForwardMessage;
import il.technion.ewolf.kbr.openkad.msg.ForwardRequest;
import il.technion.ewolf.kbr.openkad.msg.ForwardResponse;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.msg.PingResponse;
import il.technion.ewolf.kbr.openkad.net.MessageDispatcher;
import il.technion.ewolf.kbr.openkad.net.filter.SrcExcluderMessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.TypeExcluderMessageFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

/**
 * This is a data structures that holds all the known nodes
 * It sorts them into buckets according to their keys common prefix
 * with the local node's key.
 * 
 * A node with a different MSB in its key than the local node's MSB
 * will be inserted to the last bucket.
 * A node with ONLY the LSB different will be inserted into the first bucket.
 * Generally, a node with a common prefix the length of k bits with the local
 * node will be inserted to the KeyLengthInBit - k bucket
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public class KadBuckets implements NodeStorage,KBuckets {

	private final Provider<MessageDispatcher<Object>> msgDispatcherProvider;
	private final Provider<KadNode> kadNodeProvider;
	private final Bucket[] kbuckets;
	protected final Node localNode;
	private final KeyFactory keyFactory;
	private final int nrColors;
	@Inject
	protected
	KadBuckets(
			KeyFactory keyFactory,
			Provider<KadNode> kadNodeProvider,
			Provider<MessageDispatcher<Object>> msgDispatcherProvider,
			@Named("openkad.bucket.kbuckets") Provider<Bucket> kBucketProvider,
			@Named("openkad.local.node") Node localNode,
			@Named("openkad.color.nrcolors") int nrColors) {
		this.keyFactory = keyFactory;
		this.msgDispatcherProvider = msgDispatcherProvider;
		this.kadNodeProvider = kadNodeProvider;
		this.localNode = localNode;
		this.nrColors = nrColors;
		
		kbuckets = new Bucket[keyFactory.getBitLength()];
		for (int i=0; i < kbuckets.length; ++i) {
			kbuckets[i] = kBucketProvider.get();
		}
	}

	/**
	 * Uses the keyFactory to generate keys which will fit to different buckets
	 * @return a list of random keys where no 2 keys will fit into the same bucket
	 */
	public List<Key> randomKeysForAllBuckets() {
		List<Key> $ = new ArrayList<Key>();
		for (int i=0; i < kbuckets.length; ++i) {
			Key key = keyFactory.generate(i).xor(localNode.getKey());
			$.add(key);
		}
		return $;
	}
	
	/**
	 * Register this data structure to listen to incoming messages and update itself
	 * accordingly.
	 * Invoke this method after creating the entire system
	 */
	public synchronized void registerIncomingMessageHandler() {
		msgDispatcherProvider.get()
			.setConsumable(false)
			// do not add PingResponse since it might create a loop
			.addFilter(new TypeExcluderMessageFilter(PingResponse.class))
			.addFilter(new SrcExcluderMessageFilter(localNode))
			
			.setCallback(null, new CompletionHandler<KadMessage, Object>() {
				
				@Override
				public void failed(Throwable exc, Object attachment) {
					// should never be here
					exc.printStackTrace();
				}
				
				@Override
				public void completed(KadMessage msg, Object attachment) {
					KadBuckets.this.insert(kadNodeProvider.get()
							.setNode(msg.getSrc())
							.setNodeWasContacted());
					
					// try to sniff the message for more information, such as
					// nodes in its content
					List<Node> nodes = null;
					if (msg instanceof FindNodeResponse) {
						nodes = ((FindNodeResponse)msg).getNodes();
					} else if (msg instanceof ForwardResponse) {
						nodes = ((ForwardResponse)msg).getNodes();
					} else if (msg instanceof ForwardMessage) {
						nodes = ((ForwardMessage)msg).getNodes();
					} else if (msg instanceof ForwardRequest) {
						nodes = ((ForwardRequest)msg).getBootstrap();
					}
					
					if (nodes != null) {
						for (int i =0; i<nodes.size();i++) {
							KadBuckets.this.insert(kadNodeProvider.get().setNode(nodes.get(i)));
						}
					}
				}
			})
			.register();
	}

	private int getKBucketIndex(Key key) {
		return key.xor(localNode.getKey()).getFirstSetBitIndex();
	}
	
	private List<Node> getClosestNodes(Key k, int n, int index, Bucket[] buckets) {
		Set<Node> emptySet = Collections.emptySet();
		return getClosestNodes(k, n, index, buckets, emptySet);
	}
	
	private List<Node> getClosestNodes(Key k, int n, int index, Bucket[] buckets, Collection<Node> exclude) {
	
		final List<Node> $ = new ArrayList<Node>();
		final Set<Node> t = new HashSet<Node>();
		if (index < 0)
			index = 0;
		
		buckets[index].addNodesTo($);
		
		if ($.size() < n) {
			// look in other buckets
			for (int i=1; $.size() < n; ++i) {
				if (index + i < buckets.length) {
					buckets[index + i].addNodesTo(t);
					t.removeAll(exclude);
					$.addAll(t);
					t.clear();
				}
			
				if (0 <= index - i) {
					buckets[index - i].addNodesTo(t);
					t.removeAll(exclude);
					$.addAll(t);
					t.clear();
				}
				
				if (buckets.length <= index + i && index - i < 0)
					break;
			}
		}
		
		return $;
	}
	
	/**
	 * Inserts a node to the data structure
	 * The can be rejected, depending on the bucket policy
	 * @param node
	 */
	public void insert(KadNode node) {
		int i = getKBucketIndex(node.getNode().getKey());
		if (i == -1)
			return;
		
		kbuckets[i].insert(node);
	}
	
	/**
	 * 
	 * @return a list containing all the nodes in the data structure
	 */
	public List<Node> getAllNodes() {
		List<Node> $ = new ArrayList<Node>();
		for (int i=0; i < kbuckets.length; ++i) {
			kbuckets[i].addNodesTo($);
		}
		return $;
	}
	
	public void markAsDead(Node n) {
		int i = getKBucketIndex(n.getKey());
		if (i == -1)
			return;
		
		kbuckets[i].markDead(n);
	}
	
	/**
	 * Returns a single bucket's content. The bucket number is calculated
	 * using the given key according to its prefix with the local node's key
	 * as explained above.
	 * 
	 * @param k key to calculate the bucket from
	 * @return a list of nodes from a particular bucket
	 */
	public List<Node> getAllFromBucket(Key k) {
		int i = getKBucketIndex(k);
		if (i == -1)
			return Collections.emptyList();
		List<Node> $ = new ArrayList<Node>();
		kbuckets[i].addNodesTo($);
		return $;
	}
	
	/**
	 * Gets all nodes with keys closest to the given k.
	 * The size of the list will be MIN(n, total number of nodes in the data structure)
	 * @param k the key which the result's nodes are close to
	 * @param n the maximum number of nodes expected
	 * @return a list of nodes sorted by proximity to k
	 */
	public List<Node> getClosestNodesByKey(Key k, int n) {
		List<Node> $ = getClosestNodes(k, n, getKBucketIndex(k), kbuckets);
		if ($.isEmpty())
			return $;
		$ = sort($, on(Node.class).getKey(), new KeyComparator(k));
		if ($.size() > n)
			$.subList(n, $.size()).clear();
		return $;
	}
	
	/**
	 * Gets all nodes with keys closest to the given k.
	 * The size of the list will be MIN(n, total number of nodes in the data structure)
	 * @param k the key which the result's nodes are close to
	 * @param n the maximum number of nodes expected
	 * @return a list of nodes sorted by proximity to the given key's color
	 */
	public List<Node> getClosestNodesByColor(Key k, int n) {
		List<Node> $ = getClosestNodes(k, n, getKBucketIndex(k), kbuckets);
		if ($.isEmpty())
			return $;
		$ = sort($, on(Node.class).getKey(), new KeyColorComparator(k, nrColors));
		if ($.size() > n)
			$.subList(n, $.size()).clear();
		return $;
	}
	
	@Override
	public String toString() {
		String $ = "";
		for (int i=0; i < kbuckets.length; ++i)
			$ += kbuckets[i].toString()+"\n";
		return $;
	}
	
}
