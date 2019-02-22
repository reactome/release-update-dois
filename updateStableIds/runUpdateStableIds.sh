## Update repo
git pull
## Create new jar file with updateStableIds code
mvn clean compile assembly:single

## Run program
echo "java -jar target/updateStableIds-0.0.1-SNAPSHOT-jar-with-dependencies.jar"
java -jar target/updateStableIds-0.0.1-SNAPSHOT-jar-with-dependencies.jar
echo "Finished Updating Stable Identifiers"
