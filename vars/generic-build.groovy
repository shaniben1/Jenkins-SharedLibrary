#!/usr/bin/env groovy
library 'shared-lib-int@main'

def start_jenkinsfile(Map telegramToken,appName,dockerhub){

    pipeline {

        options {
            buildDiscarder(logRotator(daysToKeepStr: '10', numToKeepStr: '10'))
            disableConcurrentBuilds()
            timestamps()
            //retry(2)
            timeout(time: 3, unit: 'MINUTES')
        }


        agent{
             docker{
                  image 'jenkins-agent:latest'
                  args  '--user root -v /var/run/docker.sock:/var/run/docker.sock'
               }
        }


        //insert credential to environment variable
        //insert to specific environment variable (must to this name: SNYK_TOKEN) my snyk's token
        environment{
            SNYK_TOKEN=credentials('snykToken')
        }


        stages {
            stage('Test') {
               parallel {
                       stage('pytest'){
                            steps{
                            withCredentials([file(credentialsId: 'telegramToken', variable: 'TOKEN_FILE')]) {
                                sh "cp ${TOKEN_FILE} ./.telegramToken"
                                sh 'pip3 install --no-cache-dir -r requirements.txt'
                                sh 'python3 -m pytest --junitxml results.xml tests/*.py'
                                         }//close Credentials
                                 }//close steps
                            }//close stage pytest

                       stage('pylint') {
                             steps {
                                  script {
                                        log.info 'Starting'
                                         log.warning 'Nothing to do!'
                                         sh "python3 -m pylint *.py || true"
                                         }

                                  }
                       }//close stage pylint
               }//close parallel
            }//close stage Test


            stage('Build Bot app') {                            
                 steps {                                        //change
                      sh "docker build -t shaniben/shani-repo:${appName}-${env.BUILD_NUMBER} . "
                       }
                }


            stage('snyk test - Bot image') {
                steps {
                    sh "snyk container test --severity-threshold=critical --policy-path=PolyBot/.snyk shaniben/shani-repo:poly-bot-${env.BUILD_NUMBER} --file=Dockerfile || true"
                      }
                }

            stage('push image to rep') {                              //change
                steps {
                    withCredentials([usernamePassword(credentialsId: 'dockerhub', passwordVariable: 'pass', usernameVariable: 'user')]){
                        sh "docker login --username $user --password $pass"
                                                                //change
                        sh "docker push shaniben/shani-repo:${appName}-${env.BUILD_NUMBER}"
                        }//close Credentials
                      }//close steps
            }//close stage push


    }//close stages
          post{
                always{                                    //change
                    sh "docker rmi shaniben/shani-repo:${appName}-${env.BUILD_NUMBER}"
                      }
              }

    }//close pipeline
    
}
