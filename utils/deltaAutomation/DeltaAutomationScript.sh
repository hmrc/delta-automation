#!/usr/bin/env bash

INPUTFILELOCATION=$1
OUTPUTFILELOCATION=$2
BADFILELOCATION=$3
INPUTFILENAME=$4
PASSWORD=$5

if [ $# -eq 6 ]
  then
    sbt "run \"${INPUTFILELOCATION}\" \"${OUTPUTFILELOCATION}\" \"${BADFILELOCATION}\" \"${INPUTFILENAME}\" \"${PASSWORD}\""
  else
    echo "Incorrect number of arguments supplied. The format is ./DeltaAutomationScript.sh INPUTFILELOCATION OUTPUTFILELOCATION BADFILELOCATION INPUTFILENAME PASSWORD agent|businessuser"
fi