package il.technion.ewolf.kbr;

import il.technion.ewolf.kbr.openkad.KadNetModule;

import java.io.File;
import java.io.PrintStream;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class KeyGenerator {

	public static void main(String[] args) throws Exception {
	
		Injector injector = Guice.createInjector(
			new KadNetModule()
				.setProperty("openkad.keyfactory.keysize", "7")
			);
		
		KeyFactory kf = injector.getInstance(KeyFactory.class);
		
		PrintStream out = new PrintStream("keys.7");
		
		for (int i=0; i < 1000000; ++i) {
			out.println(kf.generate().toBase64());
		}
	     String currentDir = new File(".").getAbsolutePath();
	     System.out.println(currentDir);
		out.close();
		System.out.println("DONE !");
	}
}
