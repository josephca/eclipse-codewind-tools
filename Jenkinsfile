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
	
		//registry = "josephca/eclipse-codewind-tools"
    	//registryCredential = ‘dockerhub’
    
        CODEWIND_DOCKER_CREDS = credentials('codewind-docker-creds')
        //CODEWIND_DOCKER_USR
        //CODEWIND_DOCKER_PWD
        CODEWIND_IMAGE_NAME = 'eclipse-codewind-tools'
        CODEWIND_TAG = 'latest'
    }
	
	stages {
		stage('Build') {
			steps {
				script {
					sh 'echo "Starting gradle build for Eclipse codewind tools..."'
				
					if (isUnix()) {
					//	sh 'node --version'
		        		dir('dev') { sh './gradlew' }
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
		        	//	dir('dev') { sh './script/upload.sh $CODEWIND_IMAGE_NAME $CODEWIND_DOCKER_ID $CODEWIND_DOCKER_PASSWORD $GIT_COMMIT $CODEWIND_TAG false' }
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