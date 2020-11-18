import groovy.json.JsonSlurper
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
		// This stage backs up the gk_central and release_current databases before they are modified.
		stage('Setup: Back up DBs'){
			steps{
				script{
					withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
						utils.takeDatabaseDumpAndGzip("${env.RELEASE_CURRENT_DB}", "update_dois", "before", "${env.RELEASE_SERVER}")
					}
					withCredentials([usernamePassword(credentialsId: 'mySQLCuratorUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
						utils.takeDatabaseDumpAndGzip("${env.GK_CENTRAL_DB}", "update_dois", "before", "${env.CURATOR_SERVER}")
					}
				}
			}
		}
		// This stage builds the jar file using maven.
		stage('Setup: Build jar file'){
			steps{
				script{
					utils.buildJarFile()
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
						sh "java -jar target/update-dois-*-jar-with-dependencies.jar $ConfigFile"
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
					def emailSubject = "UpdateStableIdentifiers complete & UpdateDOIs List for v${releaseVersion}"
					def emailBody = "This is an automated message. UpdateStableIdentifiers has finished successfully, and UpdateDOIs is currently being run. Please review the attached file of Pathway DOIs to be updated by UpdateDOIs. If they are correct, please confirm so with the developer running release. \nThanks!"
					def emailAttachments = "doisToBeUpdated-v${releaseVersion}.txt"
					
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
						sh "java -jar target/update-dois-*-jar-with-dependencies.jar $ConfigFile doisToBeUpdated-v${currentRelease}.txt"
					}
				}
			}
		}
		// This stage backs up the gk_central and release_current databases after they are modified.
		stage('Post: Backup DBs'){
			steps{
				script{
					withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
						utils.takeDatabaseDumpAndGzip("${env.RELEASE_CURRENT_DB}", "update_dois", "after", "${env.RELEASE_SERVER}")
					}
					withCredentials([usernamePassword(credentialsId: 'mySQLCuratorUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
						utils.takeDatabaseDumpAndGzip("${env.GK_CENTRAL_DB}", "update_dois", "after", "${env.CURATOR_SERVER}")
					}
				}
			}
		}
		// All databases, logs, and data files generated by this step are compressed before moving them to the Reactome S3 bucket. 
		// All files are then deleted.
		stage('Post: Archive Outputs'){
			steps{
				script{
					def s3Path = "${env.S3_RELEASE_DIRECTORY_URL}/${currentRelease}/update_dois"
					sh "mkdir -p databases/ data/"
					sh "mv --backup=numbered *_${currentRelease}_*.dump.gz databases/"
					sh "mv doisToBeUpdated-v${currentRelease}.txt data/"
					sh "gzip data/* logs/*"
					sh "aws s3 --no-progress --recursive cp databases/ $s3Path/databases/"
					sh "aws s3 --no-progress --recursive cp logs/ $s3Path/logs/"
					sh "aws s3 --no-progress --recursive cp data/ $s3Path/data/"
					sh "rm -r databases logs data"
				}
			}
		}	
	}
}
