pipeline {
    agent any

    stages {
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
							sh 'touch src/main/resources/UpdateDOIs.report'
							sh 'java -jar target/update-dois-0.0.1-jar-with-dependencies.jar $ConfigFile'
						}
					}
				}
			}
		}
		stage('User Input Required: Confirm DOIs'){
			steps{
				script{
          			def userInput = input(
          				id: 'userInput', message: 'Has the list of updateable DOIs output by the UpdateDOIs Test Run been confirmed by a curator? (yes/no)',
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
							withCredentials([file(credentialsId: 'doisToUpdate', variable: 'DOIsFile')]) {
								sh 'java -jar target/update-dois-0.0.1-jar-with-dependencies.jar $ConfigFile $DOIsFile'
							}
						}
					}
				}
			}
		}						
    }
}
