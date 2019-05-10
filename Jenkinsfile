#!groovy​

pipeline {
	//agent any
	agent {
        docker { image 'node:12-alpine' }
    }
    
    options {
        skipStagesAfterUnstable()
    }
    
	environment {
	
		//registry = "josephca/tempest-eclipse-tools"
    	//registryCredential = ‘dockerhub’
    
        TEMPEST_DOCKER_CREDS = credentials('tempest-docker-creds')
        //TEMPEST_DOCKER_USR
        //TEMPEST_DOCKER_PWD
        TEMPEST_IMAGE_NAME = 'tempest-eclipse-tools'
        TEMPEST_TAG = 'latest'
    }
	
	

	stages {
		stage('Build') {
			steps {
				script {
					sh 'echo "Starting gradle build..."'
				
					if (isUnix()) {
						sh 'node --version'
		        	//	dir('dev') { sh './gradlew' }
		    		} 
		    		else {
		        		dir('dev') { bat 'gradlew.bat' }
		    		}
		    	}
			}
		}
		
		stage('Test') {
            steps {
                echo 'Testing'
            }
        }
        
        stage('Upload') {
            steps {
            	script {
	                echo 'Uploading to dockerhub...'
	                
	                if (isUnix()) {
		        		dir('dev') { sh './script/upload.sh $TEMPEST_IMAGE_NAME $TEMPEST_DOCKER_ID $TEMPEST_DOCKER_PASSWORD $GIT_COMMIT $TEMPEST_TAG false' }
		    		} 
		    		else {
		        		dir('dev') { sh 'echo "./script/upload.bat is not available yet"' }
		    		}
                }
            }
        }
	}
	
	post {
        always {
            echo 'This will always run'
        }
        success {
            echo 'This will run only if successful'
        }
        failure {
            echo 'This will run only if failed'
        }
        unstable {
            echo 'This will run only if the run was marked as unstable'
        }
        changed {
            echo 'This will run only if the state of the Pipeline has changed'
            echo 'For example, if the Pipeline was previously failing but is now successful'
        }
    }
}