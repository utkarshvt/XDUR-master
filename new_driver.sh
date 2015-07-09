#!/bin/bash

obj=2000
step=500
while [ $obj -le 2000 ]
do
	./t1_driver.sh $obj;
	obj=$(($obj + $step));
	step=1000
done
