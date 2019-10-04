import groovy.json.JsonSlurper

pipeline {
	agent any

	stages {
		stage('Check if upstream builds succeeded'){
			steps{
				script{
					def updateStIdsStatusUrl = httpRequest authentication: 'jenkinsKey', url: "${env.JENKINS_JOB_URL}/job/${env.RELEASE_NUMBER}/job/UpdateStableIdentifiers/lastBuild/api/json"
					def updateStIdsStatusJson = new JsonSlurper().parseText(updateStIdsStatusUrl.getContent())
					if(updateStIdsStatusJson['result'] != "SUCCESS"){
						error("Most recent UpdateStableIdentifiers build status: " + updateStIdsStatusJson['result'])
					}
				}	
		    	}
	    	}
		stage('Setup: Back up DBs'){
			steps{
				script{
					dir('update-dois'){
						withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
							sh "mysqldump -u$user -p$pass ${env.RELEASE_CURRENT} > ${env.RELEASE_CURRENT}_${env.RELEASE_NUMBER}_before_update_dois.dump"
							sh "gzip -f ${env.RELEASE_CURRENT}_${env.RELEASE_NUMBER}_before_update_dois.dump"
							sh "mysqldump -u$user -p$pass -h${env.CURATOR_SERVER} ${env.GK_CENTRAL} > ${env.GK_CENTRAL}_${env.RELEASE_NUMBER}_before_update_dois.dump"
							sh "gzip -f ${env.GK_CENTRAL}_${env.RELEASE_NUMBER}_before_update_dois.dump"
						}
					}
				}
			}
		}				
		stage('Setup: Build jar file'){
			steps{
				script{
					dir('update-dois'){
						sh 'mvn clean compile assembly:single'
                    			}
          			}
            		}
        	}
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
		stage('User Input Required: Confirm DOIs'){
			steps{
				script{
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
		stage('Post: Backup DBs'){
			steps{
				script{
					dir('update-dois'){
						withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
							sh "mysqldump -u$user -p$pass ${env.RELEASE_CURRENT} > ${env.RELEASE_CURRENT}_${env.RELEASE_NUMBER}_after_update_dois.dump"
							sh "gzip -f ${env.RELEASE_CURRENT}_${env.RELEASE_NUMBER}_after_update_dois.dump"
							sh "mysqldump -u$user -p$pass -h${env.CURATOR_SERVER} ${env.GK_CENTRAL} > ${env.GK_CENTRAL}_${env.RELEASE_NUMBER}_after_update_dois.dump"
							sh "gzip -f ${env.GK_CENTRAL}_${env.RELEASE_NUMBER}_after_update_dois.dump"
						}
					}
				}
			}
		}
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
