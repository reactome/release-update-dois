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
						withCredentials([file(credentialsId: 'Config', variable: 'FILE')]) {
							sh 'touch src/main/resources/UpdateDOIs.report'
							sh 'java -jar target/update-dois-0.0.1-jar-with-dependencies.jar $FILE'
						}
					}
				}
			}
		}
		stage('User Input Required: Confirm DOIs'){
			steps{
				script{
					def releaseNumber
					def userInput = input(
					id: 'userInput', message: 'Has the list of updateable DOIs output by the UpdateDOIs Test Run been confirmed by a curator? (yes/no)',
					parameters: [
                		string($class: 'TextParameterDefinition',
							   defaultValue: 'None',
                               description: 'Confirmation of updateable DOIs',
                               name: 'response'),
					])
					echo("The user input: " + userInput)
				}
			}
		}
    }
}
