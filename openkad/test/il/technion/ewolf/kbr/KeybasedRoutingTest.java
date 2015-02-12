package il.technion.ewolf.kbr;

import il.technion.ewolf.kbr.openkad.KadNetModule;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Assert;

import org.junit.Test;

import test.Statistics;
import test.StatisticsModule;

import com.google.inject.Guice;
import com.google.inject.Injector;
public class KeybasedRoutingTest {

	@Test
	public void the2NodesShouldFindEachOther() throws Throwable {
		int basePort = 10000;
		List<KeybasedRouting> kbrs = new ArrayList<KeybasedRouting>();
		for (int i=0; i < 2; ++i) {
			Injector injector = Guice.createInjector(new KadNetModule()
					.setProperty("openkad.keyfactory.keysize", "1")
					.setProperty("openkad.bucket.kbuckets.maxsize", "3")
					.setProperty("openkad.seed", ""+(i+basePort))
					.setProperty("openkad.net.udp.port", ""+(i+basePort)));
			
			
			KeybasedRouting kbr = injector.getInstance(KeybasedRouting.class);
			kbr.create();
			kbrs.add(kbr);
		}
		
		kbrs.get(1).join(Arrays.asList(new URI("openkad.udp://127.0.0.1:"+basePort+"/")));
		System.out.println("finished joining");
		
		for (int i=0; i < kbrs.size(); ++i) {
			System.out.println(kbrs.get(i));
			System.out.println("======");
		}
		
		List<Node> findNode = kbrs.get(1).findNode(kbrs.get(0).getLocalNode().getKey());
		Assert.assertEquals(kbrs.get(0).getLocalNode(), findNode.get(0));
		Assert.assertEquals(kbrs.get(1).getLocalNode(), findNode.get(1));
		
		findNode = kbrs.get(0).findNode(kbrs.get(0).getLocalNode().getKey());
		Assert.assertEquals(kbrs.get(0).getLocalNode(), findNode.get(0));
		Assert.assertEquals(kbrs.get(1).getLocalNode(), findNode.get(1));
		
		findNode = kbrs.get(0).findNode(kbrs.get(1).getLocalNode().getKey());
		Assert.assertEquals(kbrs.get(1).getLocalNode(), findNode.get(0));
		Assert.assertEquals(kbrs.get(0).getLocalNode(), findNode.get(1));
		
		findNode = kbrs.get(1).findNode(kbrs.get(1).getLocalNode().getKey());
		Assert.assertEquals(kbrs.get(1).getLocalNode(), findNode.get(0));
		Assert.assertEquals(kbrs.get(0).getLocalNode(), findNode.get(1));
		
		System.out.println(findNode);
		
	}
	
	
	@Test
	public void the16NodesShouldFindEachOther() throws Throwable {
		int basePort = 10100;
		List<KeybasedRouting> kbrs = new ArrayList<KeybasedRouting>();
		for (int i=0; i < 16; ++i) {
			Injector injector = Guice.createInjector(new KadNetModule()
					.setProperty("openkad.keyfactory.keysize", "2")
					.setProperty("openkad.bucket.kbuckets.maxsize", "5")
					.setProperty("openkad.color.nrcolors", "128")
					.setProperty("openkad.seed", ""+(i+basePort))
					.setProperty("openkad.net.udp.port", ""+(i+basePort)));
			KeybasedRouting kbr = injector.getInstance(KeybasedRouting.class);
			kbr.create();
			kbrs.add(kbr);
		}
		
		for (int i=1; i < kbrs.size(); ++i) {
			int port = basePort + i -1;
			System.out.println(i+" ==> "+(i-1));
			kbrs.get(i).join(Arrays.asList(new URI("openkad.udp://127.0.0.1:"+port+"/")));
		}
			
		System.out.println("finished joining");
		
		//kbrs.get(0).findNode(kbrs.get(0).getKeyFactory().generate());
		
		for (int i=0; i < kbrs.size(); ++i) {
			System.out.println(kbrs.get(i));
			System.out.println("======");
		}
		for (int j=0; j < kbrs.size(); ++j) {
			Set<List<Node>> findNodeResults = new HashSet<List<Node>>();
			for (int i=0; i < kbrs.size(); ++i) {
				List<Node> findNode = kbrs.get(i).findNode(kbrs.get(j).getLocalNode().getKey());
				System.out.println(findNode);
				findNodeResults.add(findNode);
			}
			
			if (findNodeResults.size() != 1) {
				for (List<Node> n : findNodeResults)
					System.err.println(n);
			}
			Assert.assertEquals(1, findNodeResults.size());
		}
	}
	
	/*
	@Test
	public void the64NodesShouldFindEachOtherAsynchronously() throws Throwable {
		int basePort = 10800;
		List<KeybasedRouting> kbrs = new ArrayList<KeybasedRouting>();
		for (int i=0; i < 64; ++i) {
			Injector injector = Guice.createInjector(new KadNetModule()
					.setProperty("openkad.keyfactory.keysize", "2")
					.setProperty("openkad.bucket.kbuckets.maxsize", "5")
					.setProperty("openkad.seed", ""+(i+basePort))
					.setProperty("openkad.net.udp.port", ""+(i+basePort)));
			KeybasedRouting kbr = injector.getInstance(KeybasedRouting.class);
			kbr.create();
			kbrs.add(kbr);
		}
		
		for (int i=1; i < kbrs.size(); ++i) {
			int port = basePort + i -1;
			System.out.println(i+" ==> "+(i-1));
			kbrs.get(i).join(Arrays.asList(new URI("openkad.udp://127.0.0.1:"+port+"/")));
		}
			
		System.out.println("finished joining");
		
		
		for (int i=0; i < kbrs.size(); ++i) {
			System.out.println(kbrs.get(i));
			System.out.println("======");
		}
		
		List<Future<List<Node>>> futures = new ArrayList<Future<List<Node>>>();
		
		for (int j=0; j < kbrs.size(); ++j) {
			for (int i=0; i < kbrs.size(); ++i) {
				futures.add(kbrs.get(i).findNode(kbrs.get(j).getLocalNode().getKey(), 5));
				//System.out.println(findNode);
				//findNodeResults.add(findNode);
			}
		}
		int i=0;
		for (Future<List<Node>> f : futures) {
			System.out.println(i++);
			f.get();
		}
	}
	*/
	
	@Test
	public void the64NodesShouldFindEachOther() throws Throwable {
		int basePort = 10200;
		List<KeybasedRouting> kbrs = new ArrayList<KeybasedRouting>();
		List<Statistics> stats = new ArrayList<Statistics>();
		Random rnd = new Random(10200);
		for (int i=0; i < 64; ++i) {
			Injector injector = Guice.createInjector(new KadNetModule()
					.setProperty("openkad.keyfactory.keysize", "3")
					.setProperty("openkad.bucket.kbuckets.maxsize", "3")
					.setProperty("openkad.color.nrcolors", "1")
					.setProperty("openkad.net.concurrency", "3")
					.setProperty("openkad.seed", ""+(i+basePort))
					.setProperty("openkad.net.udp.port", ""+(i+basePort))
					, new StatisticsModule());
			KeybasedRouting kbr = injector.getInstance(KeybasedRouting.class);
			kbr.create();
			kbrs.add(kbr);
			stats.add(injector.getInstance(Statistics.class));
		}
		
		for (int i=1; i < kbrs.size(); ++i) {
			int port = basePort + rnd.nextInt(i);
			System.out.println(i+" ==> "+(port-basePort));
			kbrs.get(i).join(Arrays.asList(new URI("openkad.udp://127.0.0.1:"+port+"/")));
		}
			
		System.out.println("finished joining");
		
		for (int j=0; j < stats.size(); ++j) {
			stats.get(j).nrHandledMsgs.set(0);
		}
		
		for (int i=0; i < kbrs.size(); ++i) {
			System.out.println(kbrs.get(i));
			System.out.println("======");
		}
		for (int j=0; j < kbrs.size(); ++j) {
			Set<List<Node>> findNodeResults = new HashSet<List<Node>>();
			for (int i=0; i < kbrs.size(); ++i) {
				List<Node> findNode = kbrs.get(i).findNode(kbrs.get(j).getLocalNode().getKey());
				System.out.println(findNode);
				findNodeResults.add(findNode);
			}
			
			if (findNodeResults.size() != 1) {
				for (List<Node> r : findNodeResults)
					System.err.println(r);
			}
			Assert.assertEquals(1, findNodeResults.size());
		}
		
		int total=0;
		for (int j=0; j < stats.size(); ++j) {
			int curr = stats.get(j).nrHandledMsgs.get();
			System.out.println("node: " + j + " nrHandledMsgs: " + curr);
			total+=curr;
		}
		System.out.println("total: " + total);
	}
	
	
	@Test(timeout=5000)
	public void the2NodesShouldAbleToSendMessages() throws Throwable {
		int basePort = 10300;
		List<KeybasedRouting> kbrs = new ArrayList<KeybasedRouting>();
		for (int i=0; i < 2; ++i) {
			Injector injector = Guice.createInjector(new KadNetModule()
					.setProperty("openkad.keyfactory.keysize", "1")
					.setProperty("openkad.bucket.kbuckets.maxsize", "1")
					.setProperty("openkad.seed", ""+(i+basePort))
					.setProperty("openkad.net.udp.port", ""+(i+basePort)));
			KeybasedRouting kbr = injector.getInstance(KeybasedRouting.class);
			kbr.create();
			kbrs.add(kbr);
		}
		
		for (int i=1; i < kbrs.size(); ++i) {
			int port = basePort + i -1;
			System.out.println(i+" ==> "+(i-1));
			kbrs.get(i).join(Arrays.asList(new URI("openkad.udp://127.0.0.1:"+port+"/")));
		}
			
		System.out.println("finished joining");
		final AtomicBoolean isDone = new AtomicBoolean(false);
		kbrs.get(1).register("tag", new DefaultMessageHandler() {
			
			@Override
			public void onIncomingMessage(Node from, String tag, Serializable content) {
				Assert.assertEquals("msg", content);
				synchronized (isDone) {
					isDone.set(true);
					isDone.notifyAll();
				}
			}
		});
		
		List<Node> findNode = kbrs.get(0).findNode(kbrs.get(1).getLocalNode().getKey());
		
		System.out.println("sending msg");
		kbrs.get(0).sendMessage(findNode.get(0), "tag", "msg");
		
		synchronized (isDone) {
			while (!isDone.get())
				isDone.wait();
		}
	}
	
	
	enum Y {
		A, B, C
	}
	
	private static final class X implements Serializable {
		
		private static final long serialVersionUID = -5254444279440929179L;
		
		int a;
		int b;
		String c;
		
		public X(int a, int b, String c) {
			this.a = a;
			this.b = b;
			this.c = c;
		}
		
		@Override
		public int hashCode() {
			return 0;
		}
		@Override
		public boolean equals(Object obj) {
			if (obj == null || !getClass().equals(obj.getClass()))
				return false;
			X x = (X)obj;
			return x.a == a && x.b == b && x.c.equals(c);
		}
	}
	
	@Test(timeout=5000)
	public void the2NodesShouldAbleToSendArbitrarySerializableMessages() throws Throwable {
		int basePort = 10400;
		List<KeybasedRouting> kbrs = new ArrayList<KeybasedRouting>();
		for (int i=0; i < 2; ++i) {
			Injector injector = Guice.createInjector(new KadNetModule()
					.setProperty("openkad.keyfactory.keysize", "1")
					.setProperty("openkad.bucket.kbuckets.maxsize", "1")
					.setProperty("openkad.seed", ""+(i+basePort))
					.setProperty("openkad.net.udp.port", ""+(i+basePort)));
			KeybasedRouting kbr = injector.getInstance(KeybasedRouting.class);
			kbr.create();
			kbrs.add(kbr);
		}
		
		for (int i=1; i < kbrs.size(); ++i) {
			int port = basePort + i -1;
			System.out.println(i+" ==> "+(i-1));
			kbrs.get(i).join(Arrays.asList(new URI("openkad.udp://127.0.0.1:"+port+"/")));
		}
			
		System.out.println("finished joining");
		final X x = new X(1,2,"aaa");
		final AtomicBoolean isDone = new AtomicBoolean(false);
		kbrs.get(1).register("tag", new DefaultMessageHandler() {
			
			@Override
			public void onIncomingMessage(Node from, String tag, Serializable content) {
				Assert.assertEquals(x, content);
				synchronized (isDone) {
					isDone.set(true);
					isDone.notifyAll();
				}
			}
		});
		
		List<Node> findNode = kbrs.get(0).findNode(kbrs.get(1).getLocalNode().getKey());
		
		System.out.println("sending msg");
		kbrs.get(0).sendMessage(findNode.get(0), "tag", x);
		
		synchronized (isDone) {
			while (!isDone.get())
				isDone.wait();
		}
	}
	
	
	@Test(timeout=5000)
	public void the2NodesShouldAbleToSendArbitrarySerializableRequests() throws Throwable {
		int basePort = 10500;
		List<KeybasedRouting> kbrs = new ArrayList<KeybasedRouting>();
		for (int i=0; i < 2; ++i) {
			Injector injector = Guice.createInjector(new KadNetModule()
					.setProperty("openkad.keyfactory.keysize", "1")
					.setProperty("openkad.bucket.kbuckets.maxsize", "1")
					.setProperty("openkad.seed", ""+(i+basePort))
					.setProperty("openkad.net.udp.port", ""+(i+basePort)));
			KeybasedRouting kbr = injector.getInstance(KeybasedRouting.class);
			kbr.create();
			kbrs.add(kbr);
		}
		
		for (int i=1; i < kbrs.size(); ++i) {
			int port = basePort + i -1;
			System.out.println(i+" ==> "+(i-1));
			kbrs.get(i).join(Arrays.asList(new URI("openkad.udp://127.0.0.1:"+port+"/")));
		}
			
		System.out.println("finished joining");
		final X x = new X(1, 2, "abc");
		kbrs.get(1).register("tag", new DefaultMessageHandler() {
			@Override
			public Serializable onIncomingRequest(Node from, String tag, Serializable content) {
				Assert.assertEquals(x, content);
				return new X(3, 4, "edf");
			}
		});
		
		List<Node> findNode = kbrs.get(0).findNode(kbrs.get(1).getLocalNode().getKey());
		
		Serializable res = kbrs.get(0).sendRequest(findNode.get(0), "tag", x).get();
		Assert.assertEquals(new X(3, 4, "edf"), res);
	}
	
	@Test(timeout=30000)
	public void the16NodesShouldAbleToSendMessages() throws Throwable {
		int basePort = 10600;
		List<KeybasedRouting> kbrs = new ArrayList<KeybasedRouting>();
		for (int i=0; i < 16; ++i) {
			Injector injector = Guice.createInjector(new KadNetModule()
					.setProperty("openkad.keyfactory.keysize", "5")
					.setProperty("openkad.bucket.kbuckets.maxsize", "5")
					.setProperty("openkad.seed", ""+(i+basePort))
					.setProperty("openkad.net.udp.port", ""+(i+basePort)));
			KeybasedRouting kbr = injector.getInstance(KeybasedRouting.class);
			kbr.create();
			kbrs.add(kbr);
		}
		
		for (int i=1; i < kbrs.size(); ++i) {
			int port = basePort + i -1;
			System.out.println(i+" ==> "+(i-1));
			kbrs.get(i).join(Arrays.asList(new URI("openkad.udp://127.0.0.1:"+port+"/")));
		}
			
		System.out.println("finished joining");
		final AtomicBoolean isDone = new AtomicBoolean(false);
		kbrs.get(13).register("tag", new DefaultMessageHandler() {
			
			@Override
			public void onIncomingMessage(Node from, String tag, Serializable content) {
				Assert.assertEquals("msg", content);
				System.out.println("got "+ content);
				synchronized (isDone) {
					isDone.set(true);
					isDone.notifyAll();
				}
			}
		});
		
		List<Node> findNode = kbrs.get(0).findNode(kbrs.get(13).getLocalNode().getKey());
		
		kbrs.get(2).sendMessage(findNode.get(0), "tag", "msg");
		
		synchronized (isDone) {
			while (!isDone.get())
				isDone.wait();
		}
	}
	
	
	@Test(timeout=5000)
	public void the2NodesShouldAbleToSendRequest() throws Throwable {
		int basePort = 10700;
		List<KeybasedRouting> kbrs = new ArrayList<KeybasedRouting>();
		for (int i=0; i < 2; ++i) {
			Injector injector = Guice.createInjector(new KadNetModule()
					.setProperty("openkad.keyfactory.keysize", "1")
					.setProperty("openkad.bucket.kbuckets.maxsize", "1")
					.setProperty("openkad.seed", ""+(i+basePort))
					.setProperty("openkad.net.udp.port", ""+(i+basePort)));
			KeybasedRouting kbr = injector.getInstance(KeybasedRouting.class);
			kbr.create();
			kbrs.add(kbr);
		}
		
		for (int i=1; i < kbrs.size(); ++i) {
			int port = basePort + i -1;
			System.out.println(i+" ==> "+(i-1));
			kbrs.get(i).join(Arrays.asList(new URI("openkad.udp://127.0.0.1:"+port+"/")));
		}
			
		System.out.println("finished joining");
		
		kbrs.get(1).register("tag", new DefaultMessageHandler() {
			@Override
			public Serializable onIncomingRequest(Node from, String tag, Serializable content) {
				Assert.assertEquals("msg", content);
				return "new_msg";
			}
		});
		
		List<Node> findNode = kbrs.get(0).findNode(kbrs.get(1).getLocalNode().getKey());
		
		Serializable res = kbrs.get(0).sendRequest(findNode.get(0), "tag", "msg").get();
		Assert.assertEquals("new_msg", res);
	}
}
