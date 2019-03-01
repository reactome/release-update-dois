<h3> Update Stable Identifiers </h3>

This step is the <b>first</b> of the OICR-run steps. It is run after the final slice has been taken for release from the curator database. In a nutshell, this module will look at all instances in the `Event` and `PhysicalEntity` classes and update the Stable Identifier instances of any that have been modified since the previous release. Stable Identifiers are unique IDs used by Reactome. <br>

Stable Identifiers have the following structure: `R-ABC-123456.1`. The first character is always an R (for Reactome). The 'ABC' is used to denote the organism the instance is associated with, by scientific name. For example, a human (<i>Homo sapiens</i>) instance would have a stable identifier containing 'R-HSA-', for <i><b>H</b>omo <b>sa</b>piens</i>. The string of numbers following the species code is a unique number that corresponds to an instance (irrespective of species). The last number, following the period, is the identifier version. 
This denotes how many times this instance has been modified between releases, and is the value that will be updated by this module.

This program evaluates if an instance has changed by looking at the number of `modifications` the instance has in the current and previous release. If the current instance has more modifications, the `identifierVersion` of the stableIdentifier will be incremented.

<h4> Running UpdateStableIdentifiers</h4>

The only requirement for the user is that they have updated/created the `config.properties` file, which is typically stored at `src/main/resources/`. 

Example:

```
##Properties File for UpdateStableIds
port=3306
sliceUsername=mySQLUsername
slicePassword=mySQLPassword
sliceHost=localhost
sliceDatabase=test_slice_##

prevSliceDatabase=test_slice_(##-1)

## GkCentral is typically at a remote server
gkCentralUsername=mySQLUsername
gkCentralPassword=mySQLPassword
gkCentralHost=remoteHost
gkCentralDatabase=gk_central

personInstanceId=#######
releaseNumber=##
```

Once the file has been properly populated, the program can be run via <a href="https://github.com/reactome/data-release-pipeline/blob/feature/update-stable-ids/updateStableIds/runUpdateStableIds.sh">runUpdateStableIds.sh</a>.

<h4> Checking UpdateStableIdentifiers </h4>

There are two ways of confirming that the program ran successfully: log files and Reactome Curator Tool, which can be downloaded <a href="https://reactome.org/download-data/reactome-curator-tool">here</a>. Log messages should give a general idea of how the script ran, and can be compared with previous releases. 

To check using Curator Tool, you will want to pull a few stable identifiers from the log files that were updated. By looking at the Stable Identifier instance on the current and previous slice, you should be able to confirm the identifier was in fact updated. 

