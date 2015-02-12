package il.technion.ewolf.kbr.openkad.net;

import il.technion.ewolf.kbr.openkad.msg.KadMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * Base class for all KadMessage serializers
 * @author eyal.kibbar@gmail.com
 *
 */
public abstract class KadSerializer {

	/**
	 * Reads an object from an InputStream
	 * @param in the input stream
	 * @return the KadMessage de-serialized
	 * 
	 * @throws IOException the input throw an IOException
	 * @throws ClassCastException the input is not a KadMessage
	 * @throws ClassNotFoundException the message type was not found
	 */
	public abstract KadMessage read(InputStream in) throws IOException, ClassCastException, ClassNotFoundException;
	
	/**
	 * Writes a KadMessage into an output stream
	 * @param msg the KadMessage to be serialized
	 * @param out the output stream to write to
	 * @throws IOException the output stream throw an IOException
	 */
	public abstract void write(KadMessage msg, OutputStream out) throws IOException;
	
	/**
	 * The default java object serializer
	 * @param x
	 * @return
	 */
	protected byte[] serialize(Serializable x) {
		ByteArrayOutputStream bout = null;
		ObjectOutputStream oout = null;
		try {
			try {
				bout = new ByteArrayOutputStream();
				oout = new ObjectOutputStream(bout);
				oout.writeObject(x);
			} finally {
				if (bout != null) bout.close();
				if (oout != null) oout.close();
			}
		} catch (IOException e) {
			throw new AssertionError("could not serialize object");
		}
		return bout.toByteArray();
	}
	
	/**
	 * The default java object de-serializer
	 * @param b
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	protected Serializable deserialize(byte[] b) throws IOException, ClassNotFoundException {
		ByteArrayInputStream bin = null;
		ObjectInputStream oin = null;
		try {
			bin = new ByteArrayInputStream(b);
			oin = new ObjectInputStream(bin);
			return (Serializable) oin.readObject();
		} finally {
			if (bin != null) bin.close();
			if (oin != null) oin.close();
		}
	}
	
}
