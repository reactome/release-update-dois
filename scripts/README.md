<h2> General-purpose scripts for Reactome's release </h2>

This repository contains scripts that are used by multiple release steps.

<b>Note:</b> Make sure you have a `.my.cnf` file populated, in your <b>home directory</b>, with `mysqldump` and `mysql` parameters to use this script. See https://stackoverflow.com/a/24369804


<h3>createOrReplaceDatabase.sh</h3>

<b>Purpose</b>: Create or replace a MySQL database using a MySQL dump file.
<br>
<b>Perl version</b>: <a href="https://github.com/reactome/Release/blob/master/scripts/restore_database.pl">restore_database.pl</a>
<br>
<b>Arguments</b>:
- `-f|--file`  Filepath of MySQL dump file that will be restored
- `-d|--database`  Name of MySQL database that will be created/updated
    
<br>
<b>Usage</b>: `bash createOrReplace.sh --file release_current_new.dump.gz --database release_current`
<br>
<b>Output</b>: Backup of original database (<i>db.backup.dump.gz</i>)
