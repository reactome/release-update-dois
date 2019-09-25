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
          			def userInput = input(
          				id: 'userInput', message: 'What is the release number?',
          				parameters: [
                      [$class: 'TextParameterDefinition', defaultValue: '', description: 'Release Version', name: 'ReleaseNumber']
                      ])
          			echo("The release number: " + userInput)
				}
			}
		}
    }
}
