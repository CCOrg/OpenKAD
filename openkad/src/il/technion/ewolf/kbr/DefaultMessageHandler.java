package il.technion.ewolf.kbr;

import java.io.Serializable;

/**
 * A default implementation that does nothing for {@link MessageHandler}
 * @author eyal.kibbar@gmail.com
 */
public class DefaultMessageHandler implements MessageHandler {

	@Override
	public void onIncomingMessage(Node from, String tag, Serializable content) {
	}

	@Override
	public Serializable onIncomingRequest(Node from, String tag, Serializable content) {
		return null;
	}

}
