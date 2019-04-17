#!/bin/bash

# Command line argument parsing
# Taken from https://medium.com/@Drew_Stokes/bash-argument-parsing-54f3b81a6a8f
PARAMS=""
while (( "$#" )); do
	case "$1" in
		-b|--build_jar)
			build_jar=1
			shift
			;;
		--) # end argument parsing
			shift
			break
			;;
		-*|--*=) # unsupported flags
			echo "Error: Unsupported flag $1" >&2
			exit 1
			;;
		*) # preserve positional arguments
			PARAMS="$PARAMS $1"
			shift
			;;
	esac
done
# set positional arguments in their proper place
eval set -- "$PARAMS"


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

jar_file="data-exporter.jar"
## Generate the jar file if it doesn't exist or a re-build is requested
if [ ! -f $jar_file ] || [ ! -z $build_jar ]; then
	mvn clean package
else
	echo "Executing existing $jar_file file.  To force a rebuild, $0 -b"
fi

## Link and run the jar file
ln -sf target/data-exporter-1.0-SNAPSHOT-jar-with-dependencies.jar $jar_file
java -jar data-exporter.jar $config_file