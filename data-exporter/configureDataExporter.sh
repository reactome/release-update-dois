#!/bin/bash
DIR=$(dirname "$(readlink -f "$0")") # Directory of the script -- allows the script to invoked from anywhere
cd $DIR

original_config_file=src/main/resources/sample_config.properties
# Stop git tracking on original/sample configuration file to prevent committing and pushing if any sensitive
# information is mistakenly added
git update-index --assume-unchanged $original_config_file


echo -n "Enter Reactome version: "
read reactome_version
if [ -z $reactome_version ]; then
    echo "ERROR: Reactome version required" >&2
    exit 1;
fi

echo -n "Enter Neo4J user name: "
read user
if [ -z $user ]; then
    echo "ERROR: Neo4J user required" >&2
    exit 1;
fi

echo -n "Enter Neo4J password: "
# Outputs asterisks instead of plain text
# Taken from https://stackoverflow.com/a/4316755
unset password;
while IFS= read -r -s -n1 pass; do
  if [[ -z $pass ]]; then
     echo
     break
  else
     echo -n '*'
     password+=$pass
  fi
done

echo -n "Enter Neo4J host server name (leave blank for localhost): "
read host
if [ -z $host ]; then
    host='localhost'
fi

echo -n "Enter Neo4J Bolt port (leave blank for 7687): "
read port
if [ -z $port ]; then
    port=7687
fi

echo -n "Enter path to output directory (leave blank for $DIR/archive): "
read output_directory
if [ -z $output_directory ]; then
    output_directory="$DIR/archive"
fi

config_file=config.properties

>$config_file
echo "user=$user" >> $config_file
echo "password=$password" >> $config_file
echo "host=$host" >> $config_file
echo "port=$port" >> $config_file
echo "reactomeVersion=$reactome_version" >> $config_file
echo "outputDir=$output_directory" >> $config_file

# Change permissions to read/write for user only
chmod 600 $config_file
