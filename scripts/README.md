<h2> General-purpose scripts for Reactome's release </h2>

This repository contains scripts that are used by multiple release steps.


<h3>updateDatabase.sh</h3>

<b>Purpose</b>: Create or replace a mySQL database with a mysql dump file.
<br>
<b>Perl version</b>: <a href="https://github.com/reactome/Release/blob/master/scripts/restore_database.pl">restore_database.pl</a>
<br>
<b>Arguments</b>:
- `-f|--file`  Filepath of mySQL dump file that will be restored
- `-d|--database`  Name of mySQL database that will be created/updated
- `-c|--config`  (Optional) Filepath of config file that contains mySQL parameters (username, password, database, port)
    
<b>Configuration file</b>: Contains mySQL parameters that the script will use. See <a href="https://github.com/reactome/data-release-pipeline/blob/feature/update-release-current/scripts/config.properties.sample">example</a>. 
<br>
<b>Usage</b>: `bash updateDatabase.sh --file release_current_new.dump.gz --database release_current --config config.properties`
<br>
<b>Output</b>: Backup of original database (<i>db.backup.dump.gz</i>)
