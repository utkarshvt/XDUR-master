package stm.transaction;


/* Readset is implemented as lonked list.
 * Each element consists of the objId and the version.
 */

public class ReadSetObject
{
	public	int objId;
	public	long version;

	public ReadSetObject(int Id, long version)
	{
		objId = Id;
		this.version = version;
	}
	@Override
    	public boolean equals(Object obj) 
	{
       		if(obj == null)
			return false;

		if (!(obj instanceof ReadSetObject))
            		return false;
        	
		ReadSetObject temp = (ReadSetObject)obj;
		
		if (temp.objId == this.objId)
            		return true;
		return false;
	}


}
