#!/bin/bash
cli=500
clistep=150;
objects=$1
replica=$2
echo "Object count = $objects"
while [ $cli -le 2000 ]; do
	
	thread=2
	step=2
	echo "Client count = $cli"
	while [ $thread -le 16 ]; do
		echo "Thread count = $thread"
		iter=1
		while [ $iter -le 2 ]; do
			./tpccServer.sh $replica 20 $objects $thread 0 0 $cli 1000 $thread
			iter=$(($iter + 1))
			sleep 120
		done
		thread=$(($thread + $step))
		step=4
	done
	cli=$(($cli+ $clistep))
	clistep=150
done

