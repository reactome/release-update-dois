#!/bin/bash


if [ "$#" -gt 1 ]
then
	dbName=$1
	dbFilepath=$2
	configFilepath=config.properties
	if [ ! -z $3 ] 
	then
		configFilepath=$3
	fi

	echo "Reading $configFilepath"
	
	username=
	password=
	## Parse config file for username and password properties
	while read line
	do
		if [[ $line == username* ]]
		then
			username=${line#*=}
		elif [[ $line == password* ]]
		then
			password=${line#*=}
		fi
	done < $configFilepath

	echo "Updating $1 with $2"
	echo "Backing up $1"
	## Take archive of DB, drop it and create a new, empty one
	mysqldump -u$username -p$password $1 > $1.dump
	echo "Finished backing up $1"
	echo "Updating $1 with $2"
	mysql -u$username -p$password -e 'drop database if exists $1; create database $1'

	## If dump is gzipped, must use 'zcat'
	catCommand=cat
	if [ $2 == *.gz ]
	then 
		catCommand=zcat
	fi
	## Restore database using 'cat' piped to mysql
	cmd="$catCommand $2 | mysql -u$username -p$password $1"
	echo "Restoring updated $1"
	eval $cmd
	echo "Finished database update"
else
	echo "Please provide database name and path to the dump file"
fi



