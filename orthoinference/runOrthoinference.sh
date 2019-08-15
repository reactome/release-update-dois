#!/bin/bash
set -e

DIR=$(dirname "$(readlink -f "$0")") # Directory of the script -- allows the script to invoked from anywhere
cd $DIR

## Update repo
git pull
## Create new jar file with orthoinference code
mvn clean compile assembly:single

## Run orthoinference for each species
allSpecies=(mmus rnor cfam btau sscr drer xtro ggal dmel cele ddis spom scer pfal)
for species in "${allSpecies[@]}"
do
	echo "java -jar target/orthoinference-0.0.2-SNAPSHOT-jar-with-dependencies.jar $species > orthoinference_$species.out";
	java -jar target/orthoinference-0.0.2-SNAPSHOT-jar-with-dependencies.jar $species > orthoinference_$species.out;
done
echo "Orthoinference complete"




