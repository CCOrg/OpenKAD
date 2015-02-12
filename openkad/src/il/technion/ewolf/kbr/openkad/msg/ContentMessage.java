package il.technion.ewolf.kbr.openkad.msg;

import il.technion.ewolf.kbr.Node;

import java.io.Serializable;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * A message containing arbitrary data to be used by the KeybasedRouting.sendMessage method
 * @author eyal.kibbar@gmail.com
 *
 */
public class ContentMessage extends KadMessage {

	private static final long serialVersionUID = -57547778613163861L;
	
	private String tag;
	private Serializable content;
	
	@Inject
	ContentMessage(
			@Named("openkad.rnd.id") long id,
			@Named("openkad.local.node") Node src) {
		super(id, src);
	}

	/**
	 * Every content request has a tag associated with it.
	 * This is the same tag given in the KeybasedRouting.sendMessage or sendRequest methods.
	 * 
	 * @return the message's tag
	 */
	public String getTag() {
		return tag;
	}
	
	/**
	 * Any arbitrary data
	 * @return the data
	 */
	public Serializable getContent() {
		return content;
	}
	public ContentMessage setContent(Serializable content) {
		this.content = content;
		return this;
	}
	
	public ContentMessage setTag(String tag) {
		this.tag = tag;
		return this;
	}

}
