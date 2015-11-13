package stm.benchmark.tpcc;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
import stm.impl.SharedObjectRegistry;
import stm.transaction.AbstractObject;
import stm.transaction.ReadSetObject;
import stm.transaction.TransactionContext;

public class Tpcc extends STMService {

	protected static final int RETRY_COUNT = 2; // 100
	public static final byte TX_ORDER_STATUS = 0;
	public static final byte TX_DELIVERY = 1;
	public static final byte TX_STOCKLEVEL = 2;
	public static final byte TX_NEWORDER = 3;
	public static final byte TX_PAYMENT = 4;

	public final int DEFAULT_LENGTH = 6;

	private Random random = new Random();

	SharedObjectRegistry sharedObjectRegistry;
	PaxosSTM stmInstance;
	Replica replica;
	TpccMultiClient client;

	private SingleThreadDispatcher tpccSTMDispatcher;

	private String id;
	// Constants
	public int NUM_ITEMS = 50; // Correct overall # of items: 100,000
	public int NUM_WAREHOUSES = 5;
	public final int NUM_DISTRICTS = 10; // 4;
	public final int NUM_CUSTOMERS_PER_D = 30; // 30;
	public final int NUM_ORDERS_PER_D = 30; // 30;
	public final int MAX_CUSTOMER_NAMES = 1000; // 10;

	/* Values representing offsets for objIds when objIds are integers instead of Strings */
	
	public int warehouseStart;
	public int stockStart;
        public int districtStart;
        public int districtOffset;
        public int customerStart;
        public int historyStart;
        public int orderStart;
        public int orderlineStart;
        public int warehouseOffset;
	
	/** data collector variables **/
	static long startRead;
	static long startWrite;
	static long endRead;
	static long endWrite;
	private long lastReadCount = 0;
	private long lastWriteCount = 0;
	private long lastAbortCount = 0;
	private long lastXAbortCount = 0;
	private long lastFallBehindAbort = 0;
	private long lastCompletedCount = 0;
	private long lastRqAbortCount = 0;
	private long lastNetReqCount = 0;		// To monitor the network requests sent
	private long lastTcpReqCount = 0;
	private long lastRemCount = 0;
	private long lastSkipCount = 0;
	private long lastReadValCount = 0;
	private long lastInsertCount = 0;

	static long readCount = 0;
	static long writeCount = 0;
	static boolean startedSampling = false;
	static boolean endedSampling = false;

	/* For monitoring */
	private volatile long completedCount = 0;
	private volatile long committedCount = 0;
	private volatile long abortedCount = 0;
	private volatile long XabortedCount = 0;
	private volatile long FallBehindAbort = 0; 
	private volatile long randomabortCount = 0;
	private volatile long insertCount = 0;
	

	private volatile long skipCount = 0;
	private volatile long readValCount = 0;
	private volatile boolean skipFlag = false;
	private final Map<RequestId, byte[]> requestIdValueMap = new HashMap<RequestId, byte[]>();
	
	/* Hashmap created for Id and client request, may remove the Id Value hashmap entirely */
	private final ConcurrentHashMap<RequestId, ClientRequest> requestIdRequestMap = new ConcurrentHashMap<RequestId, ClientRequest>();

	private int localId;
	private int min;
	private int max;
	private int numReplicas;
	private int accessibleObjects;

	private int w_count;
	private int item_count;
	private int minW;
	private int maxW;
	private int accessibleWarehouses;
	private int MaxSpec;
	private int replicaCnt;	
	/* Temporary Varibles to inject abort in the commit thread */

        private int abortLimit;         /* Max number of aborts */



	XBatcher batcherTh = new XBatcher();
	MonitorThread monitorTh = new MonitorThread();

	/*************************************************************************
	 * This class is only for taking the readings from the experiment. The
	 * sampling thread is triggered when read/write count reaches a particular
	 * limit, it goes to sleep for 20 seconds and then it samples the reading.
	 * 
	 * @author sachin
	 * 
	 ************************************************************************/
	class MonitorThread extends Thread {
		public void run() {
			long start;
			long count = 0;
			long localReadCount = 0;
			long localWriteCount = 0;
			long localAbortCount = 0;
			long localRqAbortCount = 0;
			long localCompletedCount = 0;
			long localXAbortCount = 0;
			long localFallBehindAbort = 0;
			long localNetReqCount = 0;
			long localTcpReqCount = 0;
			long localRemCount = 0;
			long totalinRead = 0;
			long totalinWrite = 0;
			long totalCount = 0;
			long submitcount = 0;
			long localSkipCount = 0;
                        long localReadValCount = 0;
			long localInsertCount = 0;
			

			System.out
					.println("Read-Throughput/S  Write Throughput/S  CompletedCount/s Latency Aborts RQAborts SkipRequests  ReadSetValidationCount  InsertCount  RemovalCount  Time");
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			// Sample time is 20 seconds
			System.out.println(" ");
			while (count < 10) {
				startRead = System.currentTimeMillis();

				try {
					Thread.sleep(10000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				localReadCount = readCount;
				localWriteCount = committedCount;
				localCompletedCount = completedCount;
				localAbortCount = abortedCount;
				localRqAbortCount = stmInstance.getRqAbortCount();
				XabortedCount =   stmInstance.getXabortCount(); 
				localXAbortCount = XabortedCount;
				localSkipCount = skipCount;
				localReadValCount = readValCount;
				localInsertCount = stmInstance.getInsertCount();
				localRemCount = stmInstance.getRemCount();
				//FallBehindAbort = stmInstance.getFallBehindAbortCount();
				//localFallBehindAbort = FallBehindAbort;	
				//localNetReqCount = stmInstance.getReqCount();
				//localTcpReqCount = stmInstance.getTcpMsgCount();
				//localPropReqCount = stmInstance.getProposalLength();
				endRead = System.currentTimeMillis();
				client.collectLatencyData();
				totalCount = completedCount + readCount;
				totalinRead =  client.getReadCount();
				totalinWrite +=  client.getWriteCount();
				submitcount = totalinRead + totalinWrite;
			        //double temp = client.getWriteLatency();	
				//System.out.println("Submitted = " + submitcount + " Completed write count = " + completedCount + " Completed Read Count = " + readCount + " Total = " + totalCount);
				//System.out.println("Readcount = " + client.getReadCount() + " WriteCount = " + client.getWriteCount());
				System.out.format("%5d  %6d  %6d  %5.3f  %6d  %6d  %6d	 %6d   %6d    %6d  %6d\n",
						((localReadCount - lastReadCount) * 1000)
								/ (endRead - startRead),
						((localWriteCount - lastWriteCount) * 1000)
								/ (endRead - startRead),
						((localCompletedCount - lastCompletedCount) * 1000)
								/ (endRead - startRead),
						client.getWriteLatency(),
						((localAbortCount - lastAbortCount) * 1000)
								/ (endRead - startRead),
						(localRqAbortCount - lastRqAbortCount),
						//(localFallBehindAbort - lastFallBehindAbort),
						((localSkipCount - lastSkipCount) * 1000)
								/ (endRead - startRead),
						((localReadValCount - lastReadValCount) * 1000)
								/ (endRead - startRead),
						((localInsertCount - lastInsertCount) * 1000)
								/ (endRead - startRead),
						((localRemCount - lastRemCount) * 1000)
								/ (endRead - startRead),
						//((localPropReqCount - lastPropReqCount) * 1000)
						//		/ (endRead - startRead),
						(endRead - startRead));
//				System.out.println("LocalRemcount = " + localRemCount + " LastRemCount = " + lastRemCount);
						
				lastReadCount = localReadCount;
				lastWriteCount = localWriteCount;
				lastAbortCount = localAbortCount;
				lastXAbortCount = localXAbortCount;
				lastCompletedCount = localCompletedCount;
				lastRqAbortCount = localRqAbortCount;
				lastFallBehindAbort = localFallBehindAbort;
				lastSkipCount = localSkipCount;
                                lastReadValCount = localReadValCount;
				lastInsertCount = localInsertCount;
				//lastNetReqCount = localNetReqCount;
				lastRemCount = localRemCount;
				//lastPropReqCount = localPropReqCount;
				count++;
			
			}
			long triggers = stmInstance.getRqAbortTrigCount();
			//double ratio = (double)(localRqAbortCount / triggers);
			System.out.println("Submitted = " + submitcount + " Completed write count = " + completedCount + " Completed Read Count = " + readCount + " Total = " + totalCount);
			System.out.println("Comitted = " + committedCount + " Total Aborts = " + abortedCount + " Total RqAborts = " + localRqAbortCount + " Total Xaborts = " + localXAbortCount + " Total Commit Aborts = " + (abortedCount + localRqAbortCount) + " Random aborts = " +  randomabortCount + " FallBehindAborts = " + FallBehindAbort + " AbortTriggers = " + triggers) ;				  			  //System.out.format("RqAborts per abort = %4.4f\n",(double)(localRqAbortCount / triggers));
			try {
                                Thread.sleep(10000);
                        } catch (InterruptedException e1) {
                                // TODO Auto-generated catch block
                                e1.printStackTrace();
                        }
			/*
			for(int i = 0; i < replicaCnt; i++)
			{
				if(stmInstance.ConflictMapisEmpty(i))
					System.out.println("Replica "+ i + " map is empty");
				else
					System.out.println("Replicai " + i + " map is not empty" + " SIze is = " + stmInstance.ConflictMapSize(i));
			}*/
			System.exit(0);
		}
	}

	public void TpccInit(SharedObjectRegistry sharedObjectRegistry,
			PaxosSTM stminstance, int warehouseCount, int itemCount, int MaxSpec, int replicaCnt) {

		this.NUM_ITEMS = itemCount;
		this.NUM_WAREHOUSES = warehouseCount;

		this.sharedObjectRegistry = sharedObjectRegistry;
		this.stmInstance = stminstance;

		this.abortLimit = 0;
		this.MaxSpec = MaxSpec;
		this.replicaCnt = replicaCnt;
		/*
		for (int id = 0; id < NUM_ITEMS; id++) {
			final String myid = "i_" + Integer.toString(id);
			TpccItem item = new TpccItem(myid);
			this.sharedObjectRegistry.registerObjects(myid, item, MaxSpec);
		}
		// System.out.println("Size of shared Object Memory = " +
		// this.sharedObjectRegistry.getCapacity());

		for (int id = 0; id < NUM_WAREHOUSES; id++) {
			final String myid = "w_" + Integer.toString(id);
			TpccWarehouse warehouse = new TpccWarehouse(myid);
			this.sharedObjectRegistry.registerObjects(myid, warehouse, MaxSpec);

			for (int s_id = 0; s_id < NUM_ITEMS; s_id++) {
				final String smyid = myid + "_s_" + Integer.toString(s_id);
				TpccStock stock = new TpccStock(smyid);
				this.sharedObjectRegistry.registerObjects(smyid, stock, MaxSpec);
			}
			for (int d_id = 0; d_id < NUM_DISTRICTS; d_id++) {
				String dmyid = myid + "_" + Integer.toString(d_id);
				TpccDistrict district = new TpccDistrict(dmyid);
				this.sharedObjectRegistry.registerObjects(dmyid, district, MaxSpec);

				for (int c_id = 0; c_id < NUM_CUSTOMERS_PER_D; c_id++) {
					String cmyid = myid + "_c_" + Integer.toString(c_id);
					TpccCustomer customer = new TpccCustomer(cmyid);
					this.sharedObjectRegistry.registerObjects(cmyid, customer, MaxSpec);

					String hmyid = myid + "_h_" + Integer.toString(c_id);
					TpccHistory history = new TpccHistory(hmyid, c_id, d_id);
					this.sharedObjectRegistry.registerObjects(hmyid, history, MaxSpec);

				}
			}
			for (int o_id = 0; o_id < NUM_ORDERS_PER_D; o_id++) {
				String omyid = myid + "_o_" + Integer.toString(o_id);
				TpccOrder order = new TpccOrder(omyid);
				this.sharedObjectRegistry.registerObjects(omyid, order, MaxSpec);

				String olmyid = myid + "_ol_" + Integer.toString(o_id);
				TpccOrderline orderLine = new TpccOrderline(olmyid);
				this.sharedObjectRegistry.registerObjects(olmyid, orderLine, MaxSpec);
			}
		}*/
		for (int id = 0; id < NUM_ITEMS; id++) {
			final int myid = id;
			TpccItem item = new TpccItem(myid);
			this.sharedObjectRegistry.registerObjects(myid, item, MaxSpec);
		}
		int w_start = NUM_ITEMS;
		this.warehouseStart = w_start;		
		this.stockStart = 1;									// Relative to warehouse ID
		this.districtStart = NUM_ITEMS + 1;							// Relative to warehouse ID
		this.districtOffset = NUM_CUSTOMERS_PER_D * 2  + 1;		
		this.customerStart = 1;									// Relative to distrcit ID
		this.historyStart = NUM_CUSTOMERS_PER_D + 1;						// Relative to district ID
		this.orderStart = NUM_ITEMS + NUM_DISTRICTS + NUM_DISTRICTS * (NUM_CUSTOMERS_PER_D * 2) + 1;		// Relative to warehouse ID
		this.orderlineStart = this.orderStart + NUM_ORDERS_PER_D;				// Relative to warehouse ID
		this.warehouseOffset = NUM_ITEMS + NUM_DISTRICTS*this.districtOffset  + 2 * NUM_ORDERS_PER_D + 1;		
		/*	
		System.out.println("Warehouse start = " + this.warehouseStart);
		System.out.println("Warehouse offset = " + this.warehouseOffset);
		System.out.println("District start = " + (this.warehouseStart + this.districtStart));
		System.out.println("District offset = " + this.districtOffset);
		System.out.println("History start = " + (this.warehouseStart + this.districtStart + this.historyStart));
		System.out.println("Orderstart start = " + (this.warehouseStart + this.orderStart));
		System.out.println("Orderline start = " + (this.warehouseStart + this.orderlineStart));
		*/	
		int cur_id = w_start;
		for (int id = 0; id < NUM_WAREHOUSES; id++) {
		
			final int myid = cur_id;
			//System.out.println("Warehouse " + id + " Id = " + cur_id);
			cur_id = cur_id+1;
			TpccWarehouse warehouse = new TpccWarehouse(myid);
			this.sharedObjectRegistry.registerObjects(myid, warehouse, MaxSpec);

			for (int s_id = 0; s_id < NUM_ITEMS; s_id++) {
				final int smyid = cur_id ;
				cur_id = cur_id + 1;
				TpccStock stock = new TpccStock(smyid);
				this.sharedObjectRegistry.registerObjects(smyid, stock, MaxSpec);
			}
			for (int d_id = 0; d_id < NUM_DISTRICTS; d_id++) {
				int dmyid = cur_id;
				//System.out.println("District " + d_id + " Id = " + cur_id);
				cur_id = cur_id+1;
				TpccDistrict district = new TpccDistrict(dmyid);
				this.sharedObjectRegistry.registerObjects(dmyid, district, MaxSpec);
				//System.out.println("First customer Id = " + cur_id);	
				for (int c_id = 0; c_id < NUM_CUSTOMERS_PER_D; c_id++) {
					int cmyid = cur_id;
					cur_id = cur_id + 1;
					TpccCustomer customer = new TpccCustomer(cmyid);
					this.sharedObjectRegistry.registerObjects(cmyid, customer, MaxSpec);
				}
				//System.out.println("First history Id = " + cur_id);
				for (int c_id = 0; c_id < NUM_CUSTOMERS_PER_D; c_id++) {
					int hmyid = cur_id ;
					cur_id = cur_id + 1;
					TpccHistory history = new TpccHistory(hmyid, c_id, d_id);
					this.sharedObjectRegistry.registerObjects(hmyid, history, MaxSpec);

				}
				//System.out.println("Next district Id = " + cur_id);
			}
			//System.out.println("First order Id = " + cur_id);
			for (int o_id = 0; o_id < NUM_ORDERS_PER_D; o_id++) {
				int omyid = cur_id ;
				cur_id = cur_id + 1;
				TpccOrder order = new TpccOrder(omyid);
				this.sharedObjectRegistry.registerObjects(omyid, order, MaxSpec);
			}
			//System.out.println("First orderline Id = " + cur_id);
			for (int o_id = 0; o_id < NUM_ORDERS_PER_D; o_id++) {

				int olmyid = cur_id;
				cur_id = cur_id + 1;
				TpccOrderline orderLine = new TpccOrderline(olmyid);
				this.sharedObjectRegistry.registerObjects(olmyid, orderLine, MaxSpec);
			}
				//System.out.println("Next warehouse Id = " + cur_id);
		}
		

		/* Implementing Integer object ids in Tpcc */
		
		//System.out.println("Size of shared Object Registry = "
		//		+ this.sharedObjectRegistry.getCapacity());

		this.tpccSTMDispatcher = new SingleThreadDispatcher("TpccSTM");
		// this.tpccSTMDispatcher.start();
		batcherTh.start();
		monitorTh.start();
	}

	public void initRequests() {
		this.localId = ProcessDescriptor.getInstance().localId;
		this.numReplicas = ProcessDescriptor.getInstance().numReplicas;

	
		/*this.accessibleObjects = this.NUM_ITEMS / numReplicas;
		this.min = this.accessibleObjects * this.localId;
		this.max = (this.accessibleObjects * (this.localId + 1));
		this.item_count = (max - min)/MaxSpec;
		*/
		
		/* Have a shared portion of items */
		this.accessibleObjects = this.NUM_ITEMS / (numReplicas + 1);
		this.min = this.accessibleObjects * (this.localId + 1);
		this.max = (this.accessibleObjects * (this.localId + 2));
		this.item_count = (max - min)/MaxSpec;
		
		
		/*
		this.accessibleObjects = this.NUM_ITEMS;
		this.min = this.accessibleObjects * 0;
		this.max = (this.accessibleObjects *1);*/
		// System.out.println("O:" + this.accessibleObjects + "M:" + this.max +
		// "m:" + this.min);0 

	
		/*this.accessibleWarehouses = this.NUM_WAREHOUSES / numReplicas;
		this.minW = this.accessibleWarehouses * this.localId;
		this.maxW = (this.accessibleWarehouses * (this.localId + 1));
		this.w_count = (maxW - minW)/MaxSpec;
		*/
		/* Have a shared portion of warehouses */
		this.accessibleWarehouses = this.NUM_WAREHOUSES / (numReplicas + 1);
		this.minW = this.accessibleWarehouses * this.localId + 1;
		this.maxW = (this.accessibleWarehouses * (this.localId + 2));
		this.w_count = (maxW - minW)/MaxSpec;
		/*
		this.accessibleWarehouses = this.NUM_WAREHOUSES;
		this.minW = this.accessibleWarehouses * 0;
		this.maxW = (this.accessibleWarehouses * 1);
		*/
		//System.out.println("W:" + this.accessibleWarehouses + "MAX:" +
		//this.maxW +
		//"MIN:" + this.minW);
	}

	public void initClient(TpccMultiClient client) {
		this.client = client;
	}

	protected void orderStatus(ClientRequest cRequest, int count, boolean retry, int Tid) {
		int success = 0;
		RequestId requestId = cRequest.getRequestId();
		
		int thw_id = Tid - 1;
                int rwid = random.nextInt(w_count) + (thw_id * w_count) + minW;


                int myid = warehouseStart + (rwid * warehouseOffset);



		/*int myid = warehouseStart + 
				+ (random.nextInt(maxW - minW) + minW)*warehouseOffset;*/
		int d_id = myid + districtStart + districtOffset * random.nextInt(NUM_DISTRICTS);
		int cmyid = d_id + customerStart
				+ random.nextInt(NUM_CUSTOMERS_PER_D);

		TpccWarehouse warehouse = ((TpccWarehouse) stmInstance.open(myid,
				this.stmInstance.TX_READ_MODE, requestId,
				this.stmInstance.OBJECT_READ_MODE, retry, Tid));

		TpccCustomer customer = ((TpccCustomer) stmInstance.open(cmyid,
				this.stmInstance.TX_READ_MODE, requestId,
				this.stmInstance.OBJECT_READ_MODE, retry, Tid));

		final int omyid = myid + orderStart
				+ random.nextInt(NUM_ORDERS_PER_D);
		TpccOrder order = ((TpccOrder) stmInstance.open(omyid,
				this.stmInstance.TX_READ_MODE, requestId,
				this.stmInstance.OBJECT_READ_MODE, retry, Tid));

		float olsum = (float) 0;
		int i = 1;
		while (i < order.O_OL_CNT) {
			final int olmyid = myid + orderlineStart + i;
			TpccOrderline orderline = ((TpccOrderline) stmInstance.open(olmyid,
					this.stmInstance.TX_READ_MODE, requestId,
					this.stmInstance.OBJECT_READ_MODE, retry, Tid));

			if (orderline != null) {
				olsum += orderline.OL_AMOUNT;
				i += 1;
			}
		}
		sendReply(ByteBuffer.allocate(4).putInt(success).array(), cRequest);

	}

	protected void delivery(ClientRequest cRequest, int count, boolean retry, int Tid) {
		int success = 0;
		RequestId requestId = cRequest.getRequestId();
		/*
		Random randomGenerator = new Random();
		int randomInt = randomGenerator.nextInt(100);
		*/
	
		/*Integer clid = (int) (long) (requestId.getClientId()) - localId + minW;
		int myid = warehouseStart +
                                + clid *warehouseOffset;*/

		int thw_id = Tid - 1;
                int rwid = random.nextInt(w_count) + (thw_id * w_count) + minW;


                int myid = warehouseStart + (rwid * warehouseOffset);

		/*int tw_id = 0;
		
		if(randomInt < 15)
		{
			tw_id = random.nextInt(this.NUM_WAREHOUSES); 
		}	
		else
		{
			tw_id = random.nextInt(maxW - minW) + minW;
		}
		tw_id = random.nextInt(maxW - minW) + minW;
		final int w_id = tw_id;
		final int myid = warehouseStart +
                                + tw_id * warehouseOffset;*/

	 
		//System.out.println("delivery: " + myid);
		boolean xretry = true;
		while( xretry == true )
		{
		
			//System.out.println("Delivery retrying TX = " + Tid);
			xretry = false;
			TpccWarehouse warehouse = ((TpccWarehouse) stmInstance.Xopen(myid,
				this.stmInstance.TX_READ_WRITE_MODE, requestId,
				this.stmInstance.OBJECT_WRITE_MODE, retry, Tid));
			if (warehouse == null)
			{
				xretry = true;
				continue;
			}
		
			for (int d_id = 0; d_id < NUM_DISTRICTS; d_id++) {

				int dmyid = myid + districtStart + districtOffset * random.nextInt(NUM_DISTRICTS);
                		

                                int cmyid = dmyid + customerStart + random.nextInt(NUM_CUSTOMERS_PER_D);

				final int omyid = myid + orderStart
						+ random.nextInt(NUM_ORDERS_PER_D);
				//System.out.println("WarehouseId = " + myid + "DistrictId = " + dmyid + "Orderid = " + omyid);	

				TpccOrder order = ((TpccOrder) stmInstance.Xopen(omyid,
						this.stmInstance.TX_READ_WRITE_MODE, requestId,
						this.stmInstance.OBJECT_READ_MODE, retry, Tid));
				if (order == null)
                                {
					xretry = true;
					break;
				}

				float olsum = (float) 0;
				String crtdate = new java.util.Date().toString();
				int i = 1;
				while (i < order.O_OL_CNT) {
					if (i < NUM_ORDERS_PER_D) {
						final int olmyid =  myid + orderlineStart + i;
						TpccOrderline orderline = ((TpccOrderline) stmInstance
							.Xopen(olmyid, this.stmInstance.TX_READ_WRITE_MODE,
									requestId,
									this.stmInstance.OBJECT_READ_MODE, retry, Tid ));
					if(orderline == null)
					{
						xretry = true;
						break;
					}		
					if (orderline != null) {
						olsum += orderline.OL_AMOUNT;
						i += 1;
					}
				}
			
			}
			/* Check for abort in the NUM_DISTRICTS for loop */
			if(xretry == true)
			{
				break;
			}
			TpccCustomer customer = ((TpccCustomer) stmInstance.Xopen(cmyid,
					this.stmInstance.TX_READ_WRITE_MODE, requestId,
					this.stmInstance.OBJECT_WRITE_MODE, retry, Tid));
			if (customer == null)
                        {
			        xretry = true;
				break;	
			}
				
			customer.C_BALANCE += olsum;
			customer.C_DELIVERY_CNT += 1;
		}
	 
		// update shared copy completed-but-not-committed copy with the write
		// set
		//System.out.println("Calling updateCommit for " + myid);
		if((xretry == false) && (stmInstance.XCommitTransaction(requestId)))
			completedCount++;
		else
			xretry = true;
	}/* end while xretry */
	
		byte[] result = ByteBuffer.allocate(4).putInt(success).array();
		stmInstance.storeResultToContext(requestId, result);
	}

	protected void stockLevel(ClientRequest cRequest, int count, boolean retry, int Tid) {
		int success = 0;
		int i = 0;
		RequestId requestId = cRequest.getRequestId();
		
		Integer clid = (int) (long) (requestId.getClientId()) - localId + minW;
                int myid = warehouseStart + (clid * warehouseOffset);
		
		/*
		int thw_id = Tid - 1;

                int rwid = random.nextInt(w_count) + (thw_id * w_count) + minW;

                int myid = warehouseStart +
                                + rwid *warehouseOffset;*/
 
		/*int myid = warehouseStart +
                                + (random.nextInt(maxW - minW) + minW)*warehouseOffset;*/

		 //System.out.println("stockLevel: " + myid);
		while (i < 20) {

			/*************** Transaction start ***************/
			//int d_id = myid + districtStart + districtOffset * random.nextInt(NUM_DISTRICTS);
			final int omyid = myid + orderStart
					+ random.nextInt(NUM_ORDERS_PER_D);

			TpccWarehouse warehouse = ((TpccWarehouse) stmInstance.open(myid,
					this.stmInstance.TX_READ_MODE, requestId,
					this.stmInstance.OBJECT_READ_MODE, retry, Tid));

			TpccOrder order = ((TpccOrder) stmInstance.open(omyid,
					this.stmInstance.TX_READ_MODE, requestId,
					this.stmInstance.OBJECT_READ_MODE, retry, Tid));

			if (order != null) {
				int j = 1;
				while (j < order.O_OL_CNT) {
					if (j < NUM_ORDERS_PER_D) {
						final int olmyid = myid + orderlineStart
								+ j;
						TpccOrderline orderline = ((TpccOrderline) stmInstance
								.open(olmyid, this.stmInstance.TX_READ_MODE,
										requestId,
										this.stmInstance.OBJECT_READ_MODE,
										retry, Tid));
					}
					j += 1;
				}
			}

			/*************** Transaction end ***************/

			i += 1;
		}

		int k = 1;
		while (k <= 10) {
			int wid = warehouseStart
					+ random.nextInt(maxW - minW) + minW;
			if (k < NUM_ITEMS) {
				int smyid = wid + stockStart + k;
				TpccStock stock = ((TpccStock) stmInstance.open(smyid,
						this.stmInstance.TX_READ_MODE, requestId,
						this.stmInstance.OBJECT_READ_MODE, retry, Tid));

				// HyFlow.getLocator().open(smyid, "r");
				k += 1;
			} else
				k += 1;
		}
		sendReply(ByteBuffer.allocate(4).putInt(success).array(), cRequest);
	}

	protected void newOrder(ClientRequest cRequest, int count, boolean retry, int Tid) {

		int success = 0;
		RequestId requestId = cRequest.getRequestId();
		//Random randomGenerator = new Random();
		//int randomInt = randomGenerator.nextInt(100);
		
		/*Integer clid = (int) (long) (requestId.getClientId()) - localId + minW;
                int myid = warehouseStart +
                                + clid *warehouseOffset;*/
		
		int rwid = 0;
		int myid = 0;
		
		Random randomGenerator = new Random();
		int randomInt = randomGenerator.nextInt(100);
		
		int thw_id = Tid - 1;

		if(randomInt < 1)
		{
			rwid = random.nextInt(this.NUM_WAREHOUSES/(this.numReplicas + 1)); 
			//rwid = random.nextInt(this.NUM_WAREHOUSES); 
			myid = warehouseStart + rwid * warehouseOffset;
		}	
		else	
		{
                	rwid = random.nextInt(w_count) + (thw_id * w_count) + minW;

                	myid = warehouseStart + (rwid * warehouseOffset);
		}
		/*int tw_id = 0;
		
		if(randomInt < 15)
		{
			tw_id = random.nextInt(this.NUM_WAREHOUSES); 
		}	
		else
		{
			tw_id = random.nextInt(maxW - minW) + minW;
		}
		tw_id = random.nextInt(maxW - minW) + minW;
		final int w_id = tw_id;*/
		//final int w_id = random.nextInt(maxW - minW) + minW;
		//final int myid = warehouseStart + w_id * warehouseOffset;

		 //System.out.println("order: " + myid);

		                                                                                                                         // value
                boolean xretry = true;
                while( xretry == true)
		{
			xretry = false;
			TpccWarehouse warehouse = ((TpccWarehouse) stmInstance.Xopen(myid,
					this.stmInstance.TX_READ_WRITE_MODE, requestId,
					this.stmInstance.OBJECT_WRITE_MODE, retry, Tid));
			if(warehouse == null)
			{
				xretry = true;
				continue;
			}
			final int d_id = random.nextInt(NUM_DISTRICTS);
			final int dmyid = myid + districtStart + (d_id) * districtOffset;
			TpccDistrict district = ((TpccDistrict) stmInstance.Xopen(dmyid,
					this.stmInstance.TX_READ_WRITE_MODE, requestId,
					this.stmInstance.OBJECT_WRITE_MODE, retry, Tid));
			if(district == null)
                        {
                                xretry = true;
                                continue;
                        }

			double D_TAX = district.D_TAX;
			int o_id = district.D_NEXT_O_ID;
			district.D_NEXT_O_ID = o_id + 1;
			final int c_id = random.nextInt(NUM_CUSTOMERS_PER_D);
			final int cmyid = dmyid + customerStart + c_id;
			TpccCustomer customer = ((TpccCustomer) stmInstance.Xopen(cmyid,
				this.stmInstance.TX_READ_WRITE_MODE, requestId,
				this.stmInstance.OBJECT_WRITE_MODE, retry, Tid));
			if(customer == null)
                        {
                                xretry = true;
                                continue;
                        }

			double C_DISCOUNT = customer.C_DISCOUNT;
			String C_LAST = customer.C_LAST;
			String C_CREDIT = customer.C_CREDIT;

			// Create entries in ORDER and NEW-ORDER
			final int omyid = myid + orderStart +
					+ random.nextInt(NUM_ORDERS_PER_D);

			TpccOrder order = new TpccOrder(omyid);
			order.O_C_ID = c_id;
			order.O_CARRIER_ID = Integer.toString(random.nextInt(15)); // Check the
																	// specification
																	// for this
																	// value
			
			order.O_ALL_LOCAL = true;
			int i = 1;
			while (i <= order.O_CARRIER_ID.length()) {
				//final int i_id = random.nextInt((max - min)) + min;
				final int i_id = random.nextInt(item_count) + (thw_id * item_count) + min;

				int item_id = i_id;
				TpccItem item = ((TpccItem) stmInstance.Xopen(item_id,
						this.stmInstance.TX_READ_WRITE_MODE, requestId,
						this.stmInstance.OBJECT_WRITE_MODE, retry, Tid));
				if(item == null)
				{	
					xretry = true;
					break;			/*Break out of inner loop */
				}
					
				if (item == null) {
					System.out.println("Item is null >>>");
					//System.exit(-1);
					// return null;
				}
				
				float I_PRICE = item.I_PRICE;
				String I_NAME = item.I_NAME;
				String I_DATA = item.I_DATA;

				int olmyid =  myid + orderlineStart 
					+ (random.nextInt(1000) + NUM_ORDERS_PER_D);
				TpccOrderline orderLine = new TpccOrderline(olmyid);
				// TODO How to add the new object to shared object registry.
				// This should also be supported in STM framework
				orderLine.OL_QUANTITY = random.nextInt(1000);
				orderLine.OL_I_ID = i_id;
				orderLine.OL_SUPPLY_W_ID = myid;
				orderLine.OL_AMOUNT = (int) (orderLine.OL_QUANTITY * I_PRICE);
				orderLine.OL_DELIVERY_D = null;
				orderLine.OL_DIST_INFO = Integer.toString(d_id);
				i += 1;
			}
			

		// update shared copy completed-but-not-committed copy with the write
		// set
			if((xretry == false) && (stmInstance.XCommitTransaction(requestId)))
				completedCount++;
			else
				xretry = true;
		}
		byte[] result = ByteBuffer.allocate(4).putInt(success).array();
		stmInstance.storeResultToContext(requestId, result);
	}

	protected void payment(ClientRequest cRequest, int count, boolean retry, int Tid) {
		int success = 0;
		RequestId requestId = cRequest.getRequestId();
		final float h_amount = (float) (random.nextInt(500000) * 0.01);
	
		/* Access remote warehouse */
		Random randomGenerator = new Random();
		int randomInt = randomGenerator.nextInt(100);
		int rwid = 0;
		int myid = 0;
		
		
		/* Access a warehouse from the common pool */
		if(randomInt < 10)
		{
			rwid = random.nextInt(this.NUM_WAREHOUSES/(this.numReplicas + 1)); 
			//rwid = random.nextInt(this.NUM_WAREHOUSES); 
			myid = warehouseStart + rwid * warehouseOffset;
		}	
		else
		{
			/* Access a warehouse from the local pool */
			int thw_id = Tid - 1;
                	rwid = random.nextInt(w_count) + (thw_id * w_count) + minW;

                	myid = warehouseStart + rwid *warehouseOffset;
		}
		/* Access a warehouse from the local pool */
		/*int thw_id = Tid - 1;
                int rwid = random.nextInt(w_count) + (thw_id * w_count) + minW;

                myid = warehouseStart + (rwid * warehouseOffset);*/
		/*Integer clid = (int) (long) (requestId.getClientId()) - localId + minW;
		
                int myid = warehouseStart +
                                + clid *warehouseOffset;
		*/

		
		/*
		Random randomGenerator = new Random();
		int randomInt = randomGenerator.nextInt(100);
		int tw_id = 0;
		if(randomInt < 0)
		{
			tw_id = random.nextInt(this.NUM_WAREHOUSES); 
		}	
		else
		{
			tw_id = random.nextInt(maxW - minW) + minW;
		}
		final int w_id = tw_id;
		final int  myid = warehouseStart + w_id * warehouseOffset;*/
		final int d_id = myid + districtStart + districtOffset * random.nextInt(NUM_DISTRICTS);
		final int c_id = random.nextInt(NUM_CUSTOMERS_PER_D);
		final int  cmyid = d_id + customerStart + c_id;

		//System.out.println("payment: " + myid);
		boolean xretry = true;
		while( xretry == true)
		{ 
			xretry = false;
			// Open Wairehouse Table
			TpccWarehouse warehouse = ((TpccWarehouse) stmInstance.Xopen(myid,
				this.stmInstance.TX_READ_WRITE_MODE, requestId,
				this.stmInstance.OBJECT_WRITE_MODE, retry, Tid));
		
                        if(warehouse == null)
                        {
				xretry = true;
				continue;
			}

			warehouse.W_YTD += h_amount;

			// In DISTRICT table
			//final int d_id = random.nextInt(NUM_DISTRICTS);
			//final String dmyid = myid + "_" + Integer.toString(d_id);
			
			TpccDistrict district = ((TpccDistrict) stmInstance.Xopen(d_id,
					this.stmInstance.TX_READ_WRITE_MODE, requestId,
					this.stmInstance.OBJECT_WRITE_MODE, retry, Tid));
			if(district == null)
                	{
                         	xretry = true;
                         	continue;
                	}

			district.D_YTD += h_amount;

			TpccCustomer customer = ((TpccCustomer) stmInstance.Xopen(cmyid,
				this.stmInstance.TX_READ_WRITE_MODE, requestId,
				this.stmInstance.OBJECT_WRITE_MODE, retry, Tid));
			if( customer == null)
			{
				xretry = true;
				continue;
			}
			customer.C_BALANCE -= h_amount;
			customer.C_YTD_PAYMENT += h_amount;
			customer.C_PAYMENT_CNT += 1;

			// update shared copy completed-but-not-committed copy with the write
			// set
			if(xretry != true)
			{
				if(stmInstance.XCommitTransaction(requestId))
					completedCount++;
				else
					xretry = true;
			}
		}/* end while xretry */
		if((rwid < this.minW) || (rwid > this.maxW))
		{
			stmInstance.setCrossAccessContextFlag(requestId);
		}  
		byte[] result = ByteBuffer.allocate(4).putInt(success).array();
		stmInstance.storeResultToContext(requestId, result);
	}

	/**
	 * Pass reference to replice for sending a reply to client after read
	 * request is executed or write request is committed.
	 */
	public void setReplica(Replica replica) {
		this.replica = replica;
	}

	/**
	 * Used to execute read requests from clients locally.
	 */
	@Override
	public void executeReadRequest(final ClientRequest cRequest) {
		// TODO Auto-generated method stub
		
		tpccSTMDispatcher.submit(new Runnable() {
			public void run() {
				executeRequest(cRequest, false);
			}
		});
	}

	/**
	 * This method is used for three purposes. 1. For read transaction 2. For
	 * write transaction which is executed speculatively 3. For write
	 * transaction which is retied after commit failed
	 * 
	 * Retried method is executed by writeExecutor itself therefore there is no
	 * need to specifically execute the retry on writeExecutor
	 * 
	 * @param request
	 * @param retry
	 */
	public void executeRequest(final ClientRequest request, final boolean retry) {
/*		byte[] value = request.getValue();
		ByteBuffer buffer = ByteBuffer.wrap(value);
		final RequestId requestId = request.getRequestId();

		byte transactionType = buffer.get();
		byte command = buffer.get();
		final int count = buffer.getInt();

		if (transactionType == READ_ONLY_TX) {
			switch (command) {
			case TX_ORDER_STATUS:
				stmInstance.executeReadRequest(new Runnable() 
					{
						public void run() {
							orderStatus(request, count, retry);
							readCount++;
						}
				});
				break;

			case TX_STOCKLEVEL:
				stmInstance.executeReadRequest(new Runnable() {
					public void run() {
						stockLevel(request, count, retry);
						readCount++;
					}
				});
				break;

			default:
				System.out.println("Wrong RD command " + command
						+ " transaction type " + transactionType);
				break;
			}
		} else {
			switch (command) {
			case TX_DELIVERY: {
				if (retry == false) {
					requestIdValueMap.put(requestId, value);
					stmInstance.executeWriteRequest(new Runnable() {
						public void run() {
							delivery(request, count, retry);
							stmInstance.onExecuteComplete(request);
						}
					});
				} else {
					// delivery(request, count, retry);
				}
				break;
			}
			case TX_NEWORDER:
				if (retry == false) {
					requestIdValueMap.put(requestId, value);
					stmInstance.executeWriteRequest(new Runnable() {
						public void run() {
							newOrder(request, count, retry);
							stmInstance.onExecuteComplete(request);
						}
					});
				} else {
					// newOrder(request, count, retry);
				}
				break;
			case TX_PAYMENT:
				if (retry == false) {
					requestIdValueMap.put(requestId, value);
					stmInstance.executeWriteRequest(new Runnable() {
						public void run() {
							payment(request, count, retry);
							stmInstance.onExecuteComplete(request);
						}
					});
				} else {
					// payment(request, count, retry);
				}
				break;
			default:
				System.out.println("Wrong WR command " + command
						+ " transaction type " + transactionType);
				break;

			}
		}*/
		//System.out.println("ClientId = " + request.getRequestId().getClientId() + " Seq = " + request.getRequestId().getSeqNumber());
		if(request != null)
		{	
		
			//if(!requestIdRequestMap.containsKey(request.getRequestId())) 
			//{
			//RequestId rId = request.getRequestId();
			//requestIdRequestMap.put(rId,request);
			//System.out.println("Added request for  ClientId = " + rId.getClientId() + " SeqNumber = " + rId.getSeqNumber());
			stmInstance.xqueue(request);
		}
	}

	/**
	 * A common interface to send the reply to client request back to client
	 * through replica
	 * 
	 * @param result
	 * @param cRequest
	 */
	public void sendReply(byte[] result, ClientRequest cRequest) {
		// System.out.println("Sending reply to " +
		// cRequest.getRequestId().toString());
		// replica.replyToClient(result, cRequest);
	}

	@Override
	public void notifyCommitManager(Request request) {
		//System.out.print("Notify Comit Manager called");
		stmInstance.notifyCommitManager(request);
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

		stmInstance.executeCommitRequest(new Runnable() {
			public void run() {
				// System.out.println("Comm");
				onCommit(rId, ctx);

				writeCount++;
			}
		});
	}

	/*************************************************************************
	 * 
	 * @param requestId
	 * @param commandType
	 * @return
	 * 
	 *         This method commits the given transaction. It first validates the
	 *         readset and then after validating shadowcopy too, updates the
	 *         objects in SharedObjectRegistry with the shadowcopy. Finally it
	 *         removes all the data for optimistically executed transaction
	 *         (cleanup).
	 ************************************************************************/
	public void onCommit(RequestId requestId, TransactionContext ctx) {

		// Validate the transaction object versions or Decided InstanceIds and
		// sequence numbers
		// Check the version of object in stable copy and see if the current
		// shadowcopy version is +1
		// and InstanceId of shadowcopy matches with the stable copy.
		// Object object = null;
		// boolean retry = true;
		// RequestId requestId = cRequest.getRequestId();

		// Validate read set first
		//System.out.println("Commiting transaction");
		//Random randomGenerator = new Random();
		//int randomInt = randomGenerator.nextInt(100);
		//int abortflag = 0;
		/*int step = 10000;
		if(abortLimit <= 1000000)
		{	
			//System.out.println("AbortLimit = " + abortLimit);
			
			abortLimit++;
			abortflag = 1;
		}
		if(step != 0)
		{
			if((abortLimit % step) == 0)
				abort_random = false;
		}*/
		//System.out.println("Objects before commit");
		//stmInstance.printRWSets(ctx);
		boolean abort_random = false;
		long rId = requestId.getClientId() % (long)replicaCnt;
		int replicaId = (int)rId;
	
		if(!skipFlag)
		{	

			if((stmInstance.ConflictMapisEmpty(replicaId)) && (ctx.crossflag == false))
			{
				skipCount++;
				stmInstance.updateSharedObject(ctx, replicaId, skipFlag);
				committedCount++;
			
			}
			else
			{
			
				readValCount++;
				//if ((stmInstance.validateReadset(ctx)) && (abort_random == false)) 
				if (stmInstance.validateReadset(ctx))
				{
					stmInstance.updateSharedObject(ctx, replicaId, skipFlag);
					committedCount++;
					if(committedCount - skipCount > 150000)
					{
						skipFlag = true;
						/*for(int i = 0; i < replicaCnt; i++)
						{
							if(stmInstance.ConflictMapisEmpty(i))
								System.out.println("Replica "+ i + " map is empty");
							else
								System.out.println("Replicai " + i + " map is not empty" + " SIze is = " + stmInstance.ConflictMapSize(i));
						}*/
					
					}


				//System.out.println("Committing ClientId = " + requestId.getClientId() + " SeqNumber = " + requestId.getSeqNumber());
				//System.out.println("Objects after commit");
                		//stmInstance.printRWSets(ctx);
				//stmInstance.updateAbortMap(ctx);
				} 
				else 
				{
					//System.out.println("Aborting transaction randomly");
					//System.out.println("Aborting tx genuinely");
					//System.out.println("Aborting ClientId = " + requestId.getClientId() + " SeqNumber = " + requestId.getSeqNumber());	
					stmInstance.emptyWriteSet(ctx,false,replicaId, skipFlag);
					stmInstance.removeTransactionContext(requestId);
					//ClientRequest cRequest = requestIdRequestMap.get(requestId);
					/*if(cRequest == null)
					{
						if(requestIdRequestMap.isEmpty())
							System.out.println("Req Map is empty");
						System.out.println("Request is null");
						System.out.println("ClientId = " + requestId.getClientId() + " Seq = " + requestId.getSeqNumber());
						boolean ret = requestIdRequestMap.containsKey(requestId);
						if(ret == false)
					 		System.out.println("Request key is not present");
						else
							System.out.println("Request key is present");
						return;
					}*/ 
					//requestIdRequestMap.remove(requestId,cRequest);
					//requestIdValueMap.remove(requestId);
					byte[] value = requestIdValueMap.get(requestId);
					if(value != null)
					{
						ClientRequest cRequest = new ClientRequest(requestId , value);

						executeRequest(cRequest, false);
					}
					//System.out.println("Xcommit queue size is = " + stmInstance.getXCommitQueueSize());
					//stmInstance.printabortedObjects();
					abortedCount++;
					//stmInstance.abortXcomitted();
					return;
				}
			}
		}
		else
		{		
			readValCount++;
			//if ((stmInstance.validateReadset(ctx)) && (abort_random == false)) 
			if (stmInstance.validateReadset(ctx))
			{
				stmInstance.updateSharedObject(ctx, replicaId, skipFlag);
				committedCount++;

				//System.out.println("Committing ClientId = " + requestId.getClientId() + " SeqNumber = " + requestId.getSeqNumber());
				//System.out.println("Objects after commit");
                		//stmInstance.printRWSets(ctx);
				//stmInstance.updateAbortMap(ctx);
			} 
			else 
			{
				//System.out.println("Aborting transaction randomly");
				//System.out.println("Aborting tx genuinely");
				//System.out.println("Aborting ClientId = " + requestId.getClientId() + " SeqNumber = " + requestId.getSeqNumber());	
				stmInstance.emptyWriteSet(ctx, false, replicaId, skipFlag);
				stmInstance.removeTransactionContext(requestId);
				//ClientRequest cRequest = requestIdRequestMap.get(requestId);
				/*if(cRequest == null)
				{
					if(requestIdRequestMap.isEmpty())
						System.out.println("Req Map is empty");
					System.out.println("Request is null");
					System.out.println("ClientId = " + requestId.getClientId() + " Seq = " + requestId.getSeqNumber());
					boolean ret = requestIdRequestMap.containsKey(requestId);
					if(ret == false)
					 	System.out.println("Request key is not present");
					else
						System.out.println("Request key is present");
					return;
				}*/ 
				//requestIdRequestMap.remove(requestId,cRequest);
				//requestIdValueMap.remove(requestId);
				byte[] value = requestIdValueMap.get(requestId);
				if(value != null)
				{
					ClientRequest cRequest = new ClientRequest(requestId , value);
					executeRequest(cRequest, false);
				}
				//System.out.println("Xcommit queue size is = " + stmInstance.getXCommitQueueSize());
				//stmInstance.printabortedObjects();
				abortedCount++;
				//stmInstance.abortXcomitted();
				return;
			}
		}
		
		// committedCount++;
		// remove the entries for this transaction LTM (TransactionContext,
		// lastModifier)
		// object = stmInstance.getResultFromContext(requestId);
		// sendReply(stmInstance.getResultFromContext(requestId), cRequest);

		//System.out.println("Committing Tx");
		client.replyToClient(requestId);

		/*if(!stmInstance.ConflictMapisEmpty(replicaId))
		{
			stmInstance.updateConflictMap(ctx,replicaId);
		}*/
		stmInstance.removeTransactionContext(requestId);
		requestIdValueMap.remove(requestId);
		//requestIdRequestMap.remove(requestId,cRequest);
		// Object object = null;
		// boolean retry = false;
		// RequestId requestId = cRequest.getRequestId();
		// // Normally retries will be limited to only one retry, still kept
		// this loop
		// for(int i =0; i < RETRY_COUNT; i++) {
		//
		// if(retry == false) {
		// boolean valid = stmInstance.validateReadset(requestId);
		// if(valid) {
		// stmInstance.updateSharedObject(requestId);
		// break;
		// } else {
		// retry = true;
		// // Isn;t it needed to remove the previous content
		// executeRequest(cRequest, retry);
		// }
		// } else {
		// stmInstance.updateSharedObject(requestId);
		// }
		//
		// // // Validate read set first
		// // if(stmInstance.validateReadset(requestId)) {
		// // stmInstance.updateSharedObject(requestId);
		// // break;
		// // } else {
		// // retry = true;
		// // // Isn;t it needed to remove the previous content
		// // //stmInstance.removeTransactionContext(requestId);
		// // executeRequest(cRequest, retry);
		// // }
		// }
		//
		// // remove the entries for this transaction LTM (TransactionContext,
		// lastModifier)
		// object = stmInstance.getResultFromContext(requestId);
		// // byte command = getCommandName(requestIdValueMap.get(requestId));
		// sendReply(stmInstance.getResultFromContext(requestId), cRequest);
		//
		// stmInstance.removeTransactionContext(requestId);
		// requestIdValueMap.remove(requestId);
	}

	/**
	 * Read the command name from the client request byte array.
	 * 
	 * @param value
	 * @return
	 */
	public byte getCommandName(byte[] value) {
		ByteBuffer buffer = ByteBuffer.wrap(value);
		byte transactionType = buffer.get();
		byte command = buffer.get();
		buffer.flip();
		return command;

	}

	/************************************************************************
	 * 
	 * @param requestId
	 * 
	 *            This method rollsback all the changes performed on shadowcopy
	 *            of the optimistically executed transaction.
	 */
	public void rollback(RequestId requestId) {
		stmInstance.removeTransactionContext(requestId);
	}

	public void sanityCheck() {
		// TODO
	}

	@Override
	public byte[] serializeTransactionContext(TransactionContext ctx)
			throws IOException {
		   
		ArrayList<ReadSetObject> readset = ctx.getReadSet();

		Map<Integer, AbstractObject> writeset = ctx.getWriteSet();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteBuffer bb;
		int flag;
		/*Add boolean flag */
		if(ctx.crossflag == true)
		{	 	
			flag = 1;
		}
		else
		{
			flag = 0;
		}	
		bb = ByteBuffer.allocate(4);
		bb.putInt(flag);
		bb.flip();
		out.write(bb.array());

		bb = ByteBuffer.allocate(4);
		bb.putInt(readset.size());
		bb.flip();

		out.write(bb.array());
		for (ReadSetObject entry : readset) {
			int id = entry.objId;
			//byte[] idBytes = id.getBytes(Charset.forName("UTF-8"));
			/* Since id is int type, its size is 4 */
			int idLength = 4;
			bb = ByteBuffer.allocate(idLength + 4 + 8);

			bb.putInt(idLength);
			bb.putInt(id);
			bb.putLong(entry.version);

			bb.flip();
			out.write(bb.array());
		}

		bb = ByteBuffer.allocate(4);
		bb.putInt(writeset.size());
		bb.flip();

		// System.out.println("SW:" + writeset.size());

		out.write(bb.array());
		for (Map.Entry<Integer, AbstractObject> entry : writeset.entrySet()) {
			int id = entry.getKey();
			int idLength = 4;
			//byte[] idBytes = id.getBytes(Charset.forName("UTF-8"));

			ByteBuffer bb1 = ByteBuffer.allocate(idLength + 4);

			bb1.putInt(idLength);
			bb1.putInt(id);

			bb1.flip();
			out.write(bb1.array());

			Object object = entry.getValue();
			if (object instanceof TpccWarehouse) {
				bb = ByteBuffer.allocate(2 + 8);

				bb.putShort((short) 0);

				TpccWarehouse warehouse = (TpccWarehouse) object;

				bb.putLong(warehouse.getVersion());

				bb.flip();
				out.write(bb.array());

			} else if (object instanceof TpccCustomer) {
				bb = ByteBuffer.allocate(2 + 8 + 8 + 4 + 4 + 8);

				bb.putShort((short) 1);

				TpccCustomer customer = (TpccCustomer) object;

				bb.putDouble(customer.C_BALANCE);
				bb.putDouble(customer.C_YTD_PAYMENT);
				bb.putInt(customer.C_DELIVERY_CNT);
				bb.putInt(customer.C_PAYMENT_CNT);

				bb.putLong(customer.getVersion());

				bb.flip();
				out.write(bb.array());

			} else if (object instanceof TpccDistrict) {
				bb = ByteBuffer.allocate(2 + 4 + 8 + 8);

				bb.putShort((short) 2);

				TpccDistrict district = (TpccDistrict) object;

				bb.putInt(district.D_NEXT_O_ID);
				bb.putDouble(district.D_YTD);

				bb.putLong(district.getVersion());

				bb.flip();
				out.write(bb.array());

			} else if (object instanceof TpccItem) {
				bb = ByteBuffer.allocate(2 + 8);

				bb.putShort((short) 3);

				TpccItem item = (TpccItem) object;

				bb.putLong(item.getVersion());

				bb.flip();
				out.write(bb.array());

			} else if (object instanceof TpccOrder) {
				TpccOrder order = (TpccOrder) object;

				String str = order.O_CARRIER_ID;
				byte[] strBytes = str.getBytes();

				bb = ByteBuffer.allocate(2 + 4 + 4 + strBytes.length + 1 + 8);

				bb.putShort((short) 4);

				bb.putInt(order.O_C_ID);

				bb.putInt(strBytes.length);
				bb.put(strBytes);

				bb.put(new byte[] { (byte) (order.O_ALL_LOCAL ? 1 : 0) });

				bb.putLong(order.getVersion());

				bb.flip();
				out.write(bb.array());

			} else if (object instanceof TpccOrderline) {

				TpccOrderline orderline = (TpccOrderline) object;

				String str = orderline.OL_DELIVERY_D;
				byte[] strBytes = str.getBytes();

				String str1 = orderline.OL_DIST_INFO;
				byte[] strBytes1 = str1.getBytes();

				bb = ByteBuffer.allocate(2 + 4 + 4 + 4 + 4 + 4
						+ strBytes.length + 4 + strBytes1.length + 8);

				bb.putShort((short) 5);

				bb.putInt(orderline.OL_QUANTITY);
				bb.putInt(orderline.OL_I_ID);
				bb.putInt(orderline.OL_SUPPLY_W_ID);
				bb.putInt(orderline.OL_AMOUNT);

				bb.putInt(strBytes.length);
				bb.put(strBytes);

				bb.putInt(strBytes1.length);
				bb.put(strBytes1);

				bb.putLong(orderline.getVersion());

				bb.flip();
				out.write(bb.array());

			} else if (object instanceof TpccStock) {
				bb = ByteBuffer.allocate(2 + 8);

				bb.putShort((short) 6);

				TpccStock stock = (TpccStock) object;

				bb.putLong(stock.getVersion());

				bb.flip();
				out.write(bb.array());

			} else {
				System.out
						.println("Tpcc Object serialization: object not defined");
			}
		}

		return out.toByteArray();
	}

	@Override
	public TransactionContext deserializeTransactionContext(byte[] bytes)
			throws IOException {

		ByteBuffer bb = ByteBuffer.wrap(bytes);

		TransactionContext ctx = new TransactionContext();

		int flag = bb.getInt();
		if(flag == 0)
		{
			ctx.crossflag = false;
		}
		else
		{
			ctx.crossflag = true;
		}
	


		int readsetSize = bb.getInt();
		for (int i = 0; i < readsetSize; i++) {
			byte[] value = new byte[bb.getInt()];
			//bb.get(value);

			int id = bb.getInt();
			//String id = new String(value, Charset.forName("UTF-8"));

			long version = bb.getLong();

			TpccItem object = new TpccItem(id);
			object.setVersion(version);

			ctx.addObjectToReadSet(id, object);
		}

		int writesetSize = bb.getInt();
		for (int i = 0; i < writesetSize; i++) {
			byte[] value = new byte[bb.getInt()];
			//bb.get(value);
			int id = bb.getInt();
			//String id = new String(value, Charset.forName("UTF-8"));

			short type = bb.getShort();

			switch (type) {
			case 0: {
				TpccWarehouse object = new TpccWarehouse(id);

				object.setVersion(bb.getLong());

				ctx.addObjectToWriteSet(id, object);
				break;
			}
			case 1: {
				TpccCustomer object = new TpccCustomer(id);

				object.C_BALANCE = bb.getDouble();
				object.C_YTD_PAYMENT = bb.getDouble();
				object.C_DELIVERY_CNT = bb.getInt();
				object.C_PAYMENT_CNT = bb.getInt();

				object.setVersion(bb.getLong());

				ctx.addObjectToWriteSet(id, object);
				break;
			}
			case 2: {
				TpccDistrict object = new TpccDistrict(id);

				object.D_NEXT_O_ID = bb.getInt();
				object.D_YTD = bb.getDouble();

				object.setVersion(bb.getLong());

				ctx.addObjectToWriteSet(id, object);
				break;
			}
			case 3: {
				TpccItem object = new TpccItem(id);

				object.setVersion(bb.getLong());

				ctx.addObjectToWriteSet(id, object);
				break;
			}
			case 4: {
				TpccOrder object = new TpccOrder(id);

				object.O_C_ID = bb.getInt();

				byte[] v = new byte[bb.getInt()];
				bb.get(v);

				String v_string = new String(value, Charset.forName("UTF-8"));

				object.O_CARRIER_ID = v_string;

				object.O_ALL_LOCAL = (bb.get() != 0);

				object.setVersion(bb.getLong());

				ctx.addObjectToWriteSet(id, object);
				break;
			}
			case 5: {
				TpccOrderline object = new TpccOrderline(id);

				object.OL_QUANTITY = bb.getInt();
				object.OL_I_ID = bb.getInt();
				object.OL_SUPPLY_W_ID = bb.getInt();
				object.OL_AMOUNT = bb.getInt();

				byte[] v = new byte[bb.getInt()];
				bb.get(v);
				String v_string = new String(value, Charset.forName("UTF-8"));
				object.OL_DELIVERY_D = v_string;

				v = new byte[bb.getInt()];
				bb.get(v);
				v_string = new String(value, Charset.forName("UTF-8"));
				object.OL_DIST_INFO = v_string;

				object.setVersion(bb.getLong());

				ctx.addObjectToWriteSet(id, object);
				break;
			}
			case 6: {
				TpccStock object = new TpccStock(id);

				object.setVersion(bb.getLong());

				ctx.addObjectToWriteSet(id, object);
				break;
			}
			default:
				System.out.println("Invalid Object Type");
			}

		}

		return ctx;
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
			int percent) {
		byte[] request = new byte[DEFAULT_LENGTH];

		ByteBuffer buffer = ByteBuffer.wrap(request);

		if (TpccProfile) {
			// TPCC workload
			// int percent = random.nextInt(100);
			if (percent < 4) {
				buffer.put(READ_ONLY_TX);
				buffer.put(TX_ORDER_STATUS);
			} else if (percent < 8) {
				buffer.put(READ_ONLY_TX);
				buffer.put(TX_STOCKLEVEL);
			} else if (percent < 12) {
				buffer.put(READ_WRITE_TX);
				buffer.put(TX_DELIVERY);
			} else if (percent < 55) {
				buffer.put(READ_WRITE_TX);
				buffer.put(TX_PAYMENT);
			} else {
				buffer.put(READ_WRITE_TX);
				buffer.put(TX_NEWORDER);
			}
		} else {
			if (readOnly) {
				buffer.put(READ_ONLY_TX);
				int command = random.nextInt(2);
				switch (command) {
				case 0:
					buffer.put(TX_ORDER_STATUS);
					break;
				case 1:
					buffer.put(TX_STOCKLEVEL);
					break;
				}
			} else {
				buffer.put(READ_WRITE_TX);
				int command = random.nextInt(3);
				switch (command) {
				case 0:
					buffer.put(TX_DELIVERY);
					break;
				case 1:
					buffer.put(TX_NEWORDER);
					break;
				case 2:
					buffer.put(TX_PAYMENT);
					break;
				}
			}
		}

		int count = random.nextInt(max - min) + min;

		buffer.putInt(count);

		buffer.flip();
		return request;
	}

	@Override
	public Replica getReplica() {
		return this.replica;
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

	public Object getId() {
		return id;
	}


     private class XBatcher extends Thread {
        //private final kryo kryo;
	
        @Override
        public void run() {


                int MaxSpec = stmInstance.getMaxSpec();
                final boolean retry = false;

                ArrayList<ClientRequest> reqarray = new ArrayList<ClientRequest>(MaxSpec);
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
                        /* Drain the request queue */
                        int drain = stmInstance.XqueuedrainTo(reqarray,MaxSpec);

                        /* Reset lastXCommit */
                        stmInstance.resetLastXcommit();
                        AtomicInteger Tid = new AtomicInteger(0);
                        final CyclicBarrier barrier = new CyclicBarrier(drain + 1);
                        /*if(drain > 0)
                        {
                            System.out.println("drain = " + drain);
                        }*/
                        int r_count = 0;
                        while(r_count < drain)
                        {
                                final ClientRequest request = reqarray.remove(0);
                                Runnable task = new ExecThread(request, barrier, Tid);
                                stmInstance.executeRequest(task);
                                r_count++;
                        } /*End inner while */
                        /* Wait for all the thread to join */
                        try
                        {
                                //System.out.println("XBatcher thread  joined, Threads waiting  = "  + barrier.getNumberWaiting());
                                barrier.await();
                                //if( drain != 0 )
                                //      System.out.println("All " + drain + " threads joined");
                        }
                        catch(InterruptedException ex)
                        {
                                System.out.println("transfer gave barrier exception");

                        }
                        catch(BrokenBarrierException ex)
                        {

                                System.out.println("transfer gave broken barrier exception");
                        }

                        /* Sanity check */
                        /*if(checkBalances() == true)
                                System.out.println("Sanity check passed");
                        else
                                System.out.println("Sanity check failed");*/
                        /* Signal the globalCommitManager */
                        stmInstance.addBatchToCommitManager();
                        reqarray.clear();
                }/*End outer while */
        }/* End run*/
}


public class ExecThread implements Runnable
{
        private ClientRequest request;
        CyclicBarrier barrier;
        AtomicInteger Tid;
        public ExecThread( ClientRequest cRequest, CyclicBarrier barrier, AtomicInteger Tid)
        {
                this.request = cRequest;
                this.barrier = barrier;
                this.Tid = Tid;
	}

        @Override
        public void run()
        {

                byte[] value = request.getValue();
                ByteBuffer buffer = ByteBuffer.wrap(value);
                final RequestId requestId = request.getRequestId();

                byte transactionType = buffer.get();
                byte command = buffer.get();
                final int count = buffer.getInt();
                boolean retry = false;
                if (transactionType == READ_ONLY_TX)
                {
                        switch (command)
                        {
                                case TX_ORDER_STATUS:
                                        orderStatus(request, count, retry, 0);
                                        try
                                        {
                                                //System.out.println("Thread joined = " + batchnum + " Threads waiting = " + barrier.getNumberWaiting());
                                                barrier.await();
                                        }
                                        catch(InterruptedException ex)
                                        {
                                                System.out.println("getBalance gave barrier exception");
                                        }
                                        catch(BrokenBarrierException ex)
                                        {
                                                System.out.println("getBalance gave brokenbarrier exception");
                                        }


                                        readCount++;
                                break;
                                case TX_STOCKLEVEL:
                                        stockLevel(request, count, retry, 0);
                                        try
                                        {
                                                //System.out.println("Thread joined = " + batchnum + " Threads waiting = " + barrier.getNumberWaiting());
                                                barrier.await();
                                        }
                                        catch(InterruptedException ex)
                                        {
                                                System.out.println("getBalance gave barrier exception");
                                        }
                                        catch(BrokenBarrierException ex)
                                        {
                                                System.out.println("getBalance gave brokenbarrier exception");
                                        }
                                        readCount++;
                                break;

                                default:
                                        System.out.println("Wrong RD command " + command
                                                + " transaction type " + transactionType);
                                break;
                        }
                }

                else
                {
                        int writenum = Tid.incrementAndGet();

                        switch (command)
                        {
                                case TX_DELIVERY:
                                        //System.out.println("Delivery op, Thread is " + batchnum + "Tx is " + writenum);
                                        requestIdValueMap.put(requestId, value);
                                        delivery(request, count, retry, writenum);
                                        /* Add to completed request batch */
                                        stmInstance.addToCompletedBatch(request, writenum );
                                        try
                                        {
                                                //System.out.println("Thread joined = " + batchnum + " Threads waiting = " + barrier.getNumberWaiting());
                                                barrier.await();
                                        }
                                        catch(InterruptedException ex)
                                        {
                                                System.out.println("getBalance gave barrier exception");
                                        }
                                        catch(BrokenBarrierException ex)
                                        {
                                                System.out.println("getBalance gave brokenbarrier exception");
                                        }
                                break;

                                case TX_NEWORDER:

                                        //System.out.println("NewOrder op, Thread is " + batchnum + "Tx is " + writenum);
                                        requestIdValueMap.put(requestId, value);
                                        newOrder(request, count, retry, writenum);
                                        stmInstance.addToCompletedBatch(request, writenum );
                                        try
                                        {
                                                //System.out.println("Thread joined = " + batchnum + " Threads waiting = " + barrier.getNumberWaiting());
                                                barrier.await();
                                        }
                                        catch(InterruptedException ex)
                                        {
                                                System.out.println("getBalance gave barrier exception");
                                        }
                                        catch(BrokenBarrierException ex)
                                        {
                                                System.out.println("getBalance gave brokenbarrier exception");
                                        }

                                break;

                               case TX_PAYMENT:
                                        //System.out.println("Payment op, Thread is " + batchnum + "Tx is " + writenum);
                                        requestIdValueMap.put(requestId, value);
                                        payment(request, count, retry, writenum);
                                        stmInstance.addToCompletedBatch(request, writenum );
                                        try
                                        {
                                                //System.out.println("Thread joined = " + batchnum + " Threads waiting = " + barrier.getNumberWaiting());
                                                barrier.await();
                                        }
                                        catch(InterruptedException ex)
                                        {
                                                System.out.println("getBalance gave barrier exception");
                                        }
                                        catch(BrokenBarrierException ex)
                                        {
                                                System.out.println("getBalance gave brokenbarrier exception");
                                        }
                                break;

                                default:
                                        System.out.println("Wrong WR command " + command
                                                + " transaction type " + transactionType);
                                break;

                        }
                }
        }


}
}
