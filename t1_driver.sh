#!/bin/bash
cli=1000
clistep=1000;
objects=$1
echo "Object count = $objects" | tee -a TP_ob"$objects"_RQA
while [ $cli -le 5000 ]; do
	
	thread=2
	step=2
	echo "Client count = $cli" | tee -a TP_ob"$objects"_RQA
	while [ $thread -le 16 ]; do
		echo "Thread count = $thread" | tee -a TP_ob"$objects"_RQA
		iter=1
		while [ $iter -le 3 ]; do
			./tpccServer.sh 0 10 $objects $thread 0 0 $cli 1000 $thread | tee -a TP_ob"$objects"_RQA
			iter=$(($iter + 1))
			sleep 60
		done
		thread=$(($thread + $step))
		step=4
	done
	cli=$(($cli+ $clistep))
	clistep=1000
done

