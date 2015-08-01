package stm.transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReadSet {
    public ArrayList<ReadSetObject> readset;
    
    public ReadSet() {
    	readset = new ArrayList<ReadSetObject>();
    }
    
    public void addToReadSet(int objId, AbstractObject object) {
    	
	ReadSetObject readObj = new ReadSetObject (objId,object.getVersion());
	if(readset.contains(readObj))
		return;
	
	readset.add(readObj);
    }
    
    public ArrayList<ReadSetObject> getReadSet() {
    	return readset;
    }

    public void remove(int objId)
    {
       	ReadSetObject readObj = new ReadSetObject (objId,0);
	readset.remove(readObj);
    }



}
