#!/bin/bash

#cd /proj/HyflowTM/
cd /proj/HyflowTM/utkarsh/XDUR-master/
screen -dm -S testing ./tpccServer.sh $*
#screen -dm -S testing ./busy_script.sh $1
#echo "Replica: $1" | tee -a $1.txt
exit
