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
```

This application needs to be able to connect to a Reactome relational database The following settings in the properties file can be used to configure this connection:

 - db.host - this is the hostname of the machine. _Default_: localhost
 - db.user - this is the name of the user that the application should connect as. _No default!_
 - db.password - this is the password of the user that the application should connect as. _No default!_
 - db.name - this is the name of the database that the application should connect to. _No default!_
 - db.port - this is the port number that the application should connect to. _Default:_ 3306
 - person.id - when the Molecules are updated, an instance edit will be created and they will be associated with the Person instance which this DB_ID refers to.

Additionally, there is one non-connection related configuration option:

 - testMode - this controls if the application will actually update the database. If set to "true", the application is in testing mode and will not modify the database. _Default_: true.

## Logging
 
This application will log to a file under `./logs/ChEBI_Update.log`. This file will have the same content as the console (stdout/stderr) except without the log4j prefix.

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
