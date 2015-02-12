package il.technion.ewolf.kbr.openkad.msg;

import java.io.Serializable;

import il.technion.ewolf.kbr.Node;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * A message containing arbitrary data to be used by the KeybasedRouting.sendRequest methods
 * @author eyal.kibbar@gmail.com
 *
 */
public class ContentRequest extends KadRequest {

	private static final long serialVersionUID = 918433377540165654L;

	private String tag;
	private Serializable content;
	
	@Inject
	ContentRequest(
			@Named("openkad.rnd.id") long id,
			@Named("openkad.local.node") Node src) {
		
		super(id, src);
	}

	public String getTag() {
		return tag;
	}
	
	public Serializable getContent() {
		return content;
	}
	
	public ContentRequest setContent(Serializable content) {
		this.content = content;
		return this;
	}
	
	public ContentRequest setTag(String tag) {
		this.tag = tag;
		return this;
	}
	
	
	@Override
	public ContentResponse generateResponse(Node localNode) {
		return new ContentResponse(getId(), localNode);
	}

	
	
}
