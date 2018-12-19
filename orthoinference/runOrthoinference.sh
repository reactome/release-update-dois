## Update repo
git pull
## Create new jar file with orthoinference code
mvn clean compile assembly:single

## Run orthoinference for each species
allSpecies=(pfal spom scer ddis cele sscr btau cfam mmus rnor ggal tgut xtro drer dmel atha osat)
for species in "${allSpecies[@]}"
do
	echo "java -Xmx4096m -jar target/orthoinference-0.0.1-SNAPSHOT-jar-with-dependencies.jar $species > orthoinference_$species.out";
	java -Xmx4096m -jar target/orthoinference-0.0.1-SNAPSHOT-jar-with-dependencies.jar $species 
done
echo "Orthoinference complete"




