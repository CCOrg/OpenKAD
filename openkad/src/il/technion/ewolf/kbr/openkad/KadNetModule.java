package il.technion.ewolf.kbr.openkad;

import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.KeyFactory;
import il.technion.ewolf.kbr.KeybasedRouting;
import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.RandomKeyFactory;
import il.technion.ewolf.kbr.openkad.bucket.Bucket;
import il.technion.ewolf.kbr.openkad.bucket.KBuckets;
import il.technion.ewolf.kbr.openkad.bucket.KadBuckets;
import il.technion.ewolf.kbr.openkad.bucket.StableBucket;
import il.technion.ewolf.kbr.openkad.cache.DummyKadCache;
import il.technion.ewolf.kbr.openkad.cache.KadCache;
import il.technion.ewolf.kbr.openkad.handlers.FindNodeHandler;
import il.technion.ewolf.kbr.openkad.handlers.ForwardHandler;
import il.technion.ewolf.kbr.openkad.handlers.KademliaFindNodeHandler;
import il.technion.ewolf.kbr.openkad.handlers.PingHandler;
import il.technion.ewolf.kbr.openkad.handlers.StoreHandler;
import il.technion.ewolf.kbr.openkad.msg.ContentRequest;
import il.technion.ewolf.kbr.openkad.msg.FindNodeRequest;
import il.technion.ewolf.kbr.openkad.msg.ForwardRequest;
import il.technion.ewolf.kbr.openkad.msg.PingRequest;
import il.technion.ewolf.kbr.openkad.net.Communicator;
import il.technion.ewolf.kbr.openkad.net.JsonZippedKadSerializer;
import il.technion.ewolf.kbr.openkad.net.KadSerializer;
import il.technion.ewolf.kbr.openkad.net.KadServer;
import il.technion.ewolf.kbr.openkad.net.MessageDispatcher;
import il.technion.ewolf.kbr.openkad.op.EagerColorFindValueOperation;
import il.technion.ewolf.kbr.openkad.op.FindNodeOperation;
import il.technion.ewolf.kbr.openkad.op.FindValueOperation;
import il.technion.ewolf.kbr.openkad.op.JoinOperation;
import il.technion.ewolf.kbr.openkad.op.KadFindNodeOperation;
import il.technion.ewolf.kbr.openkad.op.KadLocalCacheFindValueOperation;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
public class KadNetModule extends AbstractModule {

	private final Properties properties;

	private Properties getDefaultProperties() {
		final Properties defaultProps = new Properties();

		// testing params, DONT TOUCH !!!
		defaultProps.setProperty("openkad.keyfactory.keysize", "20");
		defaultProps.setProperty("openkad.keyfactory.hashalgo", "SHA-256");
		defaultProps.setProperty("openkad.bucket.kbuckets.maxsize", "20");
		defaultProps.setProperty("openkad.color.nrcolors", "10");
		defaultProps.setProperty("openkad.scheme.name", "openkad.udp");

		// performance params

		// handling incoming messages
		defaultProps.setProperty("openkad.executors.server.nrthreads", "8");
		defaultProps.setProperty("openkad.executors.server.max_pending", "128");
		// handling registered callback
		defaultProps.setProperty("openkad.executors.client.nrthreads", "1");
		defaultProps.setProperty("openkad.executors.client.max_pending", "1");
		// forwarding find node requests
		defaultProps.setProperty("openkad.executors.forward.nrthreads", "2");
		defaultProps.setProperty("openkad.executors.forward.max_pending", "2");
		// executing the long find node operations
		defaultProps.setProperty("openkad.executors.op.nrthreads", "1");
		defaultProps.setProperty("openkad.executors.op.max_pending", "1");
		// sending back pings
		defaultProps.setProperty("openkad.executors.ping.nrthreads", "1");
		defaultProps.setProperty("openkad.executors.ping.max_pending", "16");
		// cache settings
		defaultProps.setProperty("openkad.cache.validtime", TimeUnit.HOURS.toMillis(10) + "");
		defaultProps.setProperty("openkad.cache.size", "100");
		defaultProps.setProperty("openkad.cache.share", "1");
		// minimum time between successive pings
		defaultProps.setProperty("openkad.bucket.valid_timespan", TimeUnit.HOURS.toMillis(24) + "");
		// network timeouts and concurrency level
		defaultProps.setProperty("openkad.net.concurrency", "3");
		defaultProps.setProperty("openkad.net.timeout", TimeUnit.SECONDS.toMillis(3) + "");
		defaultProps.setProperty("openkad.net.forwarded.timeout", TimeUnit.SECONDS.toMillis(30) + "");

		defaultProps.setProperty("openkad.color.candidates", "1");
		// interval between successive find node operations for refresh buckets
		defaultProps.setProperty("openkad.refresh.interval", TimeUnit.SECONDS.toMillis(30) + "");

		// local configuration, please touch
		defaultProps.setProperty("openkad.net.udp.port", "-1");
		defaultProps.setProperty("openkad.local.key", "");
		defaultProps.setProperty("openkad.file.nodes.path", "nodes");

		// misc
		defaultProps.setProperty("openkad.seed", "0");

		return defaultProps;
	}

	public KadNetModule() {
		this(new Properties());
	}

	public KadNetModule(final Properties properties) {
		this.properties = getDefaultProperties();
		this.properties.putAll(properties);
	}

	public KadNetModule setProperty(final String name, final String value) {
		this.properties.setProperty(name, value);
		return this;
	}

	@Override
	protected void configure() {
		Names.bindProperties(binder(), properties);

		bindTestingParams();

		bind(Timer.class).annotatedWith(Names.named("openkad.timer")).toInstance(new Timer());

		// bind requests
		bind(PingRequest.class);
		bind(FindNodeRequest.class);
		bind(ForwardRequest.class);
		bind(ContentRequest.class);

		bind(KadNode.class);
		// .to(UndeadKadNode.class);
		bind(KadBuckets.class).in(Scopes.SINGLETON);
		bind(KBuckets.class).to(KadBuckets.class).in(Scopes.SINGLETON);
		bind(NodeStorage.class).to(KadBuckets.class).in(Scopes.SINGLETON);

		bind(MessageDispatcher.class);
		// this line causes messages to be also compressed
		// JsonZippedKadSerializer - is the same without compressing. (takes CPU
		// resources).
		bind(KadSerializer.class).to(JsonZippedKadSerializer.class).in(Scopes.SINGLETON);
		bind(KadServer.class).in(Scopes.SINGLETON);
		bind(Communicator.class).to(KadServer.class).in(Scopes.SINGLETON);
		//
		// bind(KadCache.class)
		// .annotatedWith(Names.named("openkad.cache.stoppable.cache"))
		// .to(LRUKadCache.class)
		// .in(Scopes.SINGLETON);

		bind(KadCache.class).to(DummyKadCache.class)
		// .to(OptimalKadCache.class)
		// .to(LRUKadCache.class)
		// .to(VisionKadCache.class)

				// .to(LRUKadCache.class)

				// .to(LRUKadCache.class)
				// .to(StoppableCache.class)
				.in(Scopes.SINGLETON);
		// only for debug.
		// this.bind(genericLRUKadCache.class).to(GenericVisionKadCache.class);
		bind(JoinOperation.class);

		// bind(KadFindNodeOperation.class);
		bind(FindNodeOperation.class).to(KadFindNodeOperation.class);

		bind(FindNodeHandler.class)
		// .to(VisionFindNodeHandler.class);
				.to(KademliaFindNodeHandler.class);
		// bind(VisionFindNodeHandler.class);

		// bind handlers
		bind(PingHandler.class);
		bind(StoreHandler.class);
		bind(ForwardHandler.class);
		// bind(ResetableGuessingBloomFilter.class).in(Scopes.SINGLETON);

		bind(FindValueOperation.class).annotatedWith(Names.named("openkad.op.findvalue"))

		.to(KadLocalCacheFindValueOperation.class);

		// .to(KadLocalCacheFindValueOperation.class);
		// .to(KadCacheFindValueOperation.class);

		// .to(VisionEagerColorFindValueOperation.class);
		// .to(ForwardFindValueOperation.class);

		bind(FindValueOperation.class).annotatedWith(Names.named("openkad.op.lastFindValue"))
		// .to(VisionCacheFindValueOperation.class);
		// .to(VisionEagerColorFindValueOperation.class);
		// .to(KadCacheFindValueOperation.class);
				.to(EagerColorFindValueOperation.class);

		bind(BootstrapNodesSaver.class).in(Scopes.SINGLETON);
		bind(KeybasedRouting.class).to(KadNet.class).in(Scopes.SINGLETON);

	}
	@Provides
	@Named("openkad.bucket.kbuckets")
	Bucket provideKBucket(@Named("openkad.bucket.kbuckets.maxsize") final int maxSize,
			@Named("openkad.bucket.valid_timespan") final long validTimespan,
			@Named("openkad.executors.ping") final ExecutorService pingExecutor, final Provider<PingRequest> pingRequestProvider,
			final Provider<MessageDispatcher<Void>> msgDispatcherProvider) {
		return new StableBucket(maxSize, validTimespan, pingExecutor, pingRequestProvider, msgDispatcherProvider);
	}

	@Provides
	@Singleton
	@Named("openkad.rnd")
	Random provideRandom(@Named("openkad.seed") final long seed) {
		return seed == 0 ? new Random() : new Random(seed);
	}
	@Provides
	@Singleton
	KeyFactory provideKeyFactory(@Named("openkad.keyfactory.keysize") final int keyByteLength,
			@Named("openkad.rnd") final Random rnd, @Named("openkad.keyfactory.hashalgo") final String hashAlgo)
			throws NoSuchAlgorithmException {
		return new RandomKeyFactory(keyByteLength, rnd, hashAlgo);
	}

	@Provides
	@Named("openkad.net.expecters")
	@Singleton
	Set<MessageDispatcher<?>> provideExpectersSet() {
		return Collections.synchronizedSet(new HashSet<MessageDispatcher<?>>());
	}
	@Provides
	@Named("openkad.net.expecters.nonConsumable")
	@Singleton
	Set<MessageDispatcher<?>> provideNonConsumableExpectersSet() {
		return Collections.synchronizedSet(new HashSet<MessageDispatcher<?>>());
	}

	@Provides
	@Named("openkad.net.udp.sock")
	@Singleton
	DatagramSocket provideKadDatagramSocket(@Named("openkad.scheme.name") final String kadScheme,
			@Named("openkad.local.node") final Node localNode) throws SocketException {
		System.out.println("binding: " + localNode.getPort(kadScheme));
		return new DatagramSocket(localNode.getPort(kadScheme));
	}

	@Provides
	@Named("openkad.executors.server")
	@Singleton
	ExecutorService provideServerExecutor(@Named("openkad.executors.server.nrthreads") final int nrThreads,
			@Named("openkad.executors.server.max_pending") final int maxPending) {
		return new ThreadPoolExecutor(1, nrThreads, 5, TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(maxPending, true));
	}

	@Provides
	@Named("openkad.executors.ping")
	@Singleton
	ExecutorService providePingExecutor(@Named("openkad.executors.ping.nrthreads") final int nrThreads,
			@Named("openkad.executors.ping.max_pending") final int maxPending) {
		return new ThreadPoolExecutor(1, nrThreads, 5, TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(maxPending, true));
	}

	@Provides
	@Named("openkad.executors.forward")
	@Singleton
	ExecutorService provideColorExecutor(@Named("openkad.executors.forward.nrthreads") final int nrThreads,
			@Named("openkad.executors.forward.max_pending") final int maxPending) {
		return new ThreadPoolExecutor(1, nrThreads, 5, TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(maxPending, true));
	}

	@Provides
	@Named("openkad.executors.op")
	@Singleton
	ExecutorService provideOperationExecutor(@Named("openkad.executors.op.nrthreads") final int nrThreads,
			@Named("openkad.executors.op.max_pending") final int maxPending) {
		return new ThreadPoolExecutor(1, nrThreads, 5, TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(maxPending, true));
	}

	@Provides
	@Named("openkad.executors.client")
	@Singleton
	ExecutorService provideClientExecutor(@Named("openkad.executors.client.nrthreads") final int nrThreads,
			@Named("openkad.executors.client.max_pending") final int maxPending) {
		return new ThreadPoolExecutor(1, nrThreads, 5, TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(maxPending, true));
	}

	@Provides
	@Named("openkad.net.req_queue")
	@Singleton
	BlockingQueue<MessageDispatcher<?>> provideOutstandingRequestsQueue(@Named("openkad.net.concurrency") final int concurrency) {
		return new ArrayBlockingQueue<MessageDispatcher<?>>(concurrency, true);
	}

	@Provides
	@Named("openkad.rnd.id")
	long provideRandomId(@Named("openkad.rnd") final Random rnd) {
		return rnd.nextLong();
	}
	@Provides
	@Named("openkad.keys.zerokey")
	@Singleton
	Key provideZeroKey(final KeyFactory keyFactory) {
		return keyFactory.getZeroKey();
	}

	@Provides
	@Named("openkad.local.node")
	@Singleton
	Node provideLocalNode(@Named("openkad.scheme.name") final String kadScheme, @Named("openkad.net.udp.port") final int udpPort,
			@Named("openkad.local.key") final String base64Key, final KeyFactory keyFactory) throws UnknownHostException,
			IOException {

		final Key key = base64Key.isEmpty() ? keyFactory.generate() : keyFactory.get(base64Key);
		final Node n = new Node(key);

		n.setInetAddress(InetAddress.getByName("localhost"));
		n.addEndpoint(kadScheme, udpPort);

		return n;
	}
	@Provides
	@Named("openkad.net.buffer")
	@Singleton
	BlockingQueue<DatagramPacket> providePacketQueue() {
		return new ArrayBlockingQueue<DatagramPacket>(20);

	}
	@Provides
	@Named("openkad.net.sendbuffer")
	@Singleton
	BlockingQueue<DatagramPacket> provideSendPacketQueue() {
		return new ArrayBlockingQueue<DatagramPacket>(20);

	}

	@Provides
	@Named("openkad.refresh.task")
	@Singleton
	TimerTask provideRefreshTask(final Provider<KadFindNodeOperation> findNodeOperationProvider, final KeyFactory keyFactory) {

		return new TimerTask() {

			@Override
			public void run() {
				/*
				 * findNodeOperationProvider.get()
				 * .setKey(keyFactory.generate()) .doFindNode();
				 */
			}
		};
	}
	@Provides
	@Named("openkad.local.color")
	@Singleton
	int provideLocalColor(@Named("openkad.local.node") final Node localNode, @Named("openkad.color.nrcolors") final int nrColors) {
		return localNode.getKey().getColor(nrColors);
	}

	@Provides
	@Singleton
	@Named("openkad.file.nodes")
	File provideNodesFile(@Named("openkad.file.nodes.path") final String path) throws IOException {
		final File $ = new File(path);
		if (!$.exists())
			$.createNewFile();
		return $;
	}

	private void bindTestingParams() {

		// number of incoming messages
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrIncomingMessages")).toInstance(new AtomicInteger(0));

		// number of find nodes with wrong color
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrFindNodesWithWrongColor")).toInstance(
				new AtomicInteger(0));

		// number of handled forward requests
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrForwardHandling")).toInstance(new AtomicInteger(0));

		// number of handled forward requests from initiator
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrForwardHandlingFromInitiator")).toInstance(
				new AtomicInteger(0));

		// number of nacks recved
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrNacks")).toInstance(new AtomicInteger(0));

		// number of long timeouts
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrLongTimeouts")).toInstance(new AtomicInteger(0));

		// max number of hops until the result is found (or calculated)
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.maxHopsToResult")).toInstance(new AtomicInteger(0));

		// remote cache hits
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.remoteCacheHits")).toInstance(new AtomicInteger(0));

		// local cache hits
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.localCacheHits")).toInstance(new AtomicInteger(0));

		// number of hops histogram for all find node operations I caused
		// cache hits (find node hops = 0) will not be in here
		bind(new TypeLiteral<List<Integer>>() {
		}).annotatedWith(Names.named("openkad.testing.findNodeHopsHistogram")).toInstance(
				Collections.synchronizedList(new ArrayList<Integer>()));

		// number of hops histogram for all forward operations
		bind(new TypeLiteral<List<Integer>>() {
		}).annotatedWith(Names.named("openkad.testing.hopsToResultHistogram")).toInstance(
				Collections.synchronizedList(new ArrayList<Integer>()));

		// number of hits when requesting find node
		// instead of returning the correct K bucket, we simply return
		// the cached result
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrFindnodeHits")).toInstance(new AtomicInteger(0));

		// number of times we did not find anything in the cached results
		// for find node request and returned instead the right K bucjet
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrFindnodeMiss")).toInstance(new AtomicInteger(0));

		// number of local cache hits
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrLocalCacheHits")).toInstance(new AtomicInteger(0));

		// number of times the cache results was to short
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrShortCacheHits")).toInstance(new AtomicInteger(0));

		// number of times the cache of a remote machine had a hit
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrRemoteCacheHits")).toInstance(new AtomicInteger(0));

		// the max size of the optimal cache
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.optimalCacheMaxSize"))
				.toInstance(new AtomicInteger(0));

		// counts the number of incoming pings
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrIncomingPings")).toInstance(new AtomicInteger(0));

		// counts the number of outgoing pings
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrOutgoingPings")).toInstance(new AtomicInteger(0));

		// counts the number of short timeouts in the forward algo
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrShortForwardTimeouts")).toInstance(
				new AtomicInteger(0));

		// total amount of nacks sent
		bind(AtomicInteger.class).annotatedWith(Names.named("openkad.testing.nrNacksSent")).toInstance(new AtomicInteger(0));

		// total amount of bytes sent
		bind(AtomicLong.class).annotatedWith(Names.named("openkad.testing.nrBytesSent")).toInstance(new AtomicLong(0));

		// total amount of bytes recved
		bind(AtomicLong.class).annotatedWith(Names.named("openkad.testing.nrBytesRecved")).toInstance(new AtomicLong(0));
	}
}
