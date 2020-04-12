def call(Map pipelineParams) {
	//START OF PIPELINE
	pipeline {
		//AGENT NODE DEFINITION
		agent any
		
		//PIPELINE OPTIONS
		options {
			disableConcurrentBuilds()
			skipDefaultCheckout(true)
		}
		
		//PIPELINE ENVIRONMENT VARIABLES
        environment {
			
			PROJECT_NAME = "${pipelineParams.PROJECT_NAME}"

        }
		
		//START OF STAGES
		stages {
            //START OF PREPARE STAGE
            stage ("PREPARE"){
                steps {
                    script{
                        
                        echo "Removing the workspace"
                        cleanWs()

                    }
                }
            } //END OF PREPARE STAGE

            //START OF ECHO VARIABLES STAGE
            stage ("ECHO VARIABLES") {
                steps {
                    script {

                        echo "Value of Detroy Container Flag : ${DESTROY_CONTAINER}"

                    }
                }
            } //END OF ECHO VARIABLES STAGE
            
            //START OF CHECKOUT LATEST CODE & CREATE VERSION STAGE
            stage("CHECKOUT LATEST CODE") {
                steps {
	                script {
                        
                        echo "Fetching the latest code pushed to repository"
		                checkout scm

	                }
                }
            } //END OF CHECKOUT LATEST CODE & CREATE VERSION STAGE
			
            //START OF RUNNING TEST CASE ON CODE STAGE
            stage("RUNNING TEST CASE ON CODE") {
                steps {
                    script {
                        
                        echo  "Running Test Case on Code"
                        sh "sleep 20"

                    }
                }
	
                post {
		            success {

                        echo "Test Case Passed Successfully"
		            }
		            
                    failure {

                        echo "Test Case Failed - Sending Email to Development Team, Exiting"		
					}
				}
			} //END OF RUNNING TEST CASE ON CODE STAGE
            
            //START OF LAUNCH CONTAINER AND DEPLOY LATEST CODE STAGE
			stage ("LAUNCH CONTAINER AND DEPLOY LATEST CODE"){
				steps {
                    sh "docker build -t html-server:v1 ."
                    sh "docker run -d -p 5055:80 html-server:v1"
				}
				post {
					success {

                        echo " COnatiner Launched , Deployment Successful"

					}
					
					failure {

						echo "Launch conatiner and deploy stage failed - Sending Email to Development Team, Exiting"
					}
				}
			} //END OF LAUNCH CONTAINER AND DEPLOY LATEST CODE STAGE
			
			
			//START OF PUBLISH PACKAGE STAGE
			stage("RUN AUTOMATED TEST CASES") {
				steps {
					script {

                        echo "Running Test Cases on the launched conatiner"
                        sh "sleep 20"
					}
				}
			} //END OF PUBLISH PACKAGE STAGE

			//START OF TERMINATE CONATINER STAGE
			stage("TERMINATE CONATINER") {
				steps {
                    sh(returnStdout: true, script: '''#!/bin/bash
                    if [[ ${DESTROY_CONTAINER} == '1' ]];then
                        echo "Terminate Conatiner"
                    else
                        echo "Not Deleting Container as value passed for Flag is 0"
                    fi
                    '''.stripIndent())
                }
            } //END OF TERMINATE CONATINER STAGE
			

        } //END OF STAGES
		
		//START OF POST ACTION STAGE
		post {
            success {
                echo "Emailing JOB SUCCESSFUL status to Teams"
            }

            failure {
                echo "Emailing JOB FAILED status to Teams"
            }
        } //END OF POST ACTION STAGE
	} //END OF PIPELINE
}