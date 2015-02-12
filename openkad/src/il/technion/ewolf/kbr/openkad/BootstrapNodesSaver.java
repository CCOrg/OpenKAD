package il.technion.ewolf.kbr.openkad;

import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.openkad.bucket.KadBuckets;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

public class BootstrapNodesSaver {

	
	private final KadBuckets kBuckets;
	private final Provider<KadNode> kadNodeProvider;
	private final File nodesFile;
	
	@Inject
	BootstrapNodesSaver(
			KadBuckets kBuckets,
			Provider<KadNode> kadNodeProvider,
			@Named("openkad.file.nodes") File nodesFile) {
		
		this.kBuckets = kBuckets;
		this.kadNodeProvider = kadNodeProvider;
		this.nodesFile = nodesFile;
	}
	
	
	public void start() {
		
	}
	
	
	public void saveNow() throws IOException {
		FileOutputStream fout = null;
		ObjectOutputStream oout = null;
		try {
			fout = new FileOutputStream(nodesFile);
			oout = new ObjectOutputStream(fout);
			
			oout.writeObject(kBuckets.getAllNodes());
			
		} finally {
			if (oout != null)
				oout.close();
			if (fout != null)
				fout.close();
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public void load() throws IOException {
		if (nodesFile.length() == 0L)
			return;
		
		FileInputStream fin = null;
		ObjectInputStream oin = null;
		List<Node> nodes;
		try {
			fin = new FileInputStream(nodesFile);
			oin = new ObjectInputStream(fin);
			
			nodes = (List<Node>) oin.readObject();
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
			
		} finally {
			if (oin != null)
				oin.close();
			if (fin != null)
				fin.close();
		}
		
		for (Node n : nodes) {
			kBuckets.insert(kadNodeProvider.get().setNode(n));
		}
	}
	
}
