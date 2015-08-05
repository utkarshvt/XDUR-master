package stm.benchmark.tpcc;

import java.util.Random;

import stm.transaction.AbstractObject;

public class TpccOrder extends AbstractObject implements java.io.Serializable {	
	public int O_C_ID;
	public String O_ENTRY_D;
	public String O_CARRIER_ID;
	public int O_OL_CNT;
	public Boolean O_ALL_LOCAL;
	
	private Random random = new Random();
	
	private int id;

	public TpccOrder() {
		// no argument constructor for kryo serialization
	}
	
	public TpccOrder(int id) {

		this.id = id;

		this.O_C_ID = random.nextInt(100);
		this.O_ENTRY_D = Integer.toString(random.nextInt(100));
		this.O_CARRIER_ID = Integer.toString(random.nextInt(15));
		this.O_OL_CNT = 5 + random.nextInt(11);
		this.O_ALL_LOCAL = true;

	}

	public int getId() {
		return id;
	}

	public TpccOrder deepcopy() {
		TpccOrder newObject = new TpccOrder(0);
		newObject.id = this.id;
		newObject.O_C_ID = this.O_C_ID;
		newObject.O_ENTRY_D = this.O_ENTRY_D;
		newObject.O_CARRIER_ID = this.O_CARRIER_ID;
		newObject.O_OL_CNT = this.O_OL_CNT;
		newObject.O_ALL_LOCAL = this.O_ALL_LOCAL;
		return newObject;
	}
}
