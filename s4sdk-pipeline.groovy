#!/usr/bin/env groovy

final def pipelineSdkVersion = 'master'

pipeline {
    agent any
    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
        skipDefaultCheckout()
    }
    stages {
        stage('Init') {
            steps {
                milestone 10
                library "s4sdk-pipeline-library@${pipelineSdkVersion}"
                stageInitS4sdkPipeline script: this
                abortOldBuilds script: this
            }
        }

        stage('Build') {
            steps {
                milestone 20
                stageBuild script: this
            }
        }

        stage('Local Tests') {
            parallel {
                //   stage("Static Code Checks") { steps { stageStaticCodeChecks script: this } }
                stage("Backend Unit Tests") { steps { stageUnitTests script: this } }
                stage("Backend Integration Tests") { steps { stageIntegrationTests script: this } }
                stage("Frontend Unit Tests") {
                    when { expression { commonPipelineEnvironment.configuration.runStage.FRONT_END_TESTS } }
                    steps { stageFrontendUnitTests script: this }
                }
            }
        }

        /*
            stage('Remote Tests') {
                when { expression { commonPipelineEnvironment.configuration.runStage.REMOTE_TESTS } }
                parallel {
                    stage("End to End Tests") {
                        when { expression { commonPipelineEnvironment.configuration.runStage.E2E_TESTS } }
                        steps { stageEndToEndTests script: this }
                    }
                    stage("Performance Tests") {
                        when { expression { commonPipelineEnvironment.configuration.runStage.PERFORMANCE_TESTS } }
                        steps { stagePerformanceTests script: this }
                    }
                }
            }
        */
        stage('Quality Checks') {
            steps {
                milestone 50
                //stageS4SdkQualityChecks script: this
            }
        }
        /*
            stage('Third-party Checks') {
                when { expression { commonPipelineEnvironment.configuration.runStage.THIRD_PARTY_CHECKS } }
                parallel {
                    stage("Checkmarx Scan") {
                        when { expression { commonPipelineEnvironment.configuration.runStage.CHECKMARX_SCAN } }
                        steps { stageCheckmarxScan script: this }
                    }
                    stage("WhiteSource Scan") {
                        when { expression { commonPipelineEnvironment.configuration.runStage.WHITESOURCE_SCAN } }
                        steps { stageWhitesourceScan script: this }
                    }
                    stage("SourceClear Scan") {
                        when { expression { commonPipelineEnvironment.configuration.runStage.SOURCE_CLEAR_SCAN } }
                        steps { stageSourceClearScan script: this }
                    }
                    stage("Fortify Scan") {
                        when { expression { commonPipelineEnvironment.configuration.runStage.FORTIFY_SCAN } }
                        steps { stageFortifyScan script: this }
                    }
                    stage("Additional Tools") {
                        when { expression { commonPipelineEnvironment.configuration.runStage.ADDITIONAL_TOOLS } }
                        steps { stageAdditionalTools script: this }
                    }
                }
            }
            */
       /* stage('Artifact Deployment') {
            when { expression { commonPipelineEnvironment.configuration.runStage.ARTIFACT_DEPLOYMENT } }
            steps {
                milestone 70
                stageArtifactDeployment script: this
            }
        }

        stage('Production Deployment') {
            when { expression { commonPipelineEnvironment.configuration.runStage.PRODUCTION_DEPLOYMENT } }
            //milestone 80 is set in stageProductionDeployment
            steps { stageProductionDeployment script: this }
        }

    }

        */
    post {
        always {
            script {
                if (commonPipelineEnvironment.configuration.runStage?.SEND_NOTIFICATION) {
                    postActionSendNotification script: this
                }
                sendAnalytics script:this
            }
        }
        success {
            script {

                echo " ${env.CHANGE_ID} --  "

                if (env.CHANGE_ID != null) {
                    def custcomm = """Pull Request Number ${pullRequest.number} 
		with title ${pullRequest.title} 
		for branch ${pullRequest.headRef} and
		commit number ${pullRequest.commitCount} 
		is Successfull, Please check Jenkins log and accordingly create an issue"""

                    pullRequest.comment("$custcomm")
                    pullRequest.addLabels(['BuildSuccess'])

                }
                //echo "Hello World--2"
            }
        }
        failure {
            script {
                // CHANGE_ID is set only for pull requests, so it is safe to access the pullRequest global variable
                //echo "Hello World "
                //echo "env - ${env.CHANGE_ID}"

                if (env.CHANGE_ID) {
                    def custcomm = """Pull Request Number ${pullRequest.number}
			with title ${pullRequest.title} 
			for branch ${pullRequest.headRef} and
			commit number ${pullRequest.commitCount} 
			has failed, Please check Jenkins log and accordingly create an issue"""

                    //echo "comment - $custcomm"

                    pullRequest.comment("$custcomm")
                    pullRequest.addLabels(['bug'])
                }
                //echo "Hello World--1"
            }
            /*script {
                echo "Github repo ${commonPipelineEnvironment.githubRepo}"
                properties([[$class: 'GithubProjectProperty', projectUrlStr: "${commonPipelineEnvironment.githubRepo}"]])
            }*/
            /*step([$class: 'GitHubIssueNotifier', issueAppend: true, issueLabel: '', issueTitle: "$JOB_NAME $BUILD_DISPLAY_NAME failedBuild"])*/
            deleteDir()
        }
    }
}