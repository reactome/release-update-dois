#!/bin/bash

## This script will create or replace a database using a mysql dump file. It takes the DB name (-d or --database) and dump filepath (-f or --file) as arguments.
## author: jcook

dbFilepath=
dbName=
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
		-*|--*=)
			echo "Error: Unsupported flag $1"
			exit 1
	esac
done

## If missing arguments, explain usage
if [ -z "$dbFilepath" ] || [ -z "$dbName" ]
then
	echo "Create or replace MySQL databases";
	echo "This script utilizes a \`.my.cnf\` file that should exist in your home directory and includes username/password information for [mysql] and [mysqldump] "; 
	echo "Usage: bash createOrReplaceDatabase.sh -f databaseFile -d databaseName ";
	exit 1
fi

## Take archive of DB (if it exists in MySQL), drop it and create a new, empty one 
dbArchiveFile="$dbName.backup.dump"
if ! mysql -e "use $dbName" 2> /dev/null;
then
	echo "Creating $dbName database";
	mysql -e "create database $dbName"
else
	echo "Backing up $dbName";
	mysqldump $dbName > $dbArchiveFile
	echo "Gzipping $dbArchiveFile";
	eval "gzip -f $dbArchiveFile";
	echo "Finished backing up $dbName";
fi

## Drop and create database
mysql -e "drop database if exists $dbName; create database $dbName"

## If dump is gzipped, must use 'zcat'
catCommand=cat
if [[ $dbFilepath == *.gz ]]
then
	catCommand=zcat
fi

## Restore database using 'cat' piped to mysql
cmd="$catCommand $dbFilepath | mysql $dbName"
echo "Restoring $dbFilepath file to $dbName database";
eval $cmd;
echo "Finished database update";
