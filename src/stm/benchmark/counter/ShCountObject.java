package stm.benchmark.counter;
import java.io.Serializable;
import java.util.List;
import java.util.Random;

import stm.transaction.AbstractObject;

public class ShCountObject extends AbstractObject implements java.io.Serializable {

        private int id;

	public long count;
        public ShCountObject() {
                // no argument constructor for kryo serialization       
        }

        public ShCountObject(int id) {
                this.id = id;
		this.count = 0;
        }

	public ShCountObject deepcopy() {
                ShCountObject newObject = new ShCountObject();
                newObject.id = this.id;
                newObject.count = this.count;
                return newObject;
        }

        public int getId() {
                return id;
        }

}

