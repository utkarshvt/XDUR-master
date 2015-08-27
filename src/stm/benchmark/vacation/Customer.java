package stm.benchmark.vacation;

import java.util.LinkedList;
import java.util.Iterator;

import stm.transaction.AbstractObject;

public class Customer extends AbstractObject
{
	int objId;
	public int id;
	public LinkedList<ReservationInfo> reservationInfoList;
	boolean isNull = false;

	public Customer()
	{
	}

	
	public Customer(int objId, int id)
	{
		this.objId = objId;
		this.id = id;
		reservationInfoList = new LinkedList<ReservationInfo>();
	}

	int customer_compare(Customer aPtr, Customer bPtr)
	{
		return (aPtr.id - bPtr.id);
	}

	boolean customer_addReservationInfo(int type, int id, int price)
	{
		ReservationInfo reservationInfoPtr = new ReservationInfo(type, id,
				price);
		// assert(reservationInfoPtr != NULL);

		return reservationInfoList.add(reservationInfoPtr);
	}

	boolean customer_removeReservationInfo(int type, int id)
	{
		ReservationInfo findReservationInfo = new ReservationInfo(type, id, 0);

		for(int i=0; i < reservationInfoList.size(); i++) {
			ReservationInfo reservationInfo = reservationInfoList.get(i);
			if(reservationInfo != null) {
				if(findReservationInfo.compareTo(reservationInfo) == 0) {
					reservationInfoList.remove(i);
					return true;
				}				
			}
		}
		
		return false;		
	}

	int customer_getBill()
	{
		int bill = 0;
		Iterator<ReservationInfo> iter = reservationInfoList.iterator();
		while (iter.hasNext())
		{
			//ReservationInfo val = iter.next();
			//bill += val.price;
			bill += iter.next().price;
		}

		return bill;
	}

	@Override
	public int getId() {
		// TODO Auto-generated method stub
		return id;
	}
	public Customer deepcopy()
	{
		Customer newobj = new Customer();
		newobj.objId = this.objId;
                newobj.id = this.id; 
                newobj.reservationInfoList = new LinkedList<ReservationInfo>();
		for(int i = 0; i < this.reservationInfoList.size(); i++)
		{
			ReservationInfo element = this.reservationInfoList.get(i).deepcopy();
			newobj.reservationInfoList.add(i,element);
		}

		return newobj;
	}
	


}

