/**
This file is part of openkad.

openkad is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

openkad is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with openkad.  If not, see <http://www.gnu.org/licenses/>.

**/

package il.technion.ewolf.kbr;

import il.technion.ewolf.kbr.concurrent.CompletionHandler;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;

/**
 * This is the main class of openkad
 * It defines the api of any Keybased Routing network.
 * 
 * @author eyal kibbar (eyal.kibbar@gmail.com)
 */
public interface KeybasedRouting {

	/**
	 * Binds the sockets and create a singleton network
	 * @throws IOException
	 */
	public void create() throws IOException;
	
	/**
	 * After we have a singleton network we can expand it using join.
	 * Note that joining another singleton network will merge the 2 singleton
	 * into one new network with 2 nodes in it.
	 * 
	 * URI format must be: openkad.udp://[ip of known node]:[port of that node]/
	 * 
	 * @param bootstraps all the known nodes in the other network
	 * 
	 * @throws IllegalStateException if all bootstrap nodes did not answer
	 */
	public void join(Collection<URI> bootstraps);
	
	/**
	 * Finds nodes with keys closest to the given key (using XOR metric).
	 * The list may be any size between 1 and openkad.bucket.kbuckets.maxsize
	 *  
	 * @param k the desired key
	 * @return nodes with keys close to k
	 */
	public List<Node> findNode(Key k);
	
	/**
	 * Registers a {@link MessageHandler} for receiving messages sent with a particular tag.
	 * All incoming messages and requests sent with the given tag will cause
	 * handler.onIncomingMessage or handler.inIncomingRequest invocation.
	 * 
	 * The thread pool used to execute this invocation is
	 * openkad.executors.client
	 * 
	 * Invoking register with the same tag more than once will remove the previous handler
	 * and use only the new handler
	 * 
	 * @param tag the sent messages tag
	 * @param handler the handler used for the incoming message.
	 */
	public void register(String tag, MessageHandler handler);
	
	/**
	 * Sends any serializable object to the destination node.
	 * The destination node must register a {@link MessageHandler} for the same tag if it wants
	 * to receive the sent message.
	 * This method is not designed for sending large object and does not guarantee reliability. Thus,
	 * use it to send small messages (like "please connect to me in this (ip, port)").
	 * 
	 * Note: the destination node must be created using {@link findeNode} method. Although it can
	 * be transfered using sendMessage or sendRequest to another node and used there.
	 * 
	 * @param to the destination node
	 * @param tag message tag to be used in the destination node for invoking the correct handler
	 * @param msg any arbitrary object
	 * @throws IOException failed to send due to some socket error
	 */
	public void sendMessage(Node to, String tag, Serializable msg) throws IOException;
	
	/**
	 * Sends a message to the destination node and expects a response. The response is whatever
	 * the destination's node {@link MessageHandler.onIncomingRequest} has returned.
	 * 
	 * This method is blocking ! the maximum number of outstanding requests (requests which the corresponding
	 * responses haven't arrived yet) is openkad.net.concurrency. Thus, if you try to send more than this number
	 * sendRequest will block until some responses are received (or timed out).
	 * 
	 * The response can be retrieved using Future.get() method
	 * 
	 * Important: if you send messages to yourself (destination node = local node) than make sure
	 * the concurrency factor (openkad.net.concurrency) is smaller than the number of client threads
	 * (openkad.executors.client), otherwise you can have a deadlock.
	 * 
	 * @param to the destination node
	 * @param tag message tag to be used in the destination node for invoking the correct handler
	 * @param msg any arbitrary object
	 * @return a future with the response as return by the destination node's {@link MessageHandler.onIncomingRequest} method
	 */
	public Future<Serializable> sendRequest(Node to, String tag, Serializable msg);
	
	/**
	 * Sends a message to the destination node and expects a response. The response is whatever
	 * the destination's node {@link MessageHandler.onIncomingRequest} has returned.
	 * 
	 * This method is blocking ! the maximum number of outstanding requests (requests which the corresponding
	 * responses haven't arrived yet) is openkad.net.concurrency. Thus, if you try to send more than this number
	 * sendRequest will block until some responses are received (or timed out).
	 * 
	 * The response will be received by calling the given handler completed method.
	 * It is guaranteed that either handler.completed or handler.failed will be called after
	 * the timeout defined in openkad.net.timeout 
	 * 
	 * 
	 * @param to the destination node
	 * @param tag message tag to be used in the destination node for invoking the correct handler
	 * @param msg any arbitrary object
	 * @param attachment the argument to be given to the handler when invoked
	 * @param handler the handler to be invoked upon completion
	 */
	public <A> void sendRequest(Node to, String tag, Serializable msg, A attachment, CompletionHandler<Serializable, A> handler);
	
	/**
	 * @return the keyFactory used for this Keybased Routing
	 */
	public KeyFactory getKeyFactory();
	
	/**
	 * A list of all known nodes. Changing the returned list will have no affect on the Key Based Routing
	 * @return
	 */
	public List<Node> getNeighbours();
	
	/**
	 * 
	 * @return the local node associated with this Key Based Routing. 
	 */
	public Node getLocalNode();
	
	/**
	 * Closes the sockets and shutdown all the thread pools
	 */
	public void shutdown();
}
