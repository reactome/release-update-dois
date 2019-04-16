#!/bin/bash

CWD=$(pwd) # Current working directory -- from where the script is being called
DIR=$(dirname "$(readlink -f "$0")") # Directory of the script -- allows the script to invoked from anywhere
cd $DIR

## Make sure the repo is up to date
echo "Updating data-release-pipeline repository from GitHub"
git pull

config_file=config.properties
## If the config file does not exist, run configuration script
if [ ! -f $config_file ]; then
    ./configureDataExporter.sh
fi

## Generate the jar file and run the Data Exporter program
mvn clean package
ln -sf target/data-exporter-1.0-SNAPSHOT-jar-with-dependencies.jar data-exporter.jar
java -jar data-exporter.jar $config_file
