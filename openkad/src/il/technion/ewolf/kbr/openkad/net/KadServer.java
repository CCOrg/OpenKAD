package il.technion.ewolf.kbr.openkad.net;

import static ch.lambdaj.Lambda.filter;
import static ch.lambdaj.Lambda.having;
import static ch.lambdaj.Lambda.on;
import static org.hamcrest.Matchers.is;
import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.msg.PingRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

/**
 * Low level communication handler. This class does all the serialze/de-serialze
 * and socket programming.
 * 
 * @author eyal.kibbar@gmail.com
 * 
 */
public class KadServer implements Communicator {

	// dependencies
	private final KadSerializer serializer;
	private final Provider<DatagramSocket> sockProvider;
	private final BlockingQueue<DatagramPacket> pkts;
	private final ExecutorService srvExecutor;
	private final Set<MessageDispatcher<?>> expecters;
	private final Set<MessageDispatcher<?>> nonConsumableExpecters;
	private final String kadScheme;

	// testing
	private final AtomicInteger nrOutgoingPings;
	private final AtomicInteger nrIncomingMessages;
	private final AtomicLong nrBytesSent;
	private final AtomicLong nrBytesRecved;

	// state
	private final AtomicBoolean isActive = new AtomicBoolean(false);
	// private final BlockingQueue<DatagramPacket> pktsout;

	@Inject
	KadServer(
			final KadSerializer serializer,
			@Named("openkad.scheme.name") final String kadScheme,
			@Named("openkad.net.udp.sock") final Provider<DatagramSocket> sockProvider,
			@Named("openkad.net.buffer") final BlockingQueue<DatagramPacket> pkts,
			@Named("openkad.net.sendbuffer") final BlockingQueue<DatagramPacket> pktsout,
			@Named("openkad.executors.server") final ExecutorService srvExecutor,
			@Named("openkad.net.expecters") final Set<MessageDispatcher<?>> expecters,
			@Named("openkad.net.expecters.nonConsumable") final Set<MessageDispatcher<?>> nonConsumableExpecters,

			// testing
			@Named("openkad.testing.nrOutgoingPings") final AtomicInteger nrOutgoingPings,
			@Named("openkad.testing.nrIncomingMessages") final AtomicInteger nrIncomingMessages,
			@Named("openkad.testing.nrBytesSent") final AtomicLong nrBytesSent,
			@Named("openkad.testing.nrBytesRecved") final AtomicLong nrBytesRecved) {

		this.kadScheme = kadScheme;
		this.serializer = serializer;
		this.sockProvider = sockProvider;
		this.pkts = pkts;
		// this.pktsout = pktsout;
		this.srvExecutor = srvExecutor;
		this.expecters = expecters;
		this.nonConsumableExpecters = nonConsumableExpecters;

		this.nrOutgoingPings = nrOutgoingPings;
		this.nrIncomingMessages = nrIncomingMessages;
		this.nrBytesSent = nrBytesSent;
		this.nrBytesRecved = nrBytesRecved;
	}

	/**
	 * Binds the socket
	 */
	@Override
	public void bind() {
		this.sockProvider.get();
	}

	/**
	 * Sends a message
	 * 
	 * @param to
	 *            the destination node
	 * @param msg
	 *            the message to be sent
	 * @throws IOException
	 *             any socket exception
	 */
	@Override
	public void send(final Node to, final KadMessage msg) throws IOException {
		// System.out.println("KadServer: send: " + msg + " to: " +
		// to.getKey());

		if (msg instanceof PingRequest)
			this.nrOutgoingPings.incrementAndGet();

		ByteArrayOutputStream bout = null;

		try {
			bout = new ByteArrayOutputStream();
			this.serializer.write(msg, bout);
			// here is the memory allocated.
			final byte[] bytes = bout.toByteArray();
			this.nrBytesSent.addAndGet(bytes.length);

			final DatagramPacket pkt = new DatagramPacket(bytes, 0, bytes.length);

			pkt.setSocketAddress(to.getSocketAddress(this.kadScheme));
			this.sockProvider.get().send(pkt);

		} finally {
			try {

				bout.close();
				bout = null;

			} catch (final Exception e) {
			}
		}
	}
	private List<MessageDispatcher<?>> extractShouldHandle(final KadMessage msg) {
		List<MessageDispatcher<?>> shouldHandle = Collections.emptyList();
		List<MessageDispatcher<?>> nonConsumableShouldHandle = Collections.emptyList();
		final List<MessageDispatcher<?>> $ = new ArrayList<MessageDispatcher<?>>();
		synchronized (this.expecters) {
			if (!this.expecters.isEmpty())
				shouldHandle = filter(having(on(MessageDispatcher.class).shouldHandleMessage(msg), is(true)), this.expecters);
		}

		synchronized (this.nonConsumableExpecters) {
			if (!this.nonConsumableExpecters.isEmpty())
				nonConsumableShouldHandle = filter(having(on(MessageDispatcher.class).shouldHandleMessage(msg), is(true)),
						this.nonConsumableExpecters);
		}

		$.addAll(nonConsumableShouldHandle);
		$.addAll(shouldHandle);
		return $;
	}

	private void handleIncomingPacket(final DatagramPacket pkt) {
		this.nrIncomingMessages.incrementAndGet();
		this.nrBytesRecved.addAndGet(pkt.getLength());
		this.srvExecutor.execute(new Runnable() {

			@Override
			public void run() {
				ByteArrayInputStream bin = null;
				KadMessage msg = null;
				try {
					bin = new ByteArrayInputStream(pkt.getData(), pkt.getOffset(), pkt.getLength());
					msg = KadServer.this.serializer.read(bin);

					// System.out.println("KadServer: handleIncomingPacket: " +
					// msg + " from: " + msg.getSrc().getKey());

					// fix incoming src address
					msg.getSrc().setInetAddress(pkt.getAddress());
				} catch (final Exception e) {
					e.printStackTrace();
					return;
				} finally {
					try {
						bin.close();
					} catch (final Exception e) {
					}
					KadServer.this.pkts.offer(pkt);
				}

				// call all the expecters
				final List<MessageDispatcher<?>> shouldHandle = extractShouldHandle(msg);

				for (final MessageDispatcher<?> m : shouldHandle)
					try {
						m.handle(msg);
					} catch (final Exception e) {
						// handle fail should not interrupt other handlers
						e.printStackTrace();
					}
			}
		});
	}

	/**
	 * The server loop: 1. accept a message from socket 2. parse message 3.
	 * handle the message in a thread pool
	 */
	@Override
	public void run() {
		this.isActive.set(true);
		while (this.isActive.get()) {
			DatagramPacket pkt = null;
			try {
				pkt = this.pkts.poll();
				if (pkt == null)
					pkt = new DatagramPacket(new byte[1024 * 64], 1024 * 64);

				this.sockProvider.get().receive(pkt);
				handleIncomingPacket(pkt);

			} catch (final Exception e) {
				// insert the taken pkt back
				if (pkt != null)
					this.pkts.offer(pkt);

				e.printStackTrace();
			}

		}
	}

	/**
	 * Shutdown the server and closes the socket
	 * 
	 * @param kadServerThread
	 */
	@Override
	public void shutdown(final Thread kadServerThread) {
		this.isActive.set(false);
		this.sockProvider.get().close();
		kadServerThread.interrupt();
		try {
			kadServerThread.join();
		} catch (final InterruptedException e) {
		}
	}

}
