package stm.impl;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import stm.impl.objectstructure.SharedObject;
import stm.transaction.AbstractObject;



public class SharedObjectRegistry {
	// This class is used for global access to shared objects. All objects are 
	// registered to this registry after they are created. This also serves as
	// the stable copy of objects.
	
	private ConcurrentMap<Integer, SharedObject> registry;
	private volatile int snapshot;
	private int MaxSpec;
	
	public SharedObjectRegistry(int capacity, int MaxSpec) {
		registry = new ConcurrentHashMap<Integer, SharedObject>(capacity);
		snapshot = 0;
		this.MaxSpec = MaxSpec;
	}
	
	public void registerObjects(int Id, AbstractObject object, int MaxSpec) {
		registry.put(Id, new SharedObject(object, MaxSpec));
	}
	
	public AbstractObject getObject(int Id, String mode, int transactionSnapshot, boolean retry) {

		AbstractObject object = null;
		if(mode == "rw") {
			// Object requested for write operation
			// It can either be in non-committed state from some previous non-committed transaction
			// or it can be in committed state 
			if(retry) {
				// If transaction is retried, it should get object from the last committed version, 
				// and not from the last completed since completed might be some version later than 
				// this transaction in batch (or other following batch in same/following instance)
				object = getLatestCommittedObject(Id);
			} else {
				object = registry.get(Id).getLatestCompletedObject();
			}
			if(object != null) {
				return object;
			} else {
				return getLatestCommittedObject(Id);
			}
		} else {
			// Object requested for read operation
			return registry.get(Id).getLatestCommittedObject(transactionSnapshot);
		}
	}
	
	public AbstractObject getXObject(int Id, String mode, int transactionSnapshot, PaxosSTM stmInstance, int Tid, boolean retry) {

		AbstractObject object = null;
		SharedObject shared = null;
		int readers[] = new int [MaxSpec];
		if(mode == "rw") {
			// Object requested for write operation
			// It can either be in non-committed state from some previous non-committed transaction
			// or it can be in committed state 
			if(retry) {
				// If transaction is retried, it should get object from the last committed version, 
				// and not from the last completed since completed might be some version later than 
				// this transaction in batch (or other following batch in same/following instance)
				object = getLatestCommittedObject(Id);
			} else {
				
				shared = registry.get(Id);
				//System.out.println("GetXObject Trasaction " + Tid + "trying to be the owner of " + Id + "Current owner = " + shared.getOwner());
				//shared.lock_object(Tid);
			//	System.out.println("GetXObject Trasaction " + Tid + "Locked " + Id);
				/* Check if still the owner */
				/*
				if(shared.getOwner() != Tid)
				{	
					/* SInce we are no longer the owner, we needn't bother with resetting the owner field*/
				/*	stmInstance.SetAbortArray(Tid);
					return null;
				}
				stmInstance.XabortReaders(Id, Tid);
				if(stmInstance.CheckXaborted(Tid) == true)
					return null;*/

				object = shared.getLatestCompletedObject();
			}
			if(object != null) {
				return object;
			} else {
				object = getLatestCommittedObject(Id);
				/*if(shared.getOwner() != Tid)
				{
					System.out.println("ObjId = " + Id+ " Tid of the owner is = " + shared.getOwner() + " Tid of this Tx is = " + Tid);
					if(stmInstance.CheckXaborted(Tid) == true)
						System.out.println("Tx " +  Tid + " is aborted");
				}*/
				return object;
			}
		} else {
			// Object requested for read operation
			return registry.get(Id).getLatestCommittedObject(transactionSnapshot);
		}
	}
	
	public AbstractObject getLatestCommittedObject(int Id) {
		return registry.get(Id).getLatestCommittedObject();
	}
	
	public int getSnapshot() {
		return snapshot;
	}
	
	public int getNextSnapshot() {
		snapshot++;
		return snapshot;
	}	
	
	public int getCapacity() {
		return registry.size();
	}
	
	public void updateCompletedObject(int Id, AbstractObject object) {	
		registry.get(Id).updateCompletedObject(object);
	}
	
	public void updateObject(int Id, AbstractObject object, int timeStamp) {	
		registry.get(Id).updateCommittedObject(object, timeStamp);
	}

	public int[] getReaderArray(int Id)
	{
		return registry.get(Id).getReaderArray();
	}
	public void clearReader(int Id, int Tid)
        {
              registry.get(Id).clearReader(Tid);
        }

	public int getOwner(int Id)
        {
                return (registry.get(Id).getOwner());
        }

	public void clearOwner(int Id)
        {
                registry.get(Id).clearOwner();
        }
	

	public boolean compareAndSetOwner(int Id, int prev,int  curr)
        {
                return(registry.get(Id).compareAndSetOwner(prev,curr));
        }

	public void setReader(int Id, int Tid)
        {
                registry.get(Id).setReader(Tid);
        }
	
	public long getVersion(int Id)
	{
		return registry.get(Id).getVersion();
	}	

}
