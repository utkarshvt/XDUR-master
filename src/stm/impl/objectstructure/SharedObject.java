package stm.impl.objectstructure;
import stm.transaction.AbstractObject;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedObject {
	
	/* Fields added for parallelization */
        AtomicInteger owner;
        private int readers_array[];
	private int MaxSpec;
	
	private CommittedObjects committedObjects; 
	private CompletedObject completedObject; 
	
	public SharedObject(AbstractObject object, int MaxSpec) {
		
                owner = new AtomicInteger(0);
                readers_array = new int[MaxSpec];
                this.MaxSpec = MaxSpec;
	
		committedObjects = new CommittedObjects(object);
		completedObject = new CompletedObject();	
	

	}
	

	public AbstractObject getLatestCommittedObject(int transactionSnapshot) {
		return committedObjects.getLatestObject(transactionSnapshot);
	}
	
	public AbstractObject getLatestCommittedObject() {
		return committedObjects.getLatestObject();
	}
	
	public AbstractObject getLatestCompletedObject() {
		return completedObject.getCurrentObject();
	}
	
	public void updateCompletedObject(AbstractObject object) {
		completedObject.setCurrentObject(object);
	}
	
	public void updateCommittedObject(AbstractObject object, int timeStamp) {
		committedObjects.addCommittedObject(object, timeStamp);
	}

	

	public void lock_object(int caller)
        {
                int curr;
                int it = 0;
                boolean flag = false;
                while(true)
                {
                        it++;
                        curr = this.owner.get();
                         /*if(flag == false)
                         {      
                                System.out.println("Number of iteration = " + it + " Owner = " + caller + " Prev owner = " + curr);
                                flag = true; 
                        }*/
                        if((curr == 0) || (caller <= curr))
                        {
                                if( this.owner.compareAndSet(curr, caller) == true)
                                {
                                        //System.out.println("Iterations in while = " + it + " Owner = " + caller + " Prev owner = " + curr);
                                        setReader(caller);
                                        return;
                                }
                        
                        }
                }
        }
       

	public void unlock_object(int caller)
        {
                if(this.owner.compareAndSet(caller,0))
                        return;
                else
                        return;                         /* Someone else cquired the lock, no change is required */
        }

        public void setReader(int Tid)
        {
                int index = (Tid - 1) % MaxSpec;        /* Tx Ids start from 1 at present */
                readers_array[index] = Tid;
        }

        public void clearReader(int Tid)
        {
                int index = (Tid - 1) % MaxSpec;        /* Tx Ids start from 1 at present */
                readers_array[index] = 0;
        }

        public int[] getReaderArray()
        {
                return (this.readers_array);
        }

        public int getOwner()
        {
                return(this.owner.get());
        }


	public void clearOwner()
        {
                this.owner.set(0);
        }

	public boolean compareAndSetOwner(int prev,int  curr)
        {
                return(owner.compareAndSet(prev,curr));
        }

	/* Additional functions to enfrce parallelism */
	/* public void lock_object(int caller)
	 {
		completedObject.lock_object(caller);
	 }
	 
	 public void unlock_object(int caller)
	 {
		completedObject.unlock_object(caller);
	 }

	public void setReader(int Tid)
	{
		completedObject.setReader(Tid);
	}
	public void clearReader(int Tid)
	{
		completedObject.clearReader(Tid);
	}
 	public int[] getReaderArray()
        {
                return(completedObject.getReaderArray());
        }
	public int getOwner()
	{
		return(completedObject.getOwner());
	}
 	public void clearOwner()
        {
                completedObject.clearOwner();
        }
	public boolean compareAndSetOwner(int prev, int curr)
        {
               return( completedObject.compareAndSetOwner(prev, curr));
        }
	*/

}
