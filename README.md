<h1>UpdateDOIs</h1>

<h4>An update to the UpdateDOIs module in Reactome's data release.</h4>

This program updates DOIs of new annotations contributed by curators in Reactome. It modifies instances in the current releases' Test Reactome and in GK Central databases.

More technically, it will find all 'Pathway' instances with a 'doi' attribute that needs to be updated, constructing the new DOI
from the instance's 'stableIdentifier' attribute. 

New features and tests have been implemented for this iteration of UpdateDOIs: 
  - Runtime tests ensure concordance of the instance's being updated in the Test Reactome and GK Central databases
  - Users can provide a report file (UpdateDOIs.report) of DOIs that are expected to be updated. It can be created from the data found <a href="https://docs.google.com/spreadsheets/d/1KtZ_Z3rvBELroubmeO1ai5otbsS26QpXZn6au-oSCWw/edit#gid=1011530219">here</a>. This is explained further in <a href="#updatedoisreport">UpdateDOIs.report</a> section.
    - If the report has been provided, the script will report any unexpected behaviour and attempt to suggest to the user why it happened
    
<h2>Configuration</h2>

The program uses a configuration file, `data-release-pipeline/src/main/resources/config.properties`, that must be updated before running. The file has the following parameters:

```
userTR=reactomeReleaseMysqlUsername
userGK=gkCentralMysqlUserName
passwordTR=reactomeReleaseMysqlPassword
passwordGK=gkCentralMysqlPassword
hostTR=testReactomeDatabaseServerName (eg. reactomerelease.oicr.on.ca)
hostGK=gkCentralDatabaseServerName (eg. reactomecurator.oicr.on.ca)
# The current release number must be added to this field.
databaseTR=test_reactome_## 
databaseGK=gk_central
# The author DB_IDs for the respective Test Reactome (TR) and GK Central (GK) databases must be added.
authorIdTR=userIdForTestReactome
authorIdGK=userIdForGkCentral
port=3306
```

<h2>Logging</h2>

Currently, logging is divided into 'logs' and 'warnings/errors' produced by the script. All logging is stored in files in the `logs` directory.

Log files end with '.log' and keeps track of the progress of the module:

```
Updated DOI: 10.3180/R-HSA-4755510.1 for SUMOylation of immune response proteins
Updated DOI: 10.3180/R-HSA-9034015.1 for Signaling by NTRK3 (TRKC)
Updated DOI: 10.3180/R-HSA-4085377.1 for SUMOylation of SUMOylation proteins
Updated DOI: 10.3180/R-HSA-3232142.1 for SUMOylation of ubiquitinylation proteins
Updated DOI: 10.3180/R-HSA-4090294.1 for SUMOylation of intracellular receptors
Updated DOI: 10.3180/R-HSA-4655427.1 for SUMOylation of DNA methylation proteins
UpdateDOIs Complete
```

Warning/error log files contain helpful information that can help the user troubleshoot why some instances were not updated:

```
[10.3180/R-HSA-9013694.1] Display names do not match: [Test Reactome]: Signaling by NOTCH4 ~ [GK Central]: (NEW)Signaling by NOTCH4
[10.3180/R-HSA-3232118.5] StableID 'version' in DB different from expected: [DB] 10.3180/R-HSA-3232118.6* ~ [Expected] 10.3180/R-HSA-3232118.5*
[10.3180/R-HSA-8985347.5] 'DB ID' from DB different from expected, but found matching display name: ~ [DB] 10.3180/R-HSA-8985947.5:Interleukin-9 signaling [Expected] 10.3180/R-HSA-8985347.5:Interleukin-9 signaling
[10.3180/R-HSA-1912408.4] 'Display name' in DB different from expected: [DB] Pre-NOTCH Transcription and Translation ~ [Expected] NOTCH Transcription and Translation
```

<h2>UpdateDOIs.report</h2>

This is an optional file provided to the script that can be beneficial both for making sure that the right instances were updated and providing additional information when troubleshooting.
A spreadsheet of DOIs that were added for the current release can be found <a href="https://docs.google.com/spreadsheets/d/1KtZ_Z3rvBELroubmeO1ai5otbsS26QpXZn6au-oSCWw/edit#gid=1011530219">here</a>.
To use this feature, follow these steps:
  1) Access the tab of the most recent release on the previously mentioned spreadsheet.
  2) Create a new 'UpdateDOIs.report' file or remove the contents of the old one found at `src/main/resources`
  3) For each row of the spreadsheet that contains a new curation:
    - Prepend each 'stableID' with "10.3180/" to create the new DOI (eg: 10.3180/R-HSA-123456789.1)
    - Copy into UpdateDOIs.report the new DOI and 'name', separated by a comma (eg: 10.3180/R-HSA-123456789.1,Reactome Annotation Example)
  4) If you created a new new UpdateDOIs.report file, move it to the `src/main/resources` folder. 
  
  <b>Example</b>:
  
  <h5>src/main/resources/UpdateDOIs.report</h5>
  
  ```
  10.3180/R-HSA-9034015.1,Signaling by NTRK3 (TRKC)
10.3180/R-HSA-1912408.4,NOTCH Transcription and Translation
10.3180/R-HSA-4090294.1,SUMOylation of intracellular receptors
10.3180/R-HSA-3232142.1,SUMOylation of ubiquitinylation proteins
10.3180/R-HSA-4085377.1,SUMOylation of SUMOylation proteins
10.3180/R-HSA-4755510.1,SUMOylation of immune response proteins
10.3180/R-HSA-8985347.5,Interleukin-9 signaling
10.3180/R-HSA-9013694.1,Signaling by NOTCH4
10.3180/R-HSA-3232118.5,SUMOylation of transcription factors
10.3180/R-HSA-4655427.1,SUMOylation of DNA methylation proteins
```

This tells the program that it should expect to update <b>only</b> these instances. Any aberrations will be reported in the warnings log. 
If the aberration is related to the list, the program will not update the databases. If it is a DOI that appeared unexpectedly, it will still update, but it will be recorded in the logs.
