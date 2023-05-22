#!/usr/bin/env bash

## command to use for building the LetSIP fraamework and generate executable jar files

current_dir="`pwd`" 

dispale_dir="$current_dir/code"
echo $dispale_dir

cd "$dispale_dir"

exec "gradle" "wrapper" --gradle-version=7.4.2 #4.9

#exec "./gradlew" "clean"
exec "./gradlew" "build"

