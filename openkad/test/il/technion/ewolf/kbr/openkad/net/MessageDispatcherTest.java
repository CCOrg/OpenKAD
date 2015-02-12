package il.technion.ewolf.kbr.openkad.net;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.concurrent.CompletionHandler;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.msg.KadRequest;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

public class MessageDispatcherTest {

	private BlockingQueue<MessageDispatcher<?>> mockedOutstandingRequests;
	private Set<MessageDispatcher<?>> mockedExpecters;
	private Set<MessageDispatcher<?>> mockedNonConsumableExpecters;
	private Timer mockedTimer;
	private Communicator mockedKadServer;
	private Node mockedNode;
	private KadRequest mockedRequest;
	private long timeout;
	private MessageDispatcher<Object> dispatcher;
	
	@Before
	public void setup() {
		mockedOutstandingRequests = spy(new ArrayBlockingQueue<MessageDispatcher<?>>(10));
		mockedExpecters = spy(new HashSet<MessageDispatcher<?>>());
		mockedNonConsumableExpecters = spy(new HashSet<MessageDispatcher<?>>());
		mockedTimer = spy(new Timer());
		mockedKadServer = mock(Communicator.class);
		mockedNode = mock(Node.class);
		mockedRequest = mock(KadRequest.class);
		timeout = 100;
		
		dispatcher = new MessageDispatcher<Object>(
				mockedOutstandingRequests,
				mockedExpecters,
				mockedNonConsumableExpecters,
				mockedTimer,
				timeout,
				mockedKadServer);
	}
	
	@Test
	public void itShouldInsertItselfToOutstandingRequestsWhenSent() throws Exception {
		
		dispatcher
			.setConsumable(true)
			.send(mockedNode, mockedRequest);
		
		verify(mockedOutstandingRequests).put(dispatcher);
		verify(mockedKadServer, times(1)).send(mockedNode, mockedRequest);
	}
	
	@Test
	public void itShouldRemoveItselfFromOutstandingRequestsAndExpectersWhenDone() throws Exception {
		dispatcher
			.setConsumable(true)
			.send(mockedNode, mockedRequest);
		
		KadMessage msg = mock(KadMessage.class);
		
		dispatcher.handle(msg);
		
		verify(mockedOutstandingRequests).remove(dispatcher);
		verify(mockedExpecters).remove(dispatcher);
	}
	
	@Test
	public void itShouldNotRemoveItselfFromExpectersWhenNonConsumableAndRegistered() throws Exception {
		dispatcher
			.setConsumable(false)
			.register();
		
		KadMessage msg = mock(KadMessage.class);
		
		dispatcher.handle(msg);
		
		verify(mockedOutstandingRequests, never()).put(dispatcher);
		verify(mockedExpecters, never()).remove(dispatcher);
		verify(mockedNonConsumableExpecters, never()).remove(dispatcher);
	}
	
	@Test
	public void itShouldRemoveIteselfFromExpectersWhenConsumableAndRegistered() throws Exception {
		dispatcher
			.setConsumable(true)
			.register();
	
		KadMessage msg = mock(KadMessage.class);
		
		dispatcher.handle(msg);
		
		verify(mockedOutstandingRequests, never()).put(dispatcher);
		verify(mockedExpecters, times(1)).remove(dispatcher);
	}
	
	@Test
	public void itShouldInvokeCallbackAfterHandle() throws Exception {
		final KadMessage mockedMsg = mock(KadMessage.class);
		final AtomicBoolean hasVisited = new AtomicBoolean(false);
		dispatcher
			.setConsumable(true)
			.setCallback(null, new CompletionHandler<KadMessage, Object>() {
				
				@Override
				public void failed(Throwable exc, Object attachment) {
					Assert.assertFalse("should never be here", true);
				}
				
				@Override
				public void completed(KadMessage msg, Object attachment) {
					Assert.assertTrue(mockedMsg == msg);
					hasVisited.set(true);
				}
			})
			.send(mockedNode, mockedRequest);
		
		dispatcher.handle(mockedMsg);
		
		Assert.assertTrue(hasVisited.get());
	}
	
	@Test
	public void itShouldTimedoutAfterSendWithoutHandle() throws Exception {
		final KadMessage mockedMsg = mock(KadMessage.class);
		final AtomicBoolean hasVisited = new AtomicBoolean(false);
		dispatcher
			.setConsumable(true)
			.setCallback(null, new CompletionHandler<KadMessage, Object>() {
				
				@Override
				public void failed(Throwable exc, Object attachment) {
					hasVisited.set(true);
				}
				
				@Override
				public void completed(KadMessage msg, Object attachment) {
					Assert.assertFalse("should never be here", true);
				}
			})
			.send(mockedNode, mockedRequest);
		
		Thread.sleep(timeout+100);
		
		dispatcher.handle(mockedMsg);
		
		Assert.assertTrue(hasVisited.get());
	}
	
	@Test
	public void itShouldWaitUntilHandleWasInvokedWhenSendAndWait() throws Throwable {
		
		final KadMessage mockedMsg = mock(KadMessage.class);
		
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					Thread.sleep(timeout/2);
				} catch (InterruptedException e) {
				}
				dispatcher.handle(mockedMsg);
			}
		}).start();
		
		KadMessage recvedMessage = dispatcher
			.setConsumable(true)
			.futureSend(mockedNode, mockedRequest).get();
		
		Assert.assertTrue(recvedMessage == mockedMsg);
	}
	
	@Test(expected=ExecutionException.class)
	public void itShouldTimedoutWhenSendAndWait() throws Throwable {
		
		dispatcher
			.setConsumable(true)
			.futureSend(mockedNode, mockedRequest).get();
		
	}
	@Test
	public void itShouldWaitUntilMessageIsRecvedWhenExpecting() throws Throwable {
		Future<KadMessage> f = dispatcher
			.setConsumable(true)
			.futureRegister();
		
		dispatcher.handle(mockedRequest);
		
		Assert.assertEquals(mockedRequest, f.get());
	}
	
	@Test(expected=ExecutionException.class)
	public void itShouldTimedoutWhenExpectingAndWaiting() throws Throwable {
		Future<KadMessage> f = dispatcher
			.setConsumable(true)
			.futureRegister();
		
		
		f.get();
	}
}
