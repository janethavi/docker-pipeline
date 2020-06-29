/*
*
* Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* WSO2 Inc. licenses this file to you under the Apache License,
* Version 2.0 (the "License"); you may not use this file except
* in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*
*/
import org.wso2.ie.utils.APIMUtils

SCRIPT_FILE_LOCATION = "org/wso2/ie/scripts"

def call(product_key) {
    def build_jobs = [:]
    pipeline {
        agent {
            label 'AWS01'
        }
        environment {
            PATH = "/usr/local/wum/bin:$PATH"
            EMAIL_TO = 'janeth@wso2.com'
        }
        stages {
            stage('Download_product_packs') {
                steps{
                    withCredentials([usernamePassword(credentialsId: 'docker-image-build', passwordVariable: 'WUM_PASSWORD', usernameVariable: 'WUM_USERNAME')]) {
                        sh 'wum init -u $WUM_USERNAME -p $WUM_PASSWORD'
                    }
                    script{
                        wum_update_script = libraryResource "${SCRIPT_FILE_LOCATION}/wum-update.sh"
                        writeFile file: './wum-update.sh', text: wum_update_script
                        sh 'chmod +x ${WORKSPACE}/wum-update.sh'
                        sh '${WORKSPACE}/wum-update.sh $wso2_product $wso2_product_version'
                        stash includes: 'timestamp.properties', name: 'properties'
                    }
                }
            }
            stage('Build and Push') {
                steps{
                    script {
                        build_script = new APIMUtils()
                        product_profile_docker_homes = build_script.get_product_docker_home(wso2_product)
                        build_script.get_docker_release_version(wso2_product, wso2_product_version, product_key)
                        os_platforms = [alpine: '3.10', ubuntu: '18.04', centos: '7']
                        for (os_platform_name in  os_platforms.keySet()) {
                            for (product_profile_docker_home in product_profile_docker_homes) {
                                build_jobs["${os_platform_name}-${product_profile_docker_home}"] = create_build_job(build_script, wso2_product, wso2_product_version, os_platform_name, os_platforms[os_platform_name], product_profile_docker_home, product_key)
                            }
                        }
                        parallel build_jobs
                    }
                }
            }
        }
        post {
            always {
                script{
                    cleanup_script = libraryResource '${SCRIPT_FILE_LOCATION}/cleanup.sh'
                    writeFile file: './cleanup.sh', text: cleanup_script
                    sh 'chmod +x ${WORKSPACE}/cleanup.sh'
                    sh '${WORKSPACE}/cleanup.sh $wso2_product $wso2_product_version'
                }
            }
            failure {
                emailext body: 'Check console output at $BUILD_URL to view the results. \n\n -------------------------------------------------- \n${BUILD_LOG, maxLines=100, escapeHtml=false}', 
                to: "${EMAIL_TO}",
                subject: 'Build failed in Docker Image Build Jenkins: $PROJECT_NAME - #$BUILD_NUMBER'
            }
        }
    }
}

def create_build_job(build_script, wso2_product, wso2_product_version, os_platform_name, os_platform_version, product_profile_docker_home, product_key) {
    return {
        stage("${os_platform_name}-${product_profile_docker_home}"){
            stage("Build ${os_platform_name}-${product_profile_docker_home}") {
                def image_map = build_script.image_build_handler(wso2_product, wso2_product_version, os_platform_name, os_platform_version, product_profile_docker_home, product_key)
                stage("Push ${os_platform_name}-${product_profile_docker_home}") {
                    withCredentials([usernamePassword(credentialsId: 'docker-registry', passwordVariable: 'REGISTRY_PASSWORD', usernameVariable: 'REGISTRY_USERNAME')]) {
                        sh 'docker login docker.wso2.com -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD'
                    }
                    build_script.push_images(image_map)
                }
            }
        }
    }
}
