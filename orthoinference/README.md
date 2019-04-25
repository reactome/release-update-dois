<h2> Orthoinference: Generating Reactome's Computationally Inferred Reactions and Pathways</h2>

This module has been rewritten from Perl to Java. This first iteration of orthoinference only contains the components of the orthoinference module that pertain to the <a href="https://github.com/reactome/Release/blob/master/scripts/release/orthoinference/infer_events.pl">infer_events.pl</a> script in the Release repository. While this contains the majority of code run for orthoinference, the rewrite of the <a href="https://github.com/reactome/Release/blob/master/scripts/release/orthoinference/remove_unused_PE.pl">remove_unusued_PE.pl</a>, and <a href="https://github.com/reactome/Release/blob/master/scripts/release/updateDisplayName.pl">updateDisplayName.pl</a> modules will need to be completed for this to be considered a completed rewrite.

Additionally, Orthoinference now generates orthologous Stable Identifiers and PathwayDiagrams. <b>StableIdentifier</b> generation represents the updated version of <a href="https://github.com/reactome/Release/blob/master/scripts/release/generate_stable_ids_orthoinference/add_ortho_stable_ids.pl">add_ortho_stable_ids.pl</a> (from the GenerateOrthoStableIds release <a href="https://github.com/reactome/Release/tree/master/scripts/release/generate_stable_ids_orthoinference">step</a>). It now happens during orthoinference whenever a Pathway, ReactionlikeEvent or PhysicalEntity instance is inferred. Orthologous <b>PathwayDiagram</b> generation is completed after all ReactionlikeEvents and Pathways for a species have been inferred, and is based off the <a href="https://github.com/reactome/Release/tree/master/scripts/release/orthodiagrams">OrthoDiagrams</a> release step, which itself utilized the <a href="https://github.com/reactome/CuratorTool/blob/master/src/org/gk/pathwaylayout/PredictedPathwayDiagramGeneratorFromDB.java">PredictedPathwayDiagramGeneratorFromDB</a> method from the CuratorTool repository.

<h3> The Inference Process </h3>

In a nutshell, the inference process follows this workflow:

![alt text](https://github.com/reactome/data-release-pipeline/blob/develop/assets/OrthoinferenceOverview.png)

For each species, we take all Human <b>ReactionlikeEvents</b> (RlE) instances (<i>Reaction, BlackBoxEvent, Polymerisation, Depolymerisation, FailedReaction</i>) in the `release_current` database that is a copy of the the `slice_current` database after <a href="https://github.com/reactome/data-release-pipeline/tree/develop/updateStableIds">updateStableIds</a> has been run. For each of these RlE instances, there are a few basic rules that must be followed for an inference to be attempted. It must pass a series of <a href="https://github.com/reactome/data-release-pipeline/blob/develop/orthoinference/src/main/java/org/reactome/orthoinference/SkipInstanceChecker.java">filters</a> and have <b>at least 1</b> protein instance, determined using the <a href="https://github.com/reactome/data-release-pipeline/blob/develop/orthoinference/src/main/java/org/reactome/orthoinference/ProteinCountUtility.java">ProteinCountUtility</a>. 

If the RlE passes all these tests, it is considered <i>eligible</i> for inference. Inference is first attempted on the RlE's <b>input</b> and <b>output</b> attributes, followed by <b>catalyst</b> and <b>regulation</b> inference attempts. <u>If the input or output inferences fail, then the process is terminated for that RlE since they are required components of any ReactionlikeEvent.</u> 
  
During inference, the attribute (input/output/catalyst/regulation) is broken down into its individual <b>PhysicalEntity</b> (PE) instances. These PE instances are each run through the <a href="https://github.com/reactome/data-release-pipeline/blob/develop/orthoinference/src/main/java/org/reactome/orthoinference/OrthologousEntityGenerator.java">createOrthoEntity</a> method. This method infers according to PE type: <i>GenomeEncodedentity/EntityWithAccessionedSequence, Complex/Polymer, EntitySet or SimpleEntity</i>. In cases where the PE itself contains multiple <i>GKInstance</i> attributes (eg: Complexes <i>hasComponent</i> attribute, EntitySets <i>hasMember</i> attribute), these too are run through the createOrthoEntity method and inferred. Through this system, PE instances will be recursively broken down until they reach the <b>EntityWithAccessionedSequence</b> (EWAS) level and are inferred in the <a href="https://github.com/reactome/data-release-pipeline/blob/develop/orthoinference/src/main/java/org/reactome/orthoinference/EWASInferrer.java">inferEWAS</a> method.

After all valid ReactionlikeEvents instances have been inferred for a species, the final step is to populate the <b>Pathway</b> instances these RlEs are found in. Pathway inference takes place in its entirety in the <a href="https://github.com/reactome/data-release-pipeline/blob/develop/orthoinference/src/main/java/org/reactome/orthoinference/HumanEventsUpdater.java">HumanEventsUpdater</a> class, and involves creating the hierarchy structure from the Human Pathway equivalent (this will be further expanded at a future date).

Once the database has been updated with inferred Pathways, orthologous PathwayDiagram generation takes place. This generates the diagrams that are visible in Reactome's Pathway Browser for each species. These diagrams are based off the existing Human diagrams in the database.

<h3> Preparing Orthoinference </h3>

Orthoinference can be run once the <a href="https://github.com/reactome/data-release-pipeline/tree/feature/update-stable-ids">UpdateStableIds</a> step has been run. Historically, it had been run following the now-deprecated MyISAM step. Before running the new Java Orthoinference code, there are a few requirements:<br>

- <a href="https://github.com/reactome/data-release-pipeline/tree/develop/orthopairs">Orthopair</a> file generation must have been completed.
- The `slice_current` database will need to be dumped and restored to a new `release_current` database.
- Set the `config.properties` file
- Locally install a build of <a href="https://github.com/reactome/data-release-pipeline/tree/develop/release-common-lib">release-common-lib</a>, following the instructions at the link.
- `normal_event_skip_list.txt` needs to be placed in `src/main/resources/` folder.

<h4> Setting config.properties </h4>
  
  Create or update the `config.properties` file in the`orthoinference/src/main/resources/` folder, setting the following properties to match the current release:
  
  ```
  ### Sample config.properties file for Orthoinference
  username=mysqlUsername
  password=mysqlPassword
  database=test_reactome_##
  host=localhost
  port=3306
  pathToSpeciesConfig=src/main/resources/Species.json
  pathToOrthopairs=path/to/orthopairs/
  releaseNumber=releaseNumber
  dateOfRelease=yyyy-mm-dd
  personId=reactomePersonInstanceId
  ```
  
  <h4> Setting normal_event_skip_list.txt </h4>
  
Typically there are a number of instances that inference will be skipped for. The `normal_event_skip_list.txt` file denotes these instances, listed by Reactome IDs in the format shown below:
  
  ```
  1234567
  8910987
  6543210
  ```
  If there aren't any instances that are to be skipped, the empty file still needs to exist.
  
  <h3> Running Orthoinfence </h3>
  
  <b>Note</b>: For the orthoinference steps and QA, it is recommended that <b>Java 8</b> be used and <b>mySQL 5.5</b> or <b>5.7</b> be used.
  
  Once all <a href="https://github.com/reactome/data-release-pipeline/tree/develop/orthoinference#-preparing-orthoinference-">prerequisites</a> have been completed, running the <a href="https://github.com/reactome/data-release-pipeline/blob/develop/orthoinference/runOrthoinference.sh">runOrthoinference.sh</a> script will begin the process. This bash script performs a git pull to update the repo with any changes that may have happened between releases. It then builds the orthoinference jar file with all dependencies and then executes it for each species that will be projected to.
  
  <b>Note</b>: To run orthoinference on particular species, modify the 'allSpecies' array in <a href="https://github.com/reactome/data-release-pipeline/blob/develop/orthoinference/runOrthoinference.sh">runOrthoinference.sh</a> so that it only contains the species you wish to project too. Alternatively, if the jar file has been built and only one species needs to be inferred, run the following command:<br> 
`java -jar target/orthoinference-0.0.1-SNAPSHOT-jar-with-dependencies.jar speciesCode`
- Replace 'speciesCode' with the 4 letter species code corresponding to the species you wish to infer too
- Orthoinference benefits from an increased memory heap, which can be modified with the `-Xmx####m` tag before `-jar`.
  
 During orthoinference, many files are produced:
 
 - Log files in the `logs/` folder provide information pertaining to each inference attempt and is useful for tracing errors.
   - They are organized by time stamp.
 - `eligible_(speciesCode)_75.txt` lists all ReactionlikeEvents that can be inferred. This should be the same for all species.
   - The 75 refers to the percent of distinct proteins that must exist in <b>Complex/Polymer</b> instances for an inference attempt to continue. It is a holdover name from Perl Orthoinference.
 - `inferred_(speciesCode)_75.txt` lists all ReactionlikeEvents that were successfully inferred for the species.
 - `report_ortho_inference_test_reactome_##.txt` shows the percentage of inferences that were successful for each species.
 
 Once the Java code has been finished, verify that all `eligible_(speciesCode)_75.txt` files have the same number of lines. If the line counts are different, something likely went wrong during inference and will need to be investigated.
 
 Finally, once you have verified that orthoinference seems to have run correctly, run <a href="https://github.com/reactome/Release/blob/master/scripts/release/updateDisplayName.pl">updateDisplayName.pl</a>. 
 
<h3> Verifying Orthoinference </h3>

Once all scripts in the previous step have been run there is a QA process that should be followed. Orthoinference is a foundational step for the Reactome release pipeline, and ensuring that this process worked as expected will save much time later in the Release process if anything erroneous happened. 

<b> Recommended QA </b><br>

1) Compare line counts of the `eligible_(speciesCode)_75.txt` and `inferred_(speciesCode)_75.txt` files to the previous release. Are they relatively close to each other? If any are significantly smaller for the eligible files of a particular species, perhaps check the <b>Orthopair</b> files that correspond to the species of interest. Are the eligible and/or inferred line counts considerably smaller for all species? Something may have gone wrong during the inference process itself. Check log files to see if anything obvious jumps out. Otherwise, more extensive troubleshooting with the script itself will be required.

Next, we want to make sure that the new `release_current` database can be imported to the <b>graphDb</b> in neo4j and that it reports acceptable graph QA numbers. <br><br><b>It is recommended that the following steps be run on your workstation.</b>

2) Run the <a href="https://github.com/reactome/graph-importer">graph-importer</a> module. This should take some time (30ish minutes) and will output the results from <a href="https://github.com/reactome/database-checker">database-checker</a> as well as the imported graphDb in `target/graph.db/`.

    -  The <b>database-checker</b> results should resemble the following:
    ```
    The consistency check finished reporting 13 as follows:
             1 entries for (Taxon, crossReference)

    Valid schema class check finish reporting 1 entry
    ```
    The database-checker  module just looks for any attributes of an instance that are <i>required</i> (as determined by the data model) and are not filled. Small numbers reported are OK but any newly reported entries should be investigated. 
    
3) Finally, running the <a href="https://github.com/reactome/graph-qa">graph-qa</a> step will check a series of graphDB QA items and rank them by urgency. To run graph-qa, you will need to have an instance of neo4j running with the imported graph DB. To quickly get neo4j installed and running, a docker container is recommended. The following command can be used to get it running quickly and painlessly:<br><br>
` docker run -p 7687:7687 -p 7474:7474 -e NEO4J_dbms_allow__upgrade=true -e NEO4J_dbms_allowFormatMigration=true -e NEO4J_dbms_memory_heap_max__size=XXG -v $(pwd)/target/graph.db:/var/lib/neo4j/data/databases/graph.db neo4j:3.4.9` 
    -  Make sure that the location of the `graph.db/` folder is properly specified in the last argument 
    -  Adjust the `NEO4J_dbms_memory_heap_max__size` argument so that it is  appropriate for your computer/server. 
  
  To verify that the graphDb has been properly populated, open `localhost:7474` (username and password default is `neo4j`), and click on the database icon at the top left. A panel titled <i>Database Information</i> should open up and display all nodes in the <a href="https://reactome.org/content/schema/">Data Model</a>. If none of this appears, chances are the neo4j instance is not using the imported graphDB. Verify the `graph.db/` folder is in fact populated. 

To verify that the graphDb has been properly populated, open `localhost:7474` in your browser once the docker container is built (username and password default is <b>neo4j</b>), and click on the <i>database icon</i> at the top left. A panel titled <i>Database Information</i> should open up and display all nodes in the <a href="https://reactome.org/content/schema">Data Model</a>. If none of this appears, chances are the neo4j instance is not using the imported graphDB. Verify the `graph.db/` folder is in fact populated and make sure the location of the `graph.db/` folder is properly specified in the docker command's last argument.

Once you have verified that the graphDb is populated, graph-qa can be run. Build the jar file using `mvn clean compile package` and then follow the instructions at the <a href="https://github.com/reactome/graph-qa">repo</a>. After `graph-qa` has been run, it will output the number of offending instances for each QA category, ranked by <b>urgency/priority</b>. These results can be compared with the QA results from the previous release.  QA reports can be found <a href="https://drive.google.com/drive/folders/0Byf6VJ-jol9OVnZiWjlzVlJOSGc">here</a>.

4) Once you are satisfied with the results from each of these steps, send the <b>graph-qa</b> and <b>database-checker</b> results to the Curator overseeing release. <a href="https://github.com/reactome/database-checker">Database-checker</a> can be re-run fairly painlessly by following the instructions on its github page. If the Curator is satisfied with the QA results, you can move onto one of the next steps of release. At this point in the release pipeline that could be <a href="https://github.com/reactome/data-release-pipeline/tree/develop/update-dois">UpdateDOIs</a> or <a href="https://github.com/reactome/add-links">AddLinks</a>.

<b> Additional Orthoinference troubleshooting</b>

Below are suggestions for further checking that the Orthoinference process was correctly run. They don't provide as much information or have a specific SOP though, making them optional QA processes. 

-  The <b>WebELVTool</b> found in the <a href="https://github.com/reactome/CuratorTool">CuratorTool</a> repo can be used to check the validity of the `test_reactome` database. The WebELVTool jar is built by running an <b>ant</b> build on the <a href="https://github.com/reactome/CuratorTool/blob/master/ant/WebELVDiagram.xml">WebELVDiagram.xml</a> file. The jar file will appear in the `WebELVTool/` folder above the `CuratorTool/` folder. To run the program, navigate to the directory with the jar file and run:<br> `java -jar webELVTool.jar (host) (release_current) (mysql_username) (mysql_password) (mysql_port) (reactome_author_ID)`<br>The output should be many lines of 'Predicting pathway' or 'Working on pathway'. If the script runs successfully, then it is implied `test_reactome` is appropriately populated.
-  The <b>CuratorTool</b> program (different from the CuratorTool repository mentioned above), can be downloaded from the Reactome website <a href="https://reactome.org/download-data/reactome-curator-tool">here</a> can also be used to explore the inferred data. There isn't a recommended set of tests or checks, but familarity with the CuratorTool can be quite useful for determining if instances are populated correctly. 
