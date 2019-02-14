#!/bin/bash
DIR=$(dirname "$(readlink -f "$0")") # Directory of the script -- allows the script to invoked from anywhere
cd $DIR

## Update repo
git pull
## Create new jar file with orthoinference code
mvn clean compile assembly:single

## Run orthoinference for each species
allSpecies=(pfal spom scer ddis cele sscr btau cfam mmus rnor ggal xtro drer dmel)
for species in "${allSpecies[@]}"
do
	echo "java -jar target/orthoinference-0.0.1-SNAPSHOT-jar-with-dependencies.jar $species > orthoinference_$species.out";
	java -jar target/orthoinference-0.0.1-SNAPSHOT-jar-with-dependencies.jar $species > orthoinference.out;
done
echo "Orthoinference complete"




