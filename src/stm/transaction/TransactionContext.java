package stm.transaction;

import java.util.Map;
import java.util.ArrayList;

public class TransactionContext {
	private int Tid;						/* Denotes the transaction number, added for parallel implementation */
	WriteSet writeset = new WriteSet();
	private ReadSet readset = new ReadSet();
	private byte[] result;
	public boolean crossflag = false;				/* Set if the transaction acesses an object which does not belong its private set of warehouses */
	
	/* Default constructor for deserialization, Tid = 0 */
	public TransactionContext()
	{
		this.Tid = 0;
	}	
	
	public TransactionContext( int Id)
	{
		this.Tid = Id;
	}	
	
	public int getTransactionId()
	{
		return(this.Tid) ;
	} 
	public AbstractObject getLatestUnCommittedCopy(int objId){
		return writeset.getobject(objId);
	}
	
	public void setResult(byte[] result) {
		this.result = result;
	}
	
	public byte[] getResult() {
		return result;
	}
	
	public void addObjectToWriteSet(int objId, AbstractObject object) {
		writeset.addToWriteSet(objId, object);
	}
	
	public void addObjectToReadSet(int objId, AbstractObject object) {
		readset.addToReadSet(objId, object);
	}
	
	public WriteSet getShadowCopySetElement() {
		return writeset;
	}

	public ArrayList<ReadSetObject> getReadSet() {
		return readset.getReadSet();
	}
	
	public Map<Integer, AbstractObject> getWriteSet() {
		return writeset.getWriteSet();
	}

	public void readsetremove(int ObjId)
	{
		readset.remove(ObjId);
	}
	        
	public void writesetremove(int ObjId)
        {
                writeset.remove(ObjId);
        }

	public void setFlag()
	{
		this.crossflag = true;
	}
}
