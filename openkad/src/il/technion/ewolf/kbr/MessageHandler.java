package il.technion.ewolf.kbr;

import java.io.Serializable;

/**
 * Used as an interface for the KeybasedRouting register method
 * @author eyal.kibbar@gmail.com
 */
public interface MessageHandler {

	/**
	 * Will be invoked when after registered in KeybasedRouting.register and a message with the
	 * registered tag has arrived using the remote node KeybasedRouting.sendMessage method
	 * @param from the sender node
	 * @param tag the arrived message tag
	 * @param content the sent object
	 */
	void onIncomingMessage(Node from, String tag, Serializable content);
	
	/**
	 * Will be invoked when after registered in KeybasedRouting.register and a message with the
	 * registered tag has arrived using the remote node KeybasedRouting.sendRequest method
	 * @param from the sender node
	 * @param tag the arrived message tag
	 * @param content the sent object
	 * @return an object to be sent back to the sender
	 */
	Serializable onIncomingRequest(Node from, String tag, Serializable content);
}
