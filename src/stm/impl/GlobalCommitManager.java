package stm.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.esotericsoftware.kryo.Kryo;
import stm.benchmark.bank.Account;
import stm.benchmark.tpcc.TpccCustomer;
import stm.benchmark.tpcc.TpccDistrict;
import stm.benchmark.tpcc.TpccItem;
import stm.benchmark.tpcc.TpccOrder;
import stm.benchmark.tpcc.TpccOrderline;
import stm.benchmark.tpcc.TpccStock;
import stm.benchmark.tpcc.TpccWarehouse;
//import stm.benchmark.vacation.Customer;
//import stm.benchmark.vacation.Reservation;
//import stm.benchmark.vacation.ReservationInfo;
import stm.impl.PaxosSTM;
import stm.transaction.AbstractObject;
import stm.transaction.ReadSetObject;
import stm.transaction.TransactionContext;
import lsr.common.ClientRequest;
import lsr.common.Request;
import lsr.common.RequestId;
import lsr.paxos.Paxos;
import lsr.paxos.client.NewClient;

public class GlobalCommitManager {

	BlockingQueue<ClientRequest> rQueue = new LinkedBlockingQueue<ClientRequest>();
	BlockingQueue<Request> cQueue = new LinkedBlockingQueue<Request>();

	private Thread batcherThread;
	private Thread commitThread;

	/* Thread to monitor the ClientRequest queue */
	private Thread scanThread;

	private final PaxosSTM stmInstance;

	long failedCount = 0;
	long rqAbortCount = 0;						/* Number of aborts from the RQueue */
	long rqAbortTriggerCount = 0;					/* Number of events triggerring RQueue aborts */

	private NewClient client;

	public GlobalCommitManager(PaxosSTM stmInstance, Paxos paxos, int clientCount) {
		this.stmInstance = stmInstance;
		this.client = new NewClient(paxos);
	}

	public void start() {
		this.batcherThread = new Thread(new Batcher(), "Batcher");
		batcherThread.start();

		this.commitThread = new Thread(new Committer(), "Committer");
		commitThread.start();

		this.scanThread = new Thread(new Scanner(), "Scanner");
		//scanThread.start();
		this.client.init();
	}

	public long  getrQueueSize()
	{
		return rQueue.size();	
	}
	public void execute(ClientRequest task) {
		rQueue.offer(task);
	}

	public void notify(Request request) {
		cQueue.offer(request);
	}
	
	public long getRqAbortCount()
	{
		return this.rqAbortCount;
	}

	public long getRqAbortTrigCount()
	{
		return this.rqAbortTriggerCount;
	}
	/* This function scans the rQueue and aborts any conflicting tx */	
	public void abortXcomitted()
	{	
		long lastrqAbortCount = rqAbortCount;
		
		/* Iterate over all the requests present in the rQueue */
		//System.out.println("Calling AbortXComitted");
		for(Iterator<ClientRequest> it = rQueue.iterator(); it.hasNext();)
		{
		
			ClientRequest request = it.next();
			RequestId rId = request.getRequestId();
			TransactionContext ctx = stmInstance.getTransactionContext(rId);
			if(ctx == null) 
			{
                        	continue;
                	}

                	ArrayList<ReadSetObject> readset = ctx.getReadSet();
                	/* Iterate over the objects in the request's readset*/
			for (ReadSetObject entry : readset) 
			{
                        	
				int objId = entry.objId;
				if(stmInstance.abortedObjectMapcontainsKey(objId))
				{
					/* The transaction contains an object whose X copy is invalidated, thus this transaction will abort later.
					 * We can abort this transaction from the rQueue, saving some decision making effort. But before aborting
					 * and restarting the transaction we need to ensure that the transaction is still on the rQueue. It is
					 * possible that it was taken by the consumer while we were iterating */
					
					 if(rQueue.remove(request))
					 {
						/* Request was removed from the rQueue, now the housekeeping activities associated with an Xcommit abort
						 * need to be carried out*/
				
						stmInstance.emptyWriteSet(ctx, true, 0);
                        			stmInstance.removeTransactionContext(rId);
                        			stmInstance.getSTMService().executeRequest(request, false);
                        			//System.out.println("Aborted Tx from Rqueue");
                        			//stmInstance.printabortedObjects();
                        			rqAbortCount++;
                       				break;	
                			}
				}
			}
		}
		if(rqAbortCount > lastrqAbortCount)
		{
			rqAbortTriggerCount++;
		}
	}		
	private class Batcher implements Runnable {

		private final Kryo kryo;

		public Batcher() {
			kryo = new Kryo();
			kryo.register(TransactionContext.class);
		}

		@Override
		public void run() {
			while (true) {
				ClientRequest request = null;
				try {
					request = rQueue.take();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				RequestId rId = request.getRequestId();
				TransactionContext ctx = stmInstance.getTransactionContext(rId);

				byte[] value = null;
				try {
					value = stmInstance.getSTMService().serializeTransactionContext(ctx);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				Request proposal = new Request(rId, value);
				
				client.queue(proposal);

			}
		}
	}

	private class Committer implements Runnable {

		private final Kryo kryo;

		public Committer() {
			kryo = new Kryo();
			kryo.register(TransactionContext.class);
		}

		@Override
		public void run() {
			while (true) {
				Request request = null;
				try {
					request = cQueue.take();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}


				try {
					stmInstance.onCommit(request.getRequestId(),
							stmInstance.getSTMService().deserializeTransactionContext(request.getValue()));
				} catch (IOException e) {
					e.printStackTrace();
				}

			}

		}
	}
	private class Scanner implements Runnable {

		private final Kryo kryo;

		public Scanner() {
			kryo = new Kryo();
			kryo.register(TransactionContext.class);
		}

		@Override
		public void run() {
		
			/*while (true) 
			{
				ClientRequest request = null;
				try {
					request = rQueue.take();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				RequestId rId = request.getRequestId();
				TransactionContext ctx = stmInstance.getTransactionContext(rId);

				byte[] value = null;
				try {
					value = stmInstance.getSTMService().serializeTransactionContext(ctx);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				Request proposal = new Request(rId, value);
				
				client.queue(proposal);

			}*/
		}
	}
 	
	// Return the number of requests submitted to network 
	public long getReqCount()
        {
                //return 0;
		return client.getReqCount();
        }

	public long getTcpMsgCount()
	{
		//return 0;
		return client.getTcpMsgCount();
	}

	public long getPropMsgCount()
	{
		return 0;
		//return client.getPropMsgCount();
	}


	public long getProposalLength()
        {
                //return 0;
                return client.getProposalLength();
        }
}
