package il.technion.ewolf.kbr;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;

import org.apache.commons.codec.binary.Base64;

/**
 * Identifier for nodes. Use {@link KeyFactory} to generate instances of this
 * class.
 * 
 * @author eyal.kibbar@gmail.com
 */
public class Key implements Serializable, Comparable<Key> {

	private static final long serialVersionUID = 4137662182397711129L;
	private int color;
	private final byte[] bytes;

	public Key(final byte[] bytes) {
		this.bytes = bytes;
	}
	/**
	 * Check if a key is 0 key
	 * 
	 * @return true if bytes of this key are 0
	 */
	public boolean isZeroKey() {
		for (final byte x : getBytes())
			if (x != 0)
				return false;
		return true;
	}

	/**
	 * A key color is a number between 0 and nrColors that is calculated using
	 * its LSBs
	 * 
	 * @param nrColors
	 * @return the calculated color
	 */
	public int getColor(final int nrColors) {
		if (this.color < 0)
			this.color = Math.abs(getInt().intValue()) % nrColors;
		return this.color;
	}

	/**
	 * 
	 * @return all the key's bytes
	 */
	public byte[] getBytes() {
		return this.bytes;
	}

	/**
	 * 
	 * @return length of key in bytes
	 */
	public int getByteLength() {
		return getBytes().length;
	}

	/**
	 * 
	 * @param k
	 *            another key
	 * @return a new Key which is the result of this key XOR the given key
	 */
	public Key xor(final Key k) {
		if (k.getByteLength() != getByteLength())
			throw new IllegalArgumentException("incompatable key for xor");
		final byte[] b = new byte[getByteLength()];
		for (int i = 0; i < b.length; ++i)
			b[i] = (byte) (getBytes()[i] ^ k.getBytes()[i]);
		return new Key(b);
	}
	/**
	 * @return the index of the MSB turned on, or -1 if all bits are off
	 */
	public int getFirstSetBitIndex() {
		for (int i = 0; i < getByteLength(); ++i) {
			if (getBytes()[i] == 0)
				continue;

			int j;
			for (j = 7; (getBytes()[i] & (1 << j)) == 0; --j);
			return (getByteLength() - i - 1) * 8 + j;
		}
		return -1;
	}

	/**
	 * @return length of key in bits
	 */
	public int getBitLength() {
		return getByteLength() * 8;
	}

	/**
	 * @return the key BigInteger representation
	 */
	public BigInteger getInt() {
		return new BigInteger(1, getBytes()); // TODO: yoav is getBytes()
												// two-complement?
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null || !getClass().equals(o.getClass()))
			return false;
		return Arrays.equals(getBytes(), ((Key) o).getBytes());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(getBytes());
	}

	/**
	 * 
	 * @return the key encode in Base64
	 */
	public String toBase64() {
		return Base64.encodeBase64String(this.bytes);
	}

	@Override
	public String toString() {
		return Base64.encodeBase64URLSafeString(this.bytes);
	}

	/**
	 * 
	 * @return the key encoded in binary string
	 */
	public String toBinaryString() {
		String $ = "";
		for (int i = 0; i < getByteLength(); ++i) {
			byte b = getBytes()[i];
			// fix negative numbers
			$ += b < 0 ? "1" : "0";
			b &= 0x7F;

			// fix insufficient leading 0s
			final String str = Integer.toBinaryString(b);
			switch (str.length()) {
				case 1 :
					$ += "000000";
					break;
				case 2 :
					$ += "00000";
					break;
				case 3 :
					$ += "0000";
					break;
				case 4 :
					$ += "000";
					break;
				case 5 :
					$ += "00";
					break;
				case 6 :
					$ += "0";
					break;
			}
			$ += str + " ";
		}
		return $;
	}

	@Override
	public int compareTo(final Key arg0) {
		return toString().compareTo(arg0.toString());
	}
}
