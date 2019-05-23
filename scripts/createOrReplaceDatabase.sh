#!/bin/bash

## This script will create or replace a database using a mysql dump file. It takes the DB name (-d or --database) and dump filepath (-f or --file) as arguments.
## A config file populated with MySQL username is required and optionally with the host and port. Its filepath can be optionally specified (-c or --config), otherwise 
## a default 'config.properties' file is assumed.
## author: jcook

dbFilepath=
dbName=
configFilepath=config.properties
## Parse command-line arguments
while (( "$#" )); do
	case "$1" in
		-f|--file)
			dbFilepath=$2;
			shift 2;
			;;
		-d|--database)
			dbName=$2;
			shift 2;
			;;
		-c|--config)
			configFilepath=$2;
			shift 2;
			;;
		-*|--*=)
			echo "Error: Unsupported flag $1"
			exit 1
	esac
done

## If missing arguments, explain usage
if [ -z "$dbFilepath" ] || [ -z "$dbName" ]
then
	echo "Create or replace mySQL databases";
	echo "Usage: bash createOrReplaceDatabase.sh -f databaseFile -d databaseName [-c configFile] ";
	exit 1
fi

## Parse config file for username, host and port properties
echo "Reading $configFilepath";
username=
host=localhost
port=3306
while read line; do
	if [[ $line == username* ]]
 	then
 		username=${line#*=}
	elif [[ $line == host* ]]
	then
		host=${line#*=}
	elif [[ $line == port* ]]
	then
		port=${line#*=}
 	fi
done < $configFilepath

## Improperly formatted or missing config file
if [ -z "$username" ]
then
	echo "No username in $configFilepath";
	exit 1
fi

## Take archive of DB, drop it and create a new, empty one
dbArchiveFile="$dbName.backup.dump"
if ! mysql -u$username -h$host -P$port -e "use $dbName" 2> /dev/null;
then
	echo "Creating $dbName";
	mysql -u$username -h$host -P$port -e "create database $dbName"
else
	echo "Backing up $dbName";
	mysqldump -u$username -h$host -P$port $dbName > $dbArchiveFile
	echo "Gzipping $dbArchiveFile";
	eval "gzip -f $dbArchiveFile";
	echo "Finished backing up $dbName";
fi

## Drop and create database
mysql -u$username -h$host -P$port -e "drop database if exists $dbName; create database $dbName"

## If dump is gzipped, must use 'zcat'
catCommand=cat
if [[ $dbFilepath == *.gz ]]
then
	catCommand=zcat
fi

## Restore database using 'cat' piped to mysql
cmd="$catCommand $dbFilepath | mysql -u$username -h$host -P$port $dbName"
echo "Restoring $dbFilepath to $dbName";
eval $cmd;
echo "Finished database update";
