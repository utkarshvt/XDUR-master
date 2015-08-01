package stm.impl;

import java.lang.Object;
import java.util.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.*;
import com.esotericsoftware.kryo.pool.KryoCallback;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentSkipListMap;
import java.lang.Integer;
import java.lang.Comparable;



import stm.transaction.AbstractObject;
import stm.transaction.TransactionContext;
import stm.transaction.ReadSetObject;
import stm.impl.GlobalCommitManager;
import stm.impl.executors.ReadTransactionExecutor;
import stm.impl.executors.WriteTransactionExecutor;
import lsr.common.ClientRequest;
import lsr.common.Request;
import lsr.common.RequestId;
import lsr.service.STMService;

/* This class is used to act as wrapper to ClientRequest class object
 * This is done so as to create a natural order among the ClientRequests on
 * Request Queue. The class contains a ClientRequest object and an integer
 * value which represents the Request number (depending upin the value of
 * GlobalRequestId observed by the request at the time of its creation ).
 * The Request number acts as the key for natural ordering. This functionality
 * could have been implemented in the ClientRequest class itself, but the class
 * already has a equals() method based on different keys */

public class RequestWrap implements Comparable <RequestWrap>
{
	private ClientRequest Request;
	public int requestOrder;
	
	public RequestWrap(ClientRequest req, int order)
	{
		this.Request = req;
		this.requestOrder = order;
	}
		
	public int compareTo(RequestWrap other) 
	{
    		return Integer.compare(this.requestOrder, other.requestOrder);

	}
	
	public void setRequest(ClientRequest req)
	{
		this.Request = req;
	}
	
	public ClientRequest getRequest()
	{
		return (this.Request);
	}

	public void setRequestOrder(int order)
	{
		this.requestOrder = order;
	}

	public int getRequestOrder()
	{
		return (this.requestOrder) ;
	}
	
}
