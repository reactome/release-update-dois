// This Jenkinsfile is used by Jenkins to run the UpdateDOIs step of Reactome's release.
// It requires that the UpdateStableIdentifiers step has been run successfully before it can be run.
import org.reactome.release.jenkins.utilities.Utilities

// Shared library maintained at 'release-jenkins-utils' repository.
def utils = new Utilities()

pipeline {
	agent any

	stages {
		// This stage checks that an upstream project, UpdateStableIdentifiers, was run successfully for its last build.
		stage('Check if UpdateStableIdentifiers build succeeded'){
			steps{
				script{
					utils.checkUpstreamBuildsSucceeded("Relational-Database-Updates/job/UpdateStableIdentifiers")
				}
			}
		}
		
		// This stage backs up the release_current database and the curator graph database before they are modified.
		stage('Setup: Back up DBs'){
			steps{
				script{
					withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
						utils.takeDatabaseDumpAndGzip("${env.RELEASE_CURRENT_DB}", "update_dois", "before", "${env.RELEASE_SERVER}")
					}
					utils.backUpCuratorGraphDatabase(utils.getCuratorGraphConfig(), "update_dois", "before")
				}
			}
		}
		
		// This stage builds the jar file using maven.
		stage('Setup: Build jar file'){
			steps{
				script{
					sh "mvn clean package -DskipTests"
				}
			}
		}
		
		// This stage executes UpdateDOIs without specifying a 'report' file, which should contain a list of updateable DOIS, as one of the arguments.
		// This results in test mode behaviour, which includes generating the report file that contains updateable DOIS for release.
		stage('Main: UpdateDOIs Test Run'){
			steps{
				script{
					withCredentials([file(credentialsId: 'Config', variable: 'ConfigFile')]) {
						sh "touch src/main/resources/UpdateDOIs.report"
						// Single-quoted so the shell expands $ConfigFile; interpolating a credentials
						// binding in Groovy would bake it into the generated shell script.
						sh 'java -jar target/update-dois-jar-with-dependencies.jar -config $ConfigFile'
					}
				}
			}
		}

		// This stage takes the generated report file and sends it to the curator overseeing release.
		// Before moving onto the next stage of UpdateDOIs, their confirmation that the contents of the report file are correct is needed.
		stage('Main: Send email of updateable DOIs to curator'){
			steps{
				script{
					def releaseVersion = utils.getReleaseVersion()
					def doisToBeUpdatedFile = "doisToBeUpdated-v${releaseVersion}.txt"
					def emailSubject = "UpdateStableIdentifiers complete & UpdateDOIs List for v${releaseVersion}"
					def emailBody = "This is an automated message: UpdateDOIs has completed a test run to determine which Pathway DOIs will be updated in the \'${env.RELEASE_CURRENT_DB}\' and \'${env.GK_CENTRAL_DB}\' databases. Please review the attached ${doisToBeUpdatedFile} file and let the developer running Release know if they look correct. \n\nThanks!"
					def emailAttachments = "${doisToBeUpdatedFile}"
					
					utils.sendEmailWithAttachment("$emailSubject", "$emailBody", "$emailAttachments")
				}
			}
		}

		// UpdateDOIs should pause at this stage until the curator confirms the report file is correct. Once they do, respond with 'yes' to the user input form that Jenkins brings up.
		stage('User Input Required: Confirm DOIs'){
			steps{
				script{
					def releaseVersion = utils.getReleaseVersion()
					// This asks user to confirm that the UpdateDOIs.report file has been approved by Curation.
					def userInput = input(
						id: 'userInput', message: "Proceed once \'doisToBeUpdated-v${releaseVersion}.txt\' has been approved by curation. It should have been sent in an email after the test run step.",
						parameters: [
							[$class: 'BooleanParameterDefinition', defaultValue: true, name: 'response']
						])
				}
			}
		}

		// Now that you have curator approval regarding the report file, this step executes the same jar file again -- this time providing the report file as an argument.
		// With the report file as the second argument, UpdateDOIs executes database modifications.
		stage('Main: UpdateDOIs'){
			steps{
				script{
					withCredentials([file(credentialsId: 'Config', variable: 'ConfigFile')]) {
					    def releaseVersion = utils.getReleaseVersion()
						// The release version is passed through the environment so that the command
						// itself can stay single-quoted, keeping $ConfigFile out of the Groovy string.
						withEnv(["RELEASE_VERSION=${releaseVersion}"]) {
							sh 'java -jar target/update-dois-jar-with-dependencies.jar -config $ConfigFile -report doisToBeUpdated-v${RELEASE_VERSION}.txt'
						}
					}
				}
			}
		}

		stage('Post: Verify UpdateDOIs') {
			steps {
				script {
					withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'releasePass', usernameVariable: 'releaseUser')]){
						withCredentials([usernamePassword(credentialsId: 'mySQLCuratorUsernamePassword', passwordVariable: 'curatorPass', usernameVariable: 'curatorUser')]){
							def releaseVersion = utils.getReleaseVersion()
							// The credential bindings must not be interpolated by Groovy, so the
							// command is single-quoted and everything else is passed via the environment.
							withEnv(["RELEASE_VERSION=${releaseVersion}", "CURATOR_HOST_URL=${utils.getCuratorGraphConfig().curatorToolApiURL}"]) {
					   	   		sh 'java -jar target/update-dois-verifier-jar-with-dependencies.jar --r ${RELEASE_VERSION} --cu $curatorUser --cp $curatorPass --cHU ${CURATOR_HOST_URL} --ru $releaseUser --rp $releasePass'
							}
						}
					}
				}
			}
		}

		// This stage backs up the release_current database and the curator graph database after they are modified.
		stage('Post: Backup DBs'){
			steps{
				script{
					withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
						utils.takeDatabaseDumpAndGzip("${env.RELEASE_CURRENT_DB}", "update_dois", "after", "${env.RELEASE_SERVER}")
					}
					utils.backUpCuratorGraphDatabase(utils.getCuratorGraphConfig(), "update_dois", "after")
				}
			}
		}

		// All databases, logs, and data files generated by this step are compressed before moving them to the Reactome S3 bucket. All files are then deleted.
		stage('Post: Archive Outputs'){
			steps{
				script{
					def releaseVersion = utils.getReleaseVersion()
					def curatorGraphConfig = utils.getCuratorGraphConfig()
					def dataFiles = ["doisToBeUpdated-v${releaseVersion}.txt",
									 utils.getGzippedCuratorGraphDumpFileName(curatorGraphConfig, "update_dois", "before"),
									 utils.getGzippedCuratorGraphDumpFileName(curatorGraphConfig, "update_dois", "after")]
					// Log files appear in the 'logs' folder by default, and so don't need to be specified here.
					def logFiles = []
					def foldersToDelete = []
					utils.cleanUpAndArchiveBuildFiles("update_dois", dataFiles, logFiles, foldersToDelete)
				}
			}
		}

		// This sends an email notifying the mailing list that both the UpdateStableIdentifiers and UpdateDOIs steps have completed. This indicates that gk_central can be reopened.
		stage('Post: Send completion email') {
			steps{
		        script{
		            def releaseVersion = utils.getReleaseVersion()
					def emailSubject = "UpdateStableIdentifier and UpdateDOIs complete for v${releaseVersion}"
					def emailBody = "Hello,\n\nThis is an automated message from Jenkins regarding an update for v${releaseVersion}: Both UpdateStableIdentifiers and UpdateDOIs steps have completed. ${env.GK_CENTRAL_DB} can likely be reopened, but Curation should get \'Human\' confirmation before doing so. \n\nThanks!"
					utils.sendEmail("${emailSubject}", "${emailBody}")
		       	}
		   	}
		}
	}
}
