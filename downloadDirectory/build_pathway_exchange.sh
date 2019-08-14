#!/bin/bash
set -e

URL="https://github.com/reactome/Pathway-Exchange.git"
DIRECTORY="PathwayExchange"
BRANCH_OR_TAG=${1:-master}
ANT_FILE="PathwayExchangeJar.xml"
OUTPUT="pathwayExchange.jar"
GROUP_ID="org.reactome.pathway-exchange"
ARTIFACT_ID="pathwayExchange"
VERSION=1.0.1

START_DIR=$(pwd)

if [ ! -d "$DIRECTORY/.git" ]; then
    git clone $URL $DIRECTORY
fi

cd $DIRECTORY
git pull
git checkout $BRANCH_OR_TAG
ant -DdestDir="$START_DIR" -buildfile ant/$ANT_FILE
mvn install:install-file -Dfile="$START_DIR/$OUTPUT" \
    -DgroupId=$GROUP_ID -DartifactId=$ARTIFACT_ID -Dversion=$VERSION -Dpackaging=jar
