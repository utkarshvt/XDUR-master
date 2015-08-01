package stm.benchmark.counter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.BrokenBarrierException;


import com.esotericsoftware.kryo.Kryo;
import lsr.common.ClientRequest;
import lsr.common.ProcessDescriptor;
import lsr.common.Request;
import lsr.common.RequestId;
import lsr.common.SingleThreadDispatcher;
import lsr.paxos.replica.Replica;
import lsr.paxos.replica.SnapshotListener;
import lsr.service.STMService;
import stm.impl.PaxosSTM;
import stm.impl.RequestWrap;
import stm.impl.SharedObjectRegistry;
import stm.transaction.AbstractObject;
import stm.transaction.ReadSetObject;
import stm.transaction.TransactionContext;


public class SharedCounter extends STMService
{
	public static final byte TX_INCREMENT = 0;
	public final int DEFAULT_LENGTH = 6;
	//int limit;
	SharedObjectRegistry sharedObjectRegistry;
        PaxosSTM stmInstance;
	Replica replica;
	SCMultiClient client;
	
	private volatile long completedCount = 0;
	
	XSpecThread[] executorTh;
	MonitorThread monitorTh = new MonitorThread();

	private int localId;
	private int numReplicas;
	
	private final Map<RequestId, byte[]> requestIdValueMap = new HashMap<RequestId, byte[]>();


	class MonitorThread extends Thread {
                public void run() {
                        int count = 0;
			long output  = 0;
                        //System.out
        //                              .println("Read-Throughput/S  Write Throughput/S  Latency Aborts Time");
                        try {
                                Thread.sleep(10000);
                        } catch (InterruptedException e1) {
                                // TODO Auto-generated catch block
                                e1.printStackTrace();
                        }
                        // Sample time is 20 seconds
                        while (count < 10) {
                                //startRead = System.currentTimeMillis();

                                try {
                                        Thread.sleep(10000);
                                } catch (InterruptedException e1) {
                                        // TODO Auto-generated catch block
                                        e1.printStackTrace();
                                }
                               
				output = completedCount;
				System.out.println("Counter = " + output);
			}
	}
	}

	public void SCInit(SharedObjectRegistry sharedObjectRegistry,
                        PaxosSTM stminstance, int MaxSpec)
	{
		//this.limit = MaxSpec * scale;
		this.sharedObjectRegistry = sharedObjectRegistry;
                this.stmInstance = stminstance;
		
		final int myid = 0;
		ShCountObject counter = new ShCountObject(myid);
		this.sharedObjectRegistry.registerObjects(myid, counter, MaxSpec);
		
		executorTh = new XSpecThread[MaxSpec];
                for( int i = 0; i < MaxSpec; i ++)
                {
                        executorTh[i].start();
                }

                monitorTh.start();


	}
	

	public void initRequests() {
                this.localId = ProcessDescriptor.getInstance().localId;
                this.numReplicas = ProcessDescriptor.getInstance().numReplicas;

                /*this.accessibleObjects = this.NUM_ITEMS / numReplicas;
                this.min = this.accessibleObjects * this.localId;
                this.max = (this.accessibleObjects * (this.localId + 1));
                // System.out.println("O:" + this.accessibleObjects + "M:" + this.max +
                // "m:" + this.min);

                this.accessibleWarehouses = this.NUM_WAREHOUSES / numReplicas;
                this.minW = this.accessibleWarehouses * this.localId;
                this.maxW = (this.accessibleWarehouses * (this.localId + 1));

                // System.out.println("O:" + this.accessibleWarehouses + "M:" +
                // this.maxW +
                // "m:" + this.minW);*/
        }

        public void initClient(SCMultiClient client) {
                this.client = client;
        }

	protected void increment(ClientRequest cRequest, boolean retry, int Tid)
	{
		 int success = 0;
                RequestId requestId = cRequest.getRequestId();
                final int  myid = 0;
		long tempcount = 0;

                //System.out.println("delivery: " + myid);
                boolean xretry = true;
                while( xretry == true )
                {

                        //System.out.println("Delivery retrying TX = " + Tid);
                        xretry = false;
                        ShCountObject counter = ((ShCountObject) stmInstance.Xopen(myid,
                                this.stmInstance.TX_READ_WRITE_MODE, requestId,
                                this.stmInstance.OBJECT_WRITE_MODE, retry, Tid));
                        if (counter == null)
                        {
                                xretry = true;
                                continue;
                        }
	
			counter.count++;
			tempcount = counter.count;
			if((xretry == false) && (stmInstance.XCommitTransaction(requestId, cRequest)))
                       	{
			 	completedCount = tempcount;
				System.out.println("Count = " + tempcount);
			}
                	else
                        	xretry = true;


		}
		byte[] result = ByteBuffer.allocate(4).putInt(success).array();
                stmInstance.storeResultToContext(requestId, result);
	}

	public void executeRequest(final ClientRequest request, final boolean retry) {
	
	        if(request != null)
		{
			RequestWrap Wrap = new RequestWrap(request, stmInstance.globalRqIdaddAndGet());
			stmInstance.xqueue(Wrap);
		}
        }

 	/**
         * Used to execute read requests from clients locally.
         */
        @Override
        public void executeReadRequest(final ClientRequest cRequest) {
                // TODO Auto-generated method stub
		/*
                tpccSTMDispatcher.submit(new Runnable() {
                        public void run() {
                                executeRequest(cRequest, false);
                        }
                });*/
        }


	@Override
        public void notifyCommitManager(Request request) {
                // System.out.print("!");
                stmInstance.notifyCommitManager(request);
        }

        
	 @Override
        public byte[] serializeTransactionContext(TransactionContext ctx)
                        throws IOException {
	   
		ArrayList<ReadSetObject> readset = ctx.getReadSet();
                Map<Integer, AbstractObject> writeset = ctx.getWriteSet();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ByteBuffer bb;

                bb = ByteBuffer.allocate(4);
                bb.putInt(readset.size());
                bb.flip();

                out.write(bb.array());
		
		/* Dummy function, does not do anything */
		return out.toByteArray();
	}

	  @Override
        public TransactionContext deserializeTransactionContext(byte[] bytes)
                        throws IOException {

                ByteBuffer bb = ByteBuffer.wrap(bytes);

                TransactionContext ctx = new TransactionContext();
		
		/* Dummy fnction, does not do anything */	
		return ctx;
	}

	/**
         * Called by network layer to commit a previous speculatively executed
         * batch.
         */
        @Override
        public void commitBatchOnDecision(final RequestId rId,
                        final TransactionContext ctx) {
                // TODO Auto-generated method stub
                // Validate sequence
                // If validated - commit -- Delete RequestId from
                // LocalTransactionManager.requestDirtycopyMap
                // else abort and retry

              /*  stmInstance.executeCommitRequest(new Runnable() {
                        public void run() {
                                // System.out.println("Comm");
                                onCommit(rId, ctx);

                                writeCount++;
                        }
                });*/
        }
     
	/**
         * Shuts down the executors if invoked. Here after no transaction can be
         * performed.
         * 
         * @return
         */
        public long shutDownExecutors() {
                return stmInstance.shutDownExecutors();
        }

        @Override
        public byte[] execute(byte[] value, int executeSeqNo) {
                // TODO Auto-generated method stub
                return null;
        }

        @Override
        public void askForSnapshot(int lastSnapshotNextRequestSeqNo) {
                // TODO Auto-generated method stub

        }

        @Override
        public void forceSnapshot(int lastSnapshotNextRequestSeqNo) {
                // TODO Auto-generated method stub

        }

        @Override
        public void updateToSnapshot(int nextRequestSeqNo, byte[] snapshot) {
                // TODO Auto-generated method stub

        }

        @Override
        public void addSnapshotListener(SnapshotListener listener) {
                // TODO Auto-generated method stub

        }

        @Override
        public void removeSnapshotListener(SnapshotListener listener) {
                // TODO Auto-generated method stub

        }
	
	public long getCount()
	{
		
		return completedCount;
	}
   

	     /**
         * This method fills the parameters in the request byte array for client for
         * the bank benchmark.
         * 
         * @param request
         *            : byte array
         * @param readOnly
         *            : boolean specifying what should be the transaction type
         */
        public byte[] createRequest(boolean readOnly, boolean TpccProfile,
                        int percent) 
	{
                byte[] request = new byte[DEFAULT_LENGTH];

                ByteBuffer buffer = ByteBuffer.wrap(request);
		buffer.put(READ_WRITE_TX);
		int command = 0;
		switch (command) 
		{
			case 0:
				buffer.put(TX_INCREMENT);
				break;
		}

                int count = 0;

                buffer.putInt(count);

                buffer.flip();
                return request;
        }

        @Override
        public Replica getReplica() {
                return this.replica;
        }


	public void setReplica(Replica replica) {
                this.replica = replica;
        }   
	 /* The XBatcher thread */

         private class XSpecThread extends Thread {
	
	//private final kryo kryo;


	@Override
        public void run() {


                int MaxSpec = stmInstance.getMaxSpec();
                final boolean retry = false;

                try
                {
                        Thread.sleep(10000);

                }
                catch (InterruptedException e1)
                {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                }

                while (true)
                {
                       
			RequestWrap requestWrap = stmInstance.XqueuePoll();
			if( requestWrap == null)
                        {
                                /* If request queue is empty, sleep for some time, then try again */
                                try
                                {
                                        Thread.sleep(10);

                                }
                                catch (InterruptedException e1)
                                {
                                        // TODO Auto-generated catch block
                                        e1.printStackTrace();
                                }
                                continue;
                        }


                   	final ClientRequest request = requestWrap.getRequest();
                        final int writenum = requestWrap.getRequestOrder();
			
			byte[] value = request.getValue();
			ByteBuffer buffer = ByteBuffer.wrap(value);
			final RequestId requestId = request.getRequestId();

			byte transactionType = buffer.get();
			byte command = buffer.get();
                	final int count = buffer.getInt();

                              
			if (transactionType == READ_ONLY_TX)
                        {
				System.out.println("Read operation invalid for Shared Counter");
			}
			else
			{        
				switch (command)
                                {
                                	case TX_INCREMENT:
					
                                        	//System.out.println("Delivery op, Thread is " + batchnum + "Tx is " + writenum);
                                        	requestIdValueMap.put(requestId, value);
                              			increment(request, retry, writenum);
                              	
					break;
				}
			}
                }/*End while */
        }/* End run*/
}



}
