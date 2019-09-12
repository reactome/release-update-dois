#!/bin/bash
set -e

DIR=$(dirname "$(readlink -f "$0")") # Directory of the script -- allows the script to invoked from anywhere
cd $DIR

## Update repo
git pull
## Create new jar file with updateStableIds code
mvn clean compile assembly:single

## Run program
echo "java -jar target/updateStableIds-1.0.0-jar-with-dependencies.jar"
java -jar target/updateStableIds-1.0.0-jar-with-dependencies.jar

echo "Finished Updating Stable Identifiers"
