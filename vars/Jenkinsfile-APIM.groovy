import org.wso2.ie.utils.APIMUtils

def build_jobs = [:]

def call() {
    pipeline {
        agent {
            label 'AWS01'
        }
        environment {
            PATH = "/usr/local/wum/bin:$PATH"
        }
        stages {
            stage('Download_product_packs') {
                steps{
                    withCredentials([usernamePassword(credentialsId: 'docker-image-build', passwordVariable: 'WUM_PASSWORD', usernameVariable: 'WUM_USERNAME')]) {
                        sh 'wum init -u $WUM_USERNAME -p $WUM_PASSWORD'
                        sh 'scripts/wum-update.sh $wso2_product $wso2_product_version'
                    }
                }
            }
            stage('Build and Push') {
                steps{
                    script {
                        // build_script = load 'groovy-scripts/apim-build-image.groovy'
                        build_script = new APIMUtils()
                        product_profile_docker_homes = build_script.get_product_docker_home(wso2_product)
                        build_script.get_docker_release_version(wso2_product, wso2_product_version, product_profile_docker_homes)
                        os_platforms = [alpine: '3.10', ubuntu: '18.04', centos: '7']
                        print ("Before for os")
                        for (os_platform_name in  os_platforms.keySet()) {
                            for (product_profile_docker_home in product_profile_docker_homes) {
                                build_jobs["${os_platform_name}-${product_profile_docker_home}"] = create_build_job(wso2_product, wso2_product_version, os_platform_name, product_profile_docker_home)
                            }
                        }
                        parallel build_jobs
                    }
                }
            }
        }
    }
}

def create_build_job(wso2_product, wso2_product_version, os_platform_name, product_profile_docker_home) {
    return {
        stage("${os_platform_name}-${product_profile_docker_home}"){
            stage("Build ${os_platform_name}-${product_profile_docker_home}") {
                def image_map = build_script.image_build_handler(wso2_product, wso2_product_version, os_platform_name, product_profile_docker_home)
                stage("Push ${os_platform_name}-${product_profile_docker_home}") {
                    build_script.push_images(image_map)
                }
            }
        }
    }
}
