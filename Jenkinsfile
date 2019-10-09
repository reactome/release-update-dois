import groovy.json.JsonSlurper
// This Jenkinsfile is used by Jenkins to run the UpdateDOIs step of Reactome's release. 
// It requires that the UpdateStableIdentifiers step has been run successfully before it can be run.
pipeline {
	agent any

	stages {
		// This stage checks that an upstream project, UpdateStableIdentifiers, was run successfully for its last build.
		stage('Check if UpdateStableIdentifiers build succeeded'){
			steps{
				script{
					// This queries the Jenkins API to confirm that the most recent build of UpdateStableIdentifiers was successful.
					def updateStIdsStatusUrl = httpRequest authentication: 'jenkinsKey', url: "${env.JENKINS_JOB_URL}/job/${env.RELEASE_NUMBER}/job/UpdateStableIdentifiers/lastBuild/api/json"
					def updateStIdsStatusJson = new JsonSlurper().parseText(updateStIdsStatusUrl.getContent())
					if(updateStIdsStatusJson['result'] != "SUCCESS"){
						error("Most recent UpdateStableIdentifiers build status: " + updateStIdsStatusJson['result'])
					}
				}	
		    	}
	    	}
		// This stage backs up the gk_central and release_current databases before they are modified.
		stage('Setup: Back up DBs'){
			steps{
				script{
					dir('update-dois'){
						withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
							def release_current_before_update_dois_dump = "${env.RELEASE_CURRENT}_${env.RELEASE_NUMBER}_before_update_dois.dump"
							def central_before_update_dois_dump = "${env.GK_CENTRAL}_${env.RELEASE_NUMBER}_before_update_dois.dump"
							sh "mysqldump -u$user -p$pass ${env.RELEASE_CURRENT} > $release_current_before_update_dois_dump"
							sh "gzip -f $release_current_before_update_dois_dump"
							sh "mysqldump -u$user -p$pass -h${env.CURATOR_SERVER} ${env.GK_CENTRAL} > $central_before_update_dois_dump"
							sh "gzip -f $central_before_update_dois_dump"
						}
					}
				}
			}
		}
		// This stage builds the jar file using maven.
		stage('Setup: Build jar file'){
			steps{
				script{
					dir('update-dois'){
						sh "mvn clean compile assembly:single"
                    			}
          			}
            		}
        	}
		// This stage executes UpdateDOIs without specifying a 'report' file, which should contain a list of updateable DOIS, as one of the arguments. 
		// This results in test mode behaviour, which includes generating the report file that contains updateable DOIS for release.
		stage('Main: UpdateDOIs Test Run'){
			steps{
				script{
					dir('update-dois'){
						withCredentials([file(credentialsId: 'Config', variable: 'ConfigFile')]) {
							sh "touch src/main/resources/UpdateDOIs.report"
							sh "java -jar target/update-dois-${env.UPDATE_DOIS_VERSION}-jar-with-dependencies.jar $ConfigFile"
						}
					}
				}
			}
		}
		// This stage takes the generated report file and sends it to the curator overseeing release. 
		// Before moving onto the next stage of UpdateDOIs, their confirmation that the contents of the report file are correct is needed.
		stage('Send email of updateable DOIs to curator'){
			steps{
				script{
					emailext (
						body: "This is an automated message. Please review the attached file of Pathway DOIs to be updated and confirm they are correct with the developer running release. Thanks!", 
						to: '$DEFAULT_RECIPIENTS', 
						subject: "UpdateDOIs List for v${env.RELEASE_NUMBER}",
						attachmentsPattern: "**/doisToBeUpdated-v${env.RELEASE_NUMBER}.txt"
					)
				}
			}
		}
		// UpdateDOIs should pause at this stage until the curator confirms the report file is correct. Once they do, respond with 'yes' to the user input form that Jenkins brings up.
		stage('User Input Required: Confirm DOIs'){
			steps{
				script{
					// This brings up a user input form, asking for confirmation that the curator overseeing release approves of the report file they received.
					def userInput = input(
						id: 'userInput', message: "Has a curator confirmed that the list of DOIs to be updated in doisToBeUpdated-v${env.RELEASE_NUMBER}.txt is correct? (yes/no)",
						parameters: [
							[$class: 'TextParameterDefinition', defaultValue: '', description: 'Confirmation of updateable DOIs', name: 'response']
						])

					if (userInput.toLowerCase().startsWith("y")) {
						echo("Proceeding to UpdateDOIs step.")
					} else {
						error("Please confirm output of UpdateDOIs Test Run matches DOIs that should be updated")
					}
				}
			}
		}
		// Now that you have curator approval regarding the report file, this step executes the same jar file again -- this time providing the report file as an argument.
		// With the report file as the second argument, UpdateDOIs executes database modifications.
		stage('Main: UpdateDOIs'){
			steps{
				script{
					dir('update-dois'){
						withCredentials([file(credentialsId: 'Config', variable: 'ConfigFile')]) {
							sh "java -jar target/update-dois-${env.UPDATE_DOIS_VERSION}-jar-with-dependencies.jar $ConfigFile doisToBeUpdated-v${env.RELEASE_NUMBER}.txt"
						}
					}
				}
			}
		}
		// This stage backs up the gk_central and release_current databases after they are modified.
		stage('Post: Backup DBs'){
			steps{
				script{
					dir('update-dois'){
						withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
							def release_current_after_update_dois_dump = "${env.RELEASE_CURRENT}_${env.RELEASE_NUMBER}_after_update_dois.dump"
							def central_before_after_dois_dump = "${env.GK_CENTRAL}_${env.RELEASE_NUMBER}_after_update_dois.dump"
							sh "mysqldump -u$user -p$pass ${env.RELEASE_CURRENT} > $release_current_after_update_dois_dump"
							sh "gzip -f $release_current_after_update_dois_dump"
							sh "mysqldump -u$user -p$pass -h${env.CURATOR_SERVER} ${env.GK_CENTRAL} > $central_before_after_dois_dump"
							sh "gzip -f $central_before_after_dois_dump"
						}
					}
				}
			}
		}
		// This stage archives all logs and database backups produced by UpdateDOIs.
		stage('Archive logs and backups'){
			steps{
				script{
					dir('update-dois'){
						sh "mkdir -p archive/${env.RELEASE_NUMBER}/logs"
						sh "mv --backup=numbered *_${env.RELEASE_NUMBER}_*.dump.gz archive/${env.RELEASE_NUMBER}/"
						sh "gzip logs/*"
						sh "mv logs/* archive/${env.RELEASE_NUMBER}/logs/"
					}
				}
			}
		}
    	}
}
