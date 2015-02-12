package il.technion.ewolf.kbr.openkad;

import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.KeyFactory;
import il.technion.ewolf.kbr.KeybasedRouting;
import il.technion.ewolf.kbr.MessageHandler;
import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.concurrent.CompletionHandler;
import il.technion.ewolf.kbr.concurrent.FutureTransformer;
import il.technion.ewolf.kbr.openkad.handlers.FindNodeHandler;
import il.technion.ewolf.kbr.openkad.handlers.ForwardHandler;
import il.technion.ewolf.kbr.openkad.handlers.PingHandler;
import il.technion.ewolf.kbr.openkad.handlers.StoreHandler;
import il.technion.ewolf.kbr.openkad.msg.ContentMessage;
import il.technion.ewolf.kbr.openkad.msg.ContentRequest;
import il.technion.ewolf.kbr.openkad.msg.ContentResponse;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.net.Communicator;
import il.technion.ewolf.kbr.openkad.net.MessageDispatcher;
import il.technion.ewolf.kbr.openkad.net.filter.IdMessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.TagMessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.TypeMessageFilter;
import il.technion.ewolf.kbr.openkad.op.FindValueOperation;
import il.technion.ewolf.kbr.openkad.op.JoinOperation;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.name.Named;

public class KadNet implements KeybasedRouting {

	// dependencies
	private final Provider<MessageDispatcher<Object>> msgDispatcherProvider;
	private final Provider<JoinOperation> joinOperationProvider;
	private final Provider<ContentRequest> contentRequestProvider;
	private final Provider<ContentMessage> contentMessageProvider;
	private final Provider<IncomingContentHandler<Object>> incomingContentHandlerProvider;
	private final Provider<FindValueOperation> findValueOperationProvider;
	private final Provider<FindNodeHandler> findNodeHandlerProvider;
	private final Provider<PingHandler> pingHandler;
	private final Provider<StoreHandler> storeHandlerProvider;
	private final Provider<ForwardHandler> forwardHandlerProvider;
	
	private final Node localNode;
	private final Communicator kadServer;
	private final NodeStorage nodeStorage;
	private final KeyFactory keyFactory;
	private final ExecutorService clientExecutor;
	private final int bucketSize;
	private final TimerTask refreshTask;
	private final BootstrapNodesSaver bootstrapNodesSaver;
	
	// testing
	private final List<Integer> findNodeHopsHistogram;
	
	// state
	private final Map<String, MessageDispatcher<?>> dispatcherFromTag = new HashMap<String, MessageDispatcher<?>>();
	private Thread kadServerThread = null;
	@Inject
	protected KadNet(
			Provider<MessageDispatcher<Object>> msgDispatcherProvider,
			Provider<JoinOperation> joinOperationProvider,
			Provider<ContentRequest> contentRequestProvider,
			Provider<ContentMessage> contentMessageProvider,
			Provider<IncomingContentHandler<Object>> incomingContentHandlerProvider,
			@Named("openkad.op.findvalue") Provider<FindValueOperation> findValueOperationProvider,
			Provider<FindNodeHandler> findNodeHandlerProvider,
			Provider<PingHandler> pingHandler,
			Provider<StoreHandler> storeHandlerProvider,
			Provider<ForwardHandler> forwardHandlerProvider,
			
			@Named("openkad.local.node") Node localNode,
			Communicator kadServer,
			NodeStorage nodeStorage,
			KeyFactory keyFactory,
			@Named("openkad.executors.client") ExecutorService clientExecutor,
			@Named("openkad.bucket.kbuckets.maxsize") int bucketSize,
			@Named("openkad.refresh.task") TimerTask refreshTask,
			BootstrapNodesSaver bootstrapNodesSaver,
			
			//testing
			@Named("openkad.testing.findNodeHopsHistogram") List<Integer> findNodeHopsHistogram) {
		
		
		this.msgDispatcherProvider = msgDispatcherProvider;
		this.joinOperationProvider = joinOperationProvider;
		this.contentRequestProvider = contentRequestProvider;
		this.contentMessageProvider = contentMessageProvider;
		this.incomingContentHandlerProvider = incomingContentHandlerProvider;
		this.findValueOperationProvider = findValueOperationProvider;
		this.findNodeHandlerProvider = findNodeHandlerProvider;
		this.pingHandler = pingHandler;
		this.storeHandlerProvider = storeHandlerProvider;
		this.forwardHandlerProvider = forwardHandlerProvider;
		
		this.localNode = localNode;
		this.kadServer = kadServer;
		this.nodeStorage = nodeStorage;
		this.keyFactory = keyFactory;
		this.clientExecutor = clientExecutor;
		this.bucketSize = bucketSize;
		this.refreshTask = refreshTask;
		this.bootstrapNodesSaver = bootstrapNodesSaver;
		
		//testing
		this.findNodeHopsHistogram = findNodeHopsHistogram;
	}
	
	
	@Override
	public void create() throws IOException {
		// bind communicator and register all handlers
		kadServer.bind();
		pingHandler.get().register();
		findNodeHandlerProvider.get().register();
		storeHandlerProvider.get().register();
		forwardHandlerProvider.get().register();
		
		nodeStorage.registerIncomingMessageHandler();
		kadServerThread = new Thread(kadServer);
		kadServerThread.start();
		
		bootstrapNodesSaver.load();
		bootstrapNodesSaver.start();
	}

	@Override
	public void join(Collection<URI> bootstraps) {
		joinOperationProvider.get()
			.addBootstrap(bootstraps)
			.doJoin();
	}

	
	@Override
	public List<Node> findNode(Key k) {
		FindValueOperation op = findValueOperationProvider.get()
				.setKey(k);
			
		List<Node> result = op.doFindValue();
		findNodeHopsHistogram.add(op.getNrQueried());
		
		List<Node> $ = new ArrayList<Node>(result);
		
		if ($.size() > bucketSize)
			$.subList(bucketSize, $.size()).clear();
		
		//System.out.println(op.getNrQueried());
		
		return result;
	}

	@Override
	public KeyFactory getKeyFactory() {
		return keyFactory;
	}

	@Override
	public List<Node> getNeighbours() {
		return nodeStorage.getAllNodes();
	}
	
	@Override
	public Node getLocalNode() {
		return localNode;
	}
	
	@Override
	public String toString() {
		return localNode.toString()+"\n"+nodeStorage.toString();
	}

	@Override
	public synchronized void register(String tag, MessageHandler handler) {
		MessageDispatcher<?> dispatcher = dispatcherFromTag.get(tag);
		if (dispatcher != null)
			dispatcher.cancel(new CancellationException());
		
		dispatcher = msgDispatcherProvider.get()
			.addFilter(new TagMessageFilter(tag))
			.setConsumable(false)
			.setCallback(null, incomingContentHandlerProvider.get()
				.setHandler(handler)
				.setTag(tag))
			.register();
		
		dispatcherFromTag.put(tag, dispatcher);	
	}

	@Override
	public void sendMessage(Node to, String tag, Serializable msg) throws IOException {
		kadServer.send(to, contentMessageProvider.get()
			.setTag(tag)
			.setContent(msg));
	}
	
	@Override
	public Future<Serializable> sendRequest(Node to, String tag, Serializable msg) {
		
		ContentRequest contentRequest = contentRequestProvider.get()
			.setTag(tag)
			.setContent(msg);
		
		Future<KadMessage> futureSend = msgDispatcherProvider.get()
			.setConsumable(true)
			.addFilter(new TypeMessageFilter(ContentResponse.class))
			.addFilter(new IdMessageFilter(contentRequest.getId()))
			.futureSend(to, contentRequest);
		
		return new FutureTransformer<KadMessage, Serializable>(futureSend) {
			@Override
			protected Serializable transform(KadMessage msg) throws Throwable {
				return ((ContentResponse)msg).getContent();
			}
		};
	}
	
	@Override
	public <A> void sendRequest(Node to, String tag, Serializable msg, final A attachment, final CompletionHandler<Serializable, A> handler) {
		ContentRequest contentRequest = contentRequestProvider.get()
			.setTag(tag)
			.setContent(msg);
			
		msgDispatcherProvider.get()
			.setConsumable(true)
			.addFilter(new TypeMessageFilter(ContentResponse.class))
			.addFilter(new IdMessageFilter(contentRequest.getId()))
			.setCallback(null, new CompletionHandler<KadMessage, Object>() {
				@Override
				public void completed(KadMessage msg, Object nothing) {
					final ContentResponse contentResponse = (ContentResponse)msg;
					clientExecutor.execute(new Runnable() {
						@Override
						public void run() {
							handler.completed(contentResponse.getContent(), attachment);
						}
					});
				}
				
				@Override
				public void failed(Throwable exc, Object nothing) {
					handler.failed(exc, attachment);
				}
			})
			.send(to, contentRequest);
	}

	
	
	public static void main(String[] args) throws Exception {
		Injector injector = Guice.createInjector(new KadNetModule()
			.setProperty("openkad.net.udp.port", "5555"));
		KeybasedRouting kbr = injector.getInstance(KeybasedRouting.class);
		kbr.create();
	}
	
	@Override
	public void shutdown() {
		try {
			bootstrapNodesSaver.saveNow();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		refreshTask.cancel();
		kadServer.shutdown(kadServerThread);
	}
}
