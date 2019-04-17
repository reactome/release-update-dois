# Data Exporter (post-release)

This program will produce data exports for submission to NCBI, UCSC, and Europe PMC.  Files provided to these resources are:
* **NCBI:** Gene (NCBI gene identifier to Reactome top level pathways) and Protein (all UniProt entries in Reactome associated with any NCBI Gene identifier).
* **UCSC:** Entity (UniProt entries in Reactome) and Events (UniProt entries in Reactome in relation to Reactome pathways and reactions).
* **Europe PMC:** Profile and Link XML (describing Reactome as a provider and Reactome Pathway to literature references, respectively).

The file outputs will be as follows in the configured output directory (see configuration section below) and where XX is the Reactome Version:

* gene_reactomeXX-Y.xml (where Y is the file number as this file is split between multiple files so the file upload size is acceptable for NCBI)
* proteins_versionXX (local file that is NOT uploaded to NCBI)
* protein_reactomeXX.ft
* ucsc_entityXX
* ucsc_eventsXX
* europe_pmc_profile_reactome_XX.xml
* europe_pmc_links_reactome_XX.xml

## Compiling & Running

The program can be run by invoking the script `runDataExporter.sh` at the root directory of this project.  It will prompt for configuration values if none have been previously set, so the configuration documentation below is for reference or if you wish to alter existing configuration values.

Using the -b or --build_jar option, the data-exporter.jar file will force `data-exporter.jar` to be re-built.

Usage: `./runDataExporter.sh [-b|--build_jar]`

NOTE: This script is building and invoking a Java application which requires a Java 8+ environment. You will need maven and a full JDK to compile.

### To run the application manually:

1. Compile the jar file: `mvn clean package`
2. Create a configuration file by following the instruction in the configuration section below.

If the manual compilation was successful, you should see a JAR file in the `target` directory, with a name like `data-exporter-VERSION_NUMBER-jar-with-dependencies.jar`. This is the file you will run with a command like the following:

4. `java -jar target/data-exporter-1.0-SNAPSHOT-jar-with-dependencies.jar [path_to_config_file]`

## Configuration

**NOTE: Configuration is done when calling the main script `runDataExporter.sh` for the first time, so this section is if set values need to be changed and for general reference.**

A auto-configuration script is provided at the root directory of this project and is called `configureDataExporter.sh`.  On first running the Icon Finder tool the configuration script will be run automatically and create a configuration file.   If the configuration needs to be changed, however, this script can be run directly: `./configureDataExporter.sh`.

The configuration file produced by the script will be at the root directory of this project and named `config.properties`.  It can be viewed and edited directly if desired.

A sample configuration file is provided at `src/main/resources/config.properties` and looks like this, but should **NEVER BE EDITED DIRECTLY** and any changes are ignored by git after running the configuration script.

```
user=neo4j_user
pass=neo4j_pass
host=localhost
port=7687 # Default Bolt port for Neo4J
reactomeVersion=67
outputDir=data-exporter/archive
```

## Logging

When run, the jar file will output log files to a `logs` directory at the root directory of this project.  For each run of the program, the following log files will be created:
* a Main-<timestamp>.log file - will contain all statements logged by the program
* a Main-<timestamp>.err file - will contain all statements logged with severity of WARN, ERROR, or FATAL by the program
* a NCBIGene-<timestamp>.log file - will contain all statements specific to processing in the NCBI Gene class where processing of UniProt entries in Reactome happens

The log files will contain timestamps of when the program was executed.
