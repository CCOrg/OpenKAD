package il.technion.ewolf.kbr.openkad.net;

import il.technion.ewolf.kbr.openkad.msg.KadMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.lang.reflect.Type;

import org.apache.commons.codec.binary.Base64;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.inject.Inject;

/**
 * Serialize a message into a gun-ziped json message
 * 
 * @author eyal.kibbar@gmail.com
 * 
 */
public class JsonKadSerializer extends KadSerializer implements JsonSerializer<Serializable>, JsonDeserializer<Serializable> {

	private final Gson gson;

	private static final String classPackage = KadMessage.class.getPackage().getName() + ".";

	@Inject
	JsonKadSerializer() {
		this.gson = new GsonBuilder().registerTypeAdapter(Serializable.class, this)
				.registerTypeHierarchyAdapter(Serializable.class, this).create();
	}

	@Override
	public KadMessage read(final InputStream in) throws IOException, ClassCastException, ClassNotFoundException {
		Reader utf8Reader = null;
		JsonReader reader = null;

		KadMessage msg = null;
		try {
			utf8Reader = new InputStreamReader(in, "UTF-8");
			reader = new JsonReader(utf8Reader);

			reader.beginArray();
			final String clazzName = gson.fromJson(reader, String.class);
			msg = gson.fromJson(reader, Class.forName(classPackage + clazzName));
			reader.endArray();

		} finally {
			reader.close();
			utf8Reader.close();
			in.close();
		}

		return msg;
	}

	@Override
	public void write(final KadMessage msg, final OutputStream out) throws IOException {

		Writer utf8Writer = null;
		JsonWriter writer = null;

		try {

			utf8Writer = new OutputStreamWriter(out, "UTF-8");
			writer = new JsonWriter(utf8Writer);

			writer.beginArray();
			final Class<?> clazz = msg.getClass();
			this.gson.toJson(clazz.getSimpleName(), String.class, writer);
			// System.out.println("writing class: "+clazz);
			this.gson.toJson(msg, clazz, writer);
			writer.endArray();

		} finally {
			writer.close();
			utf8Writer.close();
			out.close();

		}
	}

	@Override
	public Serializable deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
			throws JsonParseException {
		final byte[] src = Base64.decodeBase64(json.getAsJsonPrimitive().getAsString());
		try {
			return JsonKadSerializer.this.deserialize(src);
		} catch (final Exception e) {
			throw new JsonParseException(e);
		}
	}

	@Override
	public JsonElement serialize(final Serializable src, final Type typeOfSrc, final JsonSerializationContext context) {
		final byte[] serialized = JsonKadSerializer.this.serialize(src);
		final String s = Base64.encodeBase64String(serialized);
		return new JsonPrimitive(s);
	}

}
