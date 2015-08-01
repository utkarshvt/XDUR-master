#!/bin/bash

#cd /proj/HyflowTM/
cd /proj/HyflowTM/utkarsh/tw_code/tpcc_dup/XDUR-master/
screen -dm -S dup_testing ./tpccServer.sh $*
#screen -dm -S testing ./busy_script.sh $1
#echo "Replica: $1" | tee -a $1.txt
exit
