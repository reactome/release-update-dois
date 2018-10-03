# ChEBI Update

This tool will perform the ChEBI update.

It will:

 - Query ChEBI for up-to-date information on ChEBI identifiers in Reactome.
 - Update the names of ReferenceEntities that refer to ChEBI ReferenceMolecules in Reactome
 - Update the names, identifiers, and formulae for ReferenceMolecules that need to be updated (based on ChEBI query results).
 - Check for and report on duplicate ReferenceMolecules (two or more different ReferenceMolecule objects that have the same ChEBI Identifier).
 - Report on any ChEBI ReferenceMolecules for which the ChEBI web service did not provide a response.
 
## Configuration

This application requires a properties file that looks like this:

```
db.host=database_server
db.user=someuser
db.password=someuserspassword
db.name=reactome_database
db.port=3306
person.id=somepersonIDNumber
testMode=true
useCache=false
```

This application needs to be able to connect to a Reactome relational database The following settings in the properties file can be used to configure this connection:

 - db.host - this is the hostname of the machine. _Default_: localhost
 - db.user - this is the name of the user that the application should connect as. _No default!_
 - db.password - this is the password of the user that the application should connect as. _No default!_
 - db.name - this is the name of the database that the application should connect to. _No default!_
 - db.port - this is the port number that the application should connect to. _Default:_ 3306
 - person.id - when the Molecules are updated, an instance edit will be created and they will be associated with the Person instance which this DB\_ID refers to. _No default!_
 - useCache - a cache of ChEBI identifier information can be built up and used as the input for later executions. This primary purpose of this is to speed up testing, so developers don't need to wait for real-time communication with ChEBI. The only time this should be used in production is when you are experiencing connectivity problems with ChEBI and you have a good, fresh cache to use. The cache format is a TSV with the following columns: ChEBI ID (including the "CHEBI:" prefix), ChEBI Name, ChEBI Formula. Namd and Forumal are optional. _Default_: false

Additionally, there is one non-connection related configuration option:

 - testMode - this controls if the application will actually update the database. If set to "true", the application is in testing mode and will not modify the database. _Default_: true.

## Logging
 
This application will log to a file under `./logs/ChEBI_Update.log`. This file will have the same content as the console (stdout/stderr) except without the log4j prefix.

Reports will be written under `./logs`. The reports are:
 - DuplicateMoleculeIdentifiers.tsv - This report will list ChEBI Identifiers that are duplicated in the database. The code that generates this report runs at the begining of the process and at the end, so users will know if duplicates were introduced by the process of if they existed before. Because of that, some rows in this file might appear more than once.
 - FailedChEBIQueries.tsv - This report will list ReferenceMolecules which failed when ChEBI was queried, and the reason for the failure.
 - MoleculeIdentifierChanges.tsv - This report will list ReferenceMolecules whose ChEBI identifiers have changed, including the old and new identifiers.
 - MoleculeNameChanges.tsv - This report will list ReferenceMolecules whose names have changed, including the old and new names.
 - ReferenceEntityNameChanges.tsv - This report will list any Entity that refers to a ReferenceMolecule whose name has changed. This report contains the Creator of the Entity, information about the affected Entity, the new name from ChEBI, and the full list of names, *after* the update. 

## Compiling & Running

This is a Java application which requries a Java 8+ environment. You will need maven and a full JDK to compile.

To compile the application, run this command:

```
$ mvn clean compile package
```

If this is successful, you should see a JAR file in the `target` directory, with a name like `chebi-update-VERSION_NUMBER-jar-with-dependencies.jar`. This is the file you will run.

To run the program, execute this command:
```
$ java -jar target/chebi-update-0.0.1-SNAPSHOT-jar-with-dependencies.jar ./chebi-update.properties
```
