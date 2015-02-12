package il.technion.ewolf.kbr;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents contact information about a node in the network.
 * This class is serializable and can be sent/saved for other/later use by
 * any KeybasedRouting local or remote.
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public class Node implements Serializable,Comparable<Node> {

	private static final long serialVersionUID = 2520444508318328765L;
	
	private final Key key;
	private InetAddress addr = null;
	private Map<String, Integer> portFromScheme = new HashMap<String, Integer>();
	
	// dummy node
	/**
	 * Creates a dummy node with no key in it
	 */
	public Node() {
		this(null);
	}
	
	/**
	 * Create a node with only a key and no IP address
	 * @param key
	 */
	public Node(Key key) {
		this.key = key;
	}
	
	/**
	 * 
	 * @return the node's key
	 */
	public Key getKey() {
		return key;
	}
	
	/**
	 * The endpoints map is a map containing all the nodes available protocols as keys
	 * and the protocol's port as values
	 *  
	 * @return node's protocols
	 */
	public Map<String, Integer> getAllEndpoints() {
		return portFromScheme;
	}
	
	/**
	 * Creates a uri from a given protocol name
	 * @param scheme the protocol name (such as http or openkad.udp)
	 * @return the uri
	 */
	public URI getURI(String scheme) {
		try {
			return new URI(scheme+"://"+addr.getHostAddress()+":"+getPort(scheme)+"/"+key.toBase64());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Creates a uri list of all the node's protocols
	 * @return a uri list
	 */
	public List<URI> toURIs() {
		List<String> schemes = new ArrayList<String>(portFromScheme.keySet());
		Collections.sort(schemes);
		List<URI> $ = new ArrayList<URI>();
		for (String scheme : schemes)
			$.add(getURI(scheme));
		return $;
	}
	
	/**
	 * Creates a SocketAddress from a protocol name
	 * @param scheme the protocol name
	 * @return
	 */
	public SocketAddress getSocketAddress(String scheme) {
		return new InetSocketAddress(addr, getPort(scheme));
	}
	
	/**
	 * Append a new protocol for this node.
	 * Use this method to add your own protocol support (such as http support). Make sure
	 * you add the new protocol BEFORE joining a network
	 * @param scheme the protocol name
	 * @param port the protocol port
	 */
	public void addEndpoint(String scheme, int port) {
		portFromScheme.put(scheme, port);
	}
	
	/**
	 * 
	 * @return the IP address of this node
	 */
	public InetAddress getInetAddress() {
		return addr;
	}
	
	
	/**
	 * Sets the IP address of this node
	 * @param addr
	 */
	public void setInetAddress(InetAddress addr) {
		this.addr = addr;
	}
	
	/**
	 * Get the port number of a given protocol name.
	 * May throw a NullPointerException if protocol was not added
	 * @param scheme the protocol name
	 * @return the port number
	 */
	public int getPort(String scheme) {
		return portFromScheme.get(scheme);
	}
	
	@Override
	public String toString() {
		return getKey().toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + key.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		Node other = (Node) obj;
		return getKey().equals(other.getKey());
	}

	@Override
	public int compareTo(Node node) {
		return key.compareTo(node.key);
	}
	
	
	
}
