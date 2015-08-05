package stm.impl.executors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class GenericExecutor {
	ExecutorService executor; 
	long failedCount = 0;	
	/* Default constructor with 1 thread pool */
	public GenericExecutor() {
		executor = Executors.newFixedThreadPool(2); // Atleast one read and write threads
	}
	

	public GenericExecutor( int MaxSpec) {
		executor = Executors.newFixedThreadPool(2 * MaxSpec); //newSingleThreadExecutor();
	}
	
	public void execute(Runnable task) {
		try {
			executor.execute(task);
		} catch (RejectedExecutionException ex) {
			failedCount++;
			//System.out.print("!");
		}
	}
	
	public long shutDownWriteExecutor() {
		executor.shutdown();
		try {
			executor.awaitTermination(10, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return this.failedCount;
	}
}
