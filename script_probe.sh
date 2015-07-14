#!/bin/bash

cd /proj/HyflowTM/utkarsh/XDUR-master
mkdir testlogs
for j in 3
do
#	mv ${j}_paxos.properties paxos.properties
	for obj in 500 2000 5000
	do
		echo "Objects = $obj\n"
		$cli = 600
		while [ $cli -le 6000 ]
		do
			echo "Clients = $cli\n"
			for threads in 2 4 8 12 16 
			do
				echo "Threads = $threads\n"
				for k in {0..4}
                                do
                                	for (( i=0; i<$j ; i++ ))
                                	do
                                                pdsh -w "h$i"".xdur.HyflowTM" "nohup /proj/HyflowTM/utkarsh/XDUR-master/runTpcc_script.sh $i 20 $obj $threads 0 0 $cli 500 $threads"
                                    	done

                                        echo "Execution started - Nodes = $j, Iter = $k"
                                        sleep `expr 75 + $j`
                                        #sleep `expr 5 + $j`

                                      	for (( i=0; i<$j ; i++ ))
                                      	do
                                        	pdsh -w "h$i"".xdur.HyflowTM" "nohup /proj/HyflowTM/utkarsh/XDUR-master/stop_script.sh"
                                       	done

                                   	sleep 5
                                        	echo "Nodes = $j, Iter = $k"
                                done
			done
			$cli=$(($cli+150))	
                done
 #               mv paxos.properties ${j}_paxos.properties
        done
done
echo "TPCC Script finished !!"


