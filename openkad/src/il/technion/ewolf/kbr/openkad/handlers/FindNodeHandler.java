package il.technion.ewolf.kbr.openkad.handlers;



/**
 * Handle find node requests by giving the known closest nodes to the requested key
 * from the KBuckets data structure
 * @author eyal.kibbar@gmail.com
 *
 */
public abstract interface FindNodeHandler {
	public void register();


}
