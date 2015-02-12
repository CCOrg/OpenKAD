package il.technion.ewolf.kbr;

import java.io.IOException;
import java.io.InputStream;

public interface KeyFactory {
	
	/**
	 * 
	 * @return Key where all bytes are 0
	 */
	public Key getZeroKey();
	
	/**
	 * 
	 * @return a new random generated key
	 */
	public Key generate();
	
	/**
	 * 
	 * @param pow2Max power 2 of the max integer value
	 * @return a new random key where its integer value is between 0 and 2^pow2Max - 1
	 */
	public Key generate(int pow2Max);
	
	/**
	 * Convert a given byte array to a key.
	 * May throw an IllegalArgumentException if the number of bytes is incorrect.
	 * 
	 * @param bytes the key's bytes
	 * @return a key with the given bytes
	 */
	public Key get(byte[] bytes);
	
	/**
	 * Convert a given byte array encoded with Base64 to a key
	 * @param base64Encoded
	 * @return a key with the given bytes
	 */
	public Key get(String base64Encoded);
	
	/**
	 * Create a new key from an array of topics.
	 * If 2 arrays of topics are equals then the 2 keys generated from them will be equal
	 * If 2 arrays of topics are not equals the chances of the keys to be equal are next to none
	 * @param topics an arbitrary array of strings
	 * @return a key corresponding to the topics array
	 */
	public Key create(String ... topics);
	
	/**
	 * Create a new key from an arbitrary stream of bytes.
	 * @param data
	 * @return
	 * @throws IOException
	 */
	public Key create(InputStream data) throws IOException;
	
	/**
	 * 
	 * @return all the generated keys' length in bytes
	 */
	public int getByteLength();
	
	/**
	 *
	 * @return all the generated keys' length in bits
	 */
	public int getBitLength();
	
	/**
	 * Checking the given key has the correct length
	 * @param key
	 * @return
	 */
	public boolean isValid(Key key);
	
}
