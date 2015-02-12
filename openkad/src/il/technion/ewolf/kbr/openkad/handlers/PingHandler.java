package il.technion.ewolf.kbr.openkad.handlers;

import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.msg.PingRequest;
import il.technion.ewolf.kbr.openkad.msg.PingResponse;
import il.technion.ewolf.kbr.openkad.net.Communicator;
import il.technion.ewolf.kbr.openkad.net.MessageDispatcher;
import il.technion.ewolf.kbr.openkad.net.filter.MessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.TypeMessageFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

/**
 * Handles ping requests by sending back a ping response
 * @author eyal.kibbar@gmail.com
 *
 */
public class PingHandler extends AbstractHandler {

	private final Communicator kadServer;
	private final Node localNode;
	private final AtomicInteger nrIncomingPings;
	
	@Inject
	PingHandler(
			Provider<MessageDispatcher<Void>> msgDispatcherProvider,
			Communicator kadServer,
			@Named("openkad.local.node") Node localNode,
			@Named("openkad.testing.nrIncomingPings") AtomicInteger nrIncomingPings) {
		super(msgDispatcherProvider);
		this.kadServer = kadServer;
		this.localNode = localNode;
		this.nrIncomingPings = nrIncomingPings;
	}

	@Override
	public void completed(KadMessage msg, Void attachment) {
		nrIncomingPings.incrementAndGet();
		PingResponse pingResponse = ((PingRequest)msg).generateResponse(localNode);
		
		try {
			kadServer.send(msg.getSrc(), pingResponse);
		} catch (IOException e) {
			// nothing to do
			e.printStackTrace();
		}
	}

	@Override
	public void failed(Throwable exc, Void attachment) {
		// should never b here
	}

	@Override
	protected Collection<MessageFilter> getFilters() {
		return Arrays.asList(new MessageFilter[] {
				new TypeMessageFilter(PingRequest.class)
		});
	}

}
