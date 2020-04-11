def call(Map pipelineParams) {
	//START OF PIPELINE
	pipeline {
		//AGENT NODE DEFINITION
		agent {
			node {
				label "SPDCOREJ02E1B.devspeedpay.com"
				customWorkspace "workspace/${env.JOB_NAME}"
			}
		}
		
		//PIPELINE OPTIONS
		options {
			disableConcurrentBuilds()
			skipDefaultCheckout(true)
		}
		
		//PIPELINE ENVIRONMENT VARIABLES
        environment {
            
			VERSION_PREFIX = "14.1."
			
			ENVIRONMENT = "DEV"
            TENANT = "Track-5"
			
            NUGET_SOURCE1 = "https://spdcore07e1a.devspeedpay.com/repository/NuGet-Proxy/"
            NUGET_SOURCE2 = "https://nexus.nprd-speedpay.com/repository/nuget-notificationservice-core/"
            NUGET_SOURCE3 = "https://nexus.nprd-speedpay.com/repository/Speedpay-NuGet-Group/"
            
            ASSEMBLY_VERSION_FILE = "SharedAssemblyVersion.cs"
			
			PROJECT_NAME = "${pipelineParams.PROJECT_NAME}"
			PACKAGE_NAME = "NotificationService${PROJECT_NAME}"
			NEXUS_PUBLISH_REPO = "${pipelineParams.NEXUS_PUBLISH_REPO}"
            NEXUS_PUBLISH_REPOSITORY = "${env.NEXUS_URL}/repository/${NEXUS_PUBLISH_REPO}/"
            NEXUS_PUBLISH_PACKAGE = "${PACKAGE_NAME}\\*.nupkg"
            CHECKMARX_PROJECT_NAME = "Speedpay_AWS_${PACKAGE_NAME}_${TENANT}"
			CHECKMARX_URL = "https://checkmarx.wuintranet.net"
			OCTOPUS_PROJECT_NAME = "NotificationService-${PROJECT_NAME}"
			EMAIL_GROUP = "grp-aci-speedpay-communicationengine-pune@aciworldwide.com"
			SOLUTION_FILE = "Aci.Speedpay.Notification.${PROJECT_NAME}\\Aci.Speedpay.Notification.${PROJECT_NAME}.csproj" 
			TEST_SOLUTION_FILE = "Aci.Speedpay.Notification.${PROJECT_NAME}.Test\\Aci.Speedpay.Notification.${PROJECT_NAME}.Test.csproj"
        }
		
		//START OF STAGES
		stages {
            //START OF PREPARE STAGE
            stage ("PREPARE"){
                steps {
                    script{
                        cleanWs()
                    }
                }
            } //END OF PREPARE STAGE

            //START OF ECHO VARIABLES STAGE
            stage ("ECHO VARIABLES") {
                steps {
                    script {
                        bat "echo PROJECT_NAME : ${PROJECT_NAME}"
                        bat "echo PACKAGE_NAME : ${PACKAGE_NAME}"
                        bat "echo NEXUS_PUBLISH_REPOSITORY : ${NEXUS_PUBLISH_REPOSITORY}"
                        bat "echo NEXUS_PUBLISH_PACKAGE : ${NEXUS_PUBLISH_PACKAGE}"
                        bat "echo CHECKMARX_PROJECT_NAME : ${CHECKMARX_PROJECT_NAME}"
                        bat "echo CHECKMARX_URL : ${CHECKMARX_URL}"
                        bat "echo EMAIL_GROUP : ${EMAIL_GROUP}"
                        bat "echo SOLUTION_FILE :  ${SOLUTION_FILE}"
                        bat "echo TEST_SOLUTION_FILE : ${TEST_SOLUTION_FILE}"
                    }
                }
            } //END OF ECHO VARIABLES STAGE
            
            //START OF CHECKOUT LATEST CODE & CREATE VERSION STAGE
            stage("CHECKOUT LATEST CODE & CREATE VERSION") {
                steps {
	                script {
		                checkout scm
		                Version = "${VERSION_PREFIX}${BUILD_NUMBER}"
		                powershell("(Get-Content -Path ${ASSEMBLY_VERSION_FILE}).replace('1.0.0.0', '${Version}') | Out-File ${ASSEMBLY_VERSION_FILE}")
		                GIT_SHA = powershell script: "git rev-parse --short HEAD", returnStdout: true
		                Version += "-${GIT_SHA}".trim()
		                powershell("(Get-Content -Path ${ASSEMBLY_VERSION_FILE}).replace('INFORMATIONAL_VERSION', '${Version}') | Out-File ${ASSEMBLY_VERSION_FILE}")
                        currentBuild.displayName = Version
	                }
                }
            } //END OF CHECKOUT LATEST CODE & CREATE VERSION STAGE
			
            //START OF BUILD SOLUTION STAGE
            stage("BUILD SOLUTION FILE") {
                steps {
                    script {
                           bat "mkdir ${PACKAGE_NAME}"
			               bat "dotnet publish ${SOLUTION_FILE} -c \"Release\" -r \"win81-x64\" --source ${NUGET_SOURCE1} --source ${NUGET_SOURCE2} --source ${NUGET_SOURCE3} --output ..\\${PACKAGE_NAME}"
                    }
                }
	
                post {
		            success {
			            Windows_Bat([
			                cmd: "pushd ${PACKAGE_NAME} && \
				                ${NUGET} spec Aci.Speedpay.Notification.${PROJECT_NAME} && \
				                ${NUGET} pack Aci.Speedpay.Notification.${PROJECT_NAME}.nuspec -Version ${Version} -NoPackageAnalysis && \
				                popd"
                        ])
		            }
		            
                    failure {
						emailext attachLog: true,
						body: "<p>Hi Team,<br>The Build failed at Build Solution Stage | ${JOB_NAME}</b><br>PFA log.<br><br>Regards,<br>DevOps Team</p>",
						compressLog: true,
						postsendScript: '$DEFAULT_POSTSEND_SCRIPT',
						presendScript: '$DEFAULT_PRESEND_SCRIPT',
						replyTo: "${EMAIL_GROUP}",
						subject: "NotificationService-${PROJECT_NAME}:Jenkins - FAILED IN BUILD SOLUTION STAGE",
						to: "${EMAIL_GROUP}"			
					}
				}
			} //END OF BUILD SOLUTION STAGE
            
            //START OF TEST CASE AND CODE COVERAGE STAGE
			stage ("TEST CASE AND CODE COVERAGE"){
				steps {
				    withEnv(['HTTP_PROXY=internal-wu-ece-proxy-vip-160106591.us-east-1.elb.amazonaws.com:8080']) {
				    bat '''
                        :Check if GenerateReport folder exists
                        if not exist "GeneratedReports" mkdir "GeneratedReports"
    
                        :RunOpenCoverUnitTestMetrics
                        "D:\\Jenkins\\packages\\OpenCover.4.6.519\\tools\\OpenCover.Console.exe" ^
                        -register:user ^
                        -target:"c:\\Program Files\\dotnet\\dotnet.exe" ^
                        -targetargs:"test %TEST_SOLUTION_FILE% --logger:trx" ^
                        -filter:"+[ACI.Speedpay.Notification.*]* -ACI.Speedpay.Notification.*.Test]*" ^
                        -oldStyle ^
                        -mergebyhash ^
                        -skipautoprops ^
                        -output:"GeneratedReports\\ACI.Speedpay.Notification.Test_Report.xml"
                        
                        :RunReportGenerator
                        "D:\\Jenkins\\packages\\ReportGenerator.3.1.2\\tools\\ReportGenerator.exe" ^
                        -reports:"GeneratedReports\\ACI.Speedpay.Notification.Test_Report.xml" ^
                        -targetdir:"GeneratedReports"
                        
                        cd ACI.Speedpay.Notification.%PROJECT_NAME%.Test\\TestResults
                        
                        powershell -c "$xml = [xml](Get-Content '*.trx');$summary= $xml.TestRun.ResultSummary.Counters;$summary;Write-Output "total=$($summary.total)`nexecuted=$($summary.executed)`npassed=$($summary.passed)`nfailed=$($summary.failed)" | Out-File env.properties -Encoding ASCII;
                        powershell -c "$xml = [xml](Get-Content '*.trx');$summary= $xml.TestRun.ResultSummary.Counters;$($summary.failed);if($($summary.failed) -ne 0){ exit 1}else{ continue}";
                        '''
				    }
				}
				post {
					success {
						archiveArtifacts 'GeneratedReports/**, Checkmarx/Reports/**'
						publishHTML([
							allowMissing: false,
							alwaysLinkToLastBuild: false,
							keepAll: false,
							reportDir: 'GeneratedReports',
							reportFiles: 'index.htm',
							reportName: 'HTML Report',
							reportTitles: ''
						])

						bat '''
							powershell -c "$line_coverage = ((Select-String -Path "GeneratedReports\\index.htm" -Pattern 'Line coverage:.*').Matches.Groups[0].Value).Replace('</th>','').Replace('</td>','').Replace('</tr>','').Replace('<td>',''); $line_coverage; Write-Output Coverage=`""$($line_coverage)"`""" | Out-File environment.properties -Encoding ASCII";'''
					}
					
					failure {
						load env.properties

						emailext attachLog: true,
						body: "<p>Hi team,<br>Test cases have failed for <b>${JOB_NAME}</b><br>Total = ${total}<br>Executed = ${executed}<br>Passed = ${passed}<br>Failed = ${failed}<br>PFA failure logs.<br><br>Regards,<br>DevOps Team</p>",
						compressLog: true,
						postsendScript: '$DEFAULT_POSTSEND_SCRIPT',
						presendScript: '$DEFAULT_PRESEND_SCRIPT',
						replyTo: "${EMAIL_GROUP}",
						subject: 'NotificationService-${PROJECT_NAME}:Jenkins - FAILED IN TEST CASE AND CODE COVERAGE STAGE',
						to: "${EMAIL_GROUP}"
						
					}
				}
			} //END OF TEST CASE AND CODE COVERAGE STAGE
			
			
			//START OF PUBLISH PACKAGE STAGE
			stage("PUBLISH PACKAGE") {
				steps {
					script {
						Nuget_Publish([
							Nuget_PublishDst: "${NEXUS_PUBLISH_REPOSITORY}",
							Nuget_PublishList: ["${NEXUS_PUBLISH_PACKAGE}"],
							Nuget_Tool: "${NUGET}"
						])
					}
				}
			} //END OF PUBLISH PACKAGE STAGE
			
			//START OF OCTOPUS DEPLOYMENT STAGE
			stage("OCTOPUS DEPLOYMENT") {
				steps {
					octopusDeploy(projectName: env.OCTOPUS_PROJECT_NAME, version: "${Version}", environment: env.ENVIRONMENT, tenant: env.TENANT)
				}
			}//END OF OCTOPUS DEPLOYMENT STAGE
        } //END OF STAGES
		
		//START OF POST ACTION STAGE
		post {
            success {
                load 'environment.properties'

                emailext attachLog: true,
                body: "<p>Hi Team,<br>Build succeeded for - ${JOB_NAME}</b><br>Regards,<br>DevOps Team</p>",
                compressLog: true,
                postsendScript: '$DEFAULT_POSTSEND_SCRIPT',
                presendScript: '$DEFAULT_PRESEND_SCRIPT',
                replyTo: "${EMAIL_GROUP}",
                subject: "NotificationService-${PROJECT_NAME}:Jenkins  - SUCCESSFULL",
                to: "${EMAIL_GROUP}"
            }

            failure {
                emailext attachLog: true,
                body: "<p>Hi Team,<br>The Build failed for :<br> - ${JOB_NAME}</b><br>PFA log.<br><br>Regards,<br>DevOps Team</p>",
                compressLog: true,
                postsendScript: '$DEFAULT_POSTSEND_SCRIPT',
                presendScript: '$DEFAULT_PRESEND_SCRIPT',
                replyTo: "${EMAIL_GROUP}",
                subject: "NotificationService-${PROJECT_NAME}:Jenkins - FAILED",
                to: "${EMAIL_GROUP}"
            }
        } //END OF POST ACTION STAGE
	} //END OF PIPELINE
}
