package stm.impl;

import java.lang.Object;
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

import stm.transaction.AbstractObject;
import stm.transaction.TransactionContext;
import stm.impl.GlobalCommitManager;
import stm.impl.executors.ReadTransactionExecutor;
import stm.impl.executors.WriteTransactionExecutor;
import lsr.common.ClientRequest;
import lsr.common.Request;
import lsr.common.RequestId;
import lsr.service.STMService;

public class AbortEntry
{
	long commitVersion;
	
	
	public AbortEntry()
	{
		this.commitVersion = -1;
	}

	public AbortEntry(long version)
	{
		this.commitVersion = version;
	}
	
	public void setVersion(long version)
	{
		this.commitVersion = version;
	}
	
	public long getVersion()
	{
		return this.commitVersion;
	} 
}
