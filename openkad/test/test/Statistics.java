package test;

import java.util.concurrent.atomic.AtomicInteger;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class Statistics {
	
	public AtomicInteger nrHandledMsgs; 

	@Inject
	public Statistics(
			@Named("openkad.testing.nrIncomingMessages") AtomicInteger nrHandledMsgs){
		this.nrHandledMsgs = nrHandledMsgs;
	}
	
}
