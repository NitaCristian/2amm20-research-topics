#!/usr/bin/env bash

## command to use for building the LetSIP fraamework and generate executable jar files

current_dir="`pwd`" 

code_dir="$current_dir/code"
#echo $code_dir

cd "$code_dir"

#exec "gradle" "wrapper" --gradle-version=7.4.2 #4.9

exec "./gradlew" "clean" &
cleanProcessID=$!
wait $cleanProcessID

echo -e "\n\n*-------------------------------*"
echo -e "|\tStart building\t\t|"
echo -e "*-------------------------------*\n\n"

exec "./gradlew" "build" &
buildProcessID=$!
wait $buildProcessID

echo -e "\n\n*-------------------------------*"
echo -e "|\tBuilding finish\t\t|"
echo -e "*-------------------------------*\n\n"
