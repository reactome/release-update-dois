#!/bin/bash
DIR=$(dirname "$(readlink -f "$0")") # Directory of the script -- allows the script to invoked from anywhere
cd $DIR

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

echo -n "Enter Reactome version: "
read reactome_version
if [ -z $reactome_version ]; then
    echo "ERROR: Reactome version required" >&2
    exit 1;
fi

config_file=config.properties
original_config_file=src/main/resources/sample_config.properties

# Stop git tracking on original configruation file
git update-index --assume-unchanged $original_config_file

# Makes a copy of the sample configuration file
cp -f $original_config_file $config_file

# Removes comments from the original configuration file
sed -i '/^###/d' $config_file

# Replaces the dummy database configuration values
sed -i "s|\(user=\).*|\1$user|" $config_file
sed -i "s|\(password=\).*|\1$password|" $config_file
sed -i "s|\(host=\).*|\1$host|" $config_file
sed -i "s|\(port=\).*|\1$port|" $config_file
sed -i "s|\(reactomeVersion=\).*|\1$reactome_version|" $config_file
sed -i "s|\(outputDir=\).*|\1$output_directory|" $config_file

# Change permissions to read/write for user only
chmod 600 $config_file
