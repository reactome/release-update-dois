pipeline {
    agent any

    stages {
        stage('Setup: Build jar file') {
            steps {
				script {
                    dir ('update-dois') {
                  	    sh 'mvn clean compile assembly:single'
                    }
          		}
            }
        }
    }
}
