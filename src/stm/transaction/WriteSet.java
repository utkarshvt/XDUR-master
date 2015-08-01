package stm.transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WriteSet {
	public ConcurrentHashMap<Integer, AbstractObject> writeset;

	public WriteSet() {
		writeset = new ConcurrentHashMap<Integer, AbstractObject>();
	}
	
	public AbstractObject getobject(int objId) {
		return writeset.get(objId);
	}
	
	public void addToWriteSet(int objId, AbstractObject object) {
		writeset.put(objId, object);
	}
	
	public long getVersion(int objIndex) {
		return writeset.get(objIndex).getVersion();
	}
	
	public Map<Integer, AbstractObject> getWriteSet() {
		return writeset;
	}

	public void remove(int objId)
	{
		writeset.remove(objId);
	}
	

}
