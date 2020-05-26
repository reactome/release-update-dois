import groovy.json.JsonSlurper
// This Jenkinsfile is used by Jenkins to run the UpdateDOIs step of Reactome's release.
// It requires that the UpdateStableIdentifiers step has been run successfully before it can be run.
def currentRelease
def previousRelease
pipeline {
	agent any

	stages {
		// This stage checks that an upstream project, UpdateStableIdentifiers, was run successfully for its last build.
		stage('Check if UpdateStableIdentifiers build succeeded'){
			steps{
				script{
					currentRelease = (pwd() =~ /(\d+)\//)[0][1];
					previousRelease = (pwd() =~ /(\d+)\//)[0][1].toInteger() - 1;
					// This queries the Jenkins API to confirm that the most recent build of UpdateStableIdentifiers was successful.
					def updateStIdsStatusUrl = httpRequest authentication: 'jenkinsKey', validResponseCodes: "${env.VALID_RESPONSE_CODES}", url: "${env.JENKINS_JOB_URL}/job/$currentRelease/job/UpdateStableIdentifiers/lastBuild/api/json"
					if (updateStIdsStatusUrl.getStatus() == 404) {
						error("UpdateStableIdentifiers has not yet been run. Please complete a successful build.")
					} else {
						def updateStIdsStatusJson = new JsonSlurper().parseText(updateStIdsStatusUrl.getContent())
						if(updateStIdsStatusJson['result'] != "SUCCESS"){
							error("Most recent UpdateStableIdentifiers build status: " + updateStIdsStatusJson['result'])
						}
					}
				}
			}
		}
	}
}
