package stm.benchmark.counter;

import java.io.IOException;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import lsr.common.ClientRequest;
import lsr.common.ProcessDescriptor;
import lsr.common.RequestId;
import lsr.paxos.ReplicationException;
import lsr.paxos.replica.IdGenerator;
import lsr.paxos.replica.SimpleIdGenerator;

public class SCMultiClient {
        private Vector<ClientThread> clients = new Vector<ClientThread>();
        private final int clientCount;
        private final int requests;
        private final SharedCounter sc;

        ConcurrentHashMap<Long, RequestId> pendingClientRequestMap = new ConcurrentHashMap<Long, RequestId>();


        private int replicaId;

	class ClientThread extends Thread {
                private final long clientId;
                private int sequenceId = 0;

                private ArrayBlockingQueue<Integer> sends;
                private final Random random;


                public ClientThread(long clientId) throws IOException {
                        this.clientId = clientId;

                        sends = new ArrayBlockingQueue<Integer>(128);
                        this.random = new Random();
                }


                @Override
                public void run() {
                        boolean readOnly = true;
                        boolean readWrite = !readOnly;
                        try {
                                Integer count;
                                Integer submit = 0;
                		Thread.sleep(20000);

                                count = sends.take();
                                //System.out.println("Count = " + count);
                                for (int i = 0; i < count; i++) {
                                        
					byte[] reqBytes;
                                        ClientRequest request;
                                        RequestId requestId;
                                        long start = System.currentTimeMillis();
                                                        
					reqBytes = sc.createRequest(readWrite,
                                                                        false, 0);
                                        request = new ClientRequest(nextRequestId(),
                                                                        reqBytes);
                                  	requestId = request.getRequestId();
                                  	pendingClientRequestMap.put(
                                                    		requestId.getClientId(), requestId);

                                                        synchronized (requestId) {
                                                                sc.executeRequest(request, false);
                                                                requestId.wait(3000);
                                                        }
                                                }
                                       
                                        // if(i%100 == 0)
                                        // System.out.println("read-Count = " + readCount + " " +
                                        // writeCount + " " + readLatency + " " + writeLatency);

                                }
				catch (InterruptedException e) {
                                	e.printStackTrace();
                        	}
                }
	 
                public void execute(int count) throws InterruptedException {
                        sends.put(count);
                }
	
		private RequestId nextRequestId() {
                        return new RequestId(clientId, ++sequenceId);
                }


	}
        
	public SCMultiClient(int clientCount, int requests, SharedCounter sc) 
	{
                this.sc = sc;
                this.clientCount = clientCount;
                this.requests = requests;
                sc.initClient(this);
        }

        public void run() throws IOException, ReplicationException,
                        InterruptedException {

                execute(this.clientCount, this.requests);
        }

        private void execute(int clientCount, int requests)
                        throws ReplicationException, IOException, InterruptedException {

                int dummyCount = 0;

                IdGenerator idGenerator = new SimpleIdGenerator(
                                ProcessDescriptor.getInstance().localId,
                                ProcessDescriptor.getInstance().numReplicas);
                for (int i = 0; i < clientCount; i++) {
                        ClientThread client = new ClientThread(idGenerator.next());
                        clients.add(client);
                        client.start();
                }

                // startTime = System.currentTimeMillis();

                // prime the STM and OS-Paxos
                for (int i = 0; i < clientCount; i++) {
                        clients.get(i).execute(requests);
                }

}
}
