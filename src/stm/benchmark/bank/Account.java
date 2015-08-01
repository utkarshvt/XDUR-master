package stm.benchmark.bank;

import java.nio.ByteBuffer;

import stm.transaction.AbstractObject;

public class Account extends AbstractObject implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2584138666421024340L;
	private int amount;
	//private String Id;
	private int Id;
	
	public Account(int initialAmount, int Id) {
		super();
		amount = initialAmount;
		this.Id = Id;
	}
	
	public Account(int initialAmount) {
		super();
		amount = initialAmount;
		this.Id = 0;
	}


	// No-args constructor to satisfy Kryo
	public Account() {
		super();
	}

	// Copy constructor
	public Account(Account copyobj)
	{
		amount = copyobj.amount;
		Id = copyobj.Id;
	}
	
	@Override
	public int getId() {
		return Id;
	}

	public Account deepcopy()
	{
		return new Account(this);
	}
	public void setId(int Id) {
		this.Id = Id;
	}
	
	public int getAmount() {
		return amount;
	}

	public void setAmount(int value) {
		amount = value;
	}
	
	public void withdraw(int amountToTransfer) {
		amount -= amountToTransfer;
	}

	public void deposit(int amountToTransfer) {
		amount += amountToTransfer;
	}
}
