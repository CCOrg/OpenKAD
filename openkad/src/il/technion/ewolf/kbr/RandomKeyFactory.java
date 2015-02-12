package il.technion.ewolf.kbr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

import org.apache.commons.codec.binary.Base64;

/**
 * Creates keys using a Random generator
 * 
 * @author eyal.kibbar@gmail.com
 */
public class RandomKeyFactory implements KeyFactory {

	private final int keyByteLength;
	private final Random rnd;
	private final MessageDigest md;

	/**
	 * 
	 * @param keyByteLength
	 *            the length in bytes of the generated keys
	 * @param rnd
	 *            the random object to be used
	 * @param hashAlgo
	 *            the algorithm to be used for digesting when generating from an
	 *            array of topics
	 * @throws NoSuchAlgorithmException
	 *             if the digest algorithm was not found
	 */
	public RandomKeyFactory(final int keyByteLength, final Random rnd, final String hashAlgo) throws NoSuchAlgorithmException {
		this.keyByteLength = keyByteLength;
		this.rnd = rnd;
		this.md = MessageDigest.getInstance(hashAlgo);
	}

	@Override
	public Key getZeroKey() {
		final byte[] b = new byte[keyByteLength];
		Arrays.fill(b, (byte) 0);
		return new Key(b);
	}

	@Override
	public Key generate() {
		final byte[] b = new byte[keyByteLength];
		rnd.nextBytes(b);
		return new Key(b);
	}

	@Override
	public Key generate(final int pow2Max) {

		if (pow2Max < 0 || keyByteLength * 8 <= pow2Max)
			throw new IllegalArgumentException();

		final byte[] b = new byte[keyByteLength];
		Arrays.fill(b, (byte) 0);

		final byte[] r = new BigInteger(pow2Max, rnd).toByteArray();

		for (int i_b = b.length - 1, i_r = r.length - 1; i_r >= 0 && i_b >= 0; b[i_b--] = r[i_r--]);

		b[b.length - pow2Max / 8 - 1] |= 1 << (pow2Max % 8);

		return new Key(b);
	}

	@Override
	public Key get(final byte[] bytes) {
		if (bytes.length != keyByteLength)
			throw new IllegalArgumentException("key length is invalid");
		return new Key(bytes);
	}

	@Override
	public Key get(final String base64Encoded) {
		final byte[] decodeBase64 = Base64.decodeBase64(base64Encoded);
		if (decodeBase64.length != keyByteLength)
			throw new IllegalArgumentException("key length is invalid");
		return new Key(decodeBase64);
	}

	@Override
	public Key create(final String... topics) {

		ByteArrayOutputStream out = null;
		ByteArrayInputStream in = null;

		try {
			out = new ByteArrayOutputStream();
			for (final String topic : topics)
				out.write(topic.getBytes());

			out.flush();

			in = new ByteArrayInputStream(out.toByteArray());

			return create(in);

		} catch (final IOException e) {
			throw new AssertionError();
		} finally {
			try {
				out.close();
			} catch (final Exception e) {
			}
			try {
				in.close();
			} catch (final Exception e) {
			}
		}
	}

	@Override
	public Key create(final InputStream data) throws IOException {
		final byte[] buff = new byte[512];
		int n;
		while ((n = data.read(buff)) > 0)
			md.update(buff, 0, n);
		byte[] b = md.digest();
		if (b.length > keyByteLength)
			b = Arrays.copyOfRange(b, 0, keyByteLength);
		return new Key(b);
	}

	@Override
	public int getByteLength() {
		return keyByteLength;
	}

	@Override
	public int getBitLength() {
		return getByteLength() * 8;
	}

	@Override
	public boolean isValid(final Key key) {
		return key.getByteLength() == getByteLength();
	}

}
