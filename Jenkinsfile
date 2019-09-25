pipeline {
    agent any

    stages {
        stage('Build') {
            steps {
				script {
                    dir ('orthopairs') {
                  	    sh 'mvn clean compile assembly:single'
                    }
          		}
            }
        }
    }
}
