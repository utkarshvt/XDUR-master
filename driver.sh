#!/bin/bash
cli=1000
clistep=500;

while [ $cli -le 10000 ]; do
	
	thread=2
	step=2
	echo "Client count = $cli" | tee -a XDUR_Rq_Abort
	while [ $thread -le 16 ]; do
		echo "Thread count = $thread" | tee -a XDUR_Rq_Abort
		iter=1
		while [ $iter -le 3 ]; do
			./tpccServer.sh 1 10 200 $thread 0 0 $cli 1000 $thread | tee -a XDUR_Rq_Abort
			iter=$(($iter + 1))
			sleep 60
		done
		thread=$(($thread + $step))
		step=4
	done
	cli=$(($cli+ $clistep))
	clistep=500
done	
