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
    }
}
