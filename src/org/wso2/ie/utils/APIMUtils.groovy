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
package org.wso2.ie.utils

def DOCKER_RESOURCES_GIT_RELEASE_TAG
def latest_version
def DOCKER_RESOURCE_GIT_REPO_NAME

def get_product_docker_home(wso2_product) {
    println "Getting product Docker Homes..."
    def product_profile_docker_homes
    switch(wso2_product) {
        case "wso2am":
            product_profile_docker_homes = ["apim"]
            break
        case "wso2am-analytics":
            product_profile_docker_homes = ["apim-analytics/dashboard", "apim-analytics/worker"]
            break
        case "wso2is-km":
            product_profile_docker_homes = ["is-as-km"]
            break
        case "wso2is":
            product_profile_docker_homes = ["is"]
            break
        case "wso2is-analytics":
            product_profile_docker_homes = ["is-analytics/dashboard", "is-analytics/worker"]
            break
        default:
            println "Product is not valid"
            break
    }
    
    return product_profile_docker_homes
}

def get_docker_release_version(wso2_product, wso2_product_version, product_key) {
    println "Getting Docker Release Version..."
    wum_update_script = libraryResource "org/wso2/ie/conf/${product_key}-data.json"
    writeFile file: "./${product_key}-data.json", text: wum_update_script
    config_file = readJSON file: "${product_key}-data.json"
    def result = config_file.profiles.find{ it.product == wso2_product }?.versions?.find{ it.product_version == wso2_product_version }
    DOCKER_RESOURCES_GIT_RELEASE_TAG = result.docker_release_version
    latest_version = result.latest
}

def get_latest_wum_timestamp(wso2_product_profile, wso2_product_version) {
    println "Getting WUM Timestamp from config..."
    UPDATED_PRODUCT_PACK_LOCATION = "${WORKSPACE}/product-packs"
    unstash 'properties'
    def props = readProperties  file:'timestamp.properties'
    def wum_timestamp = props['wum_timestamp']

    return wum_timestamp
}

def build_image(wso2_product, wso2_product_version, os_platform_name, product_profile_docker_home, wum_timestamp, image_tags, product_key) {
    println "Building image..."
    DOCKER_RESOURCE_GIT_REPO_NAME = "docker-${product_key}"
    UPDATED_PRODUCT_PACK_HOST_LOCATION_URL = "http://172.17.0.1:8888"
    PRIVATE_DOCKER_REGISTRY = "localhost:5000"
    
    def result = config_file.profiles.find{ it.product_profile_docker_home == product_profile_docker_home }
    def profile = result.name
    def image_prefix = "${PRIVATE_DOCKER_REGISTRY}/${profile}"

    def docker_build_context = "https://github.com/wso2/${DOCKER_RESOURCE_GIT_REPO_NAME}.git#v${DOCKER_RESOURCES_GIT_RELEASE_TAG}:/dockerfiles/${os_platform_name}/${product_profile_docker_home}"
    def image = docker.build("${image_prefix}:${image_tags[0]}", "--build-arg WSO2_SERVER_DIST_URL=${UPDATED_PRODUCT_PACK_HOST_LOCATION_URL}/${wso2_product}-${wso2_product_version}+${wum_timestamp}.full.zip -f Dockerfile ${docker_build_context}")
    
    return image
}

def generate_tags(wso2_product, wso2_product_version, os_platform_name, os_platform_version, product_profile_docker_home, wum_timestamp) {
    println "Generating tags..."
    def image_tags = []

    if (latest_version) {
        image_tags.add("latest")
    }
    // add stable image tags
    def stable_version_tag = wso2_product_version
    // add OS platform name
    if (os_platform_name != "ubuntu") {
        stable_version_tag ="${stable_version_tag}-${os_platform_name}${os_platform_version}"
    }
    image_tags.add(stable_version_tag)
    
    // add a unique tag
    def unique_tag = "${wso2_product_version}.${wum_timestamp}"
    def release_version = DOCKER_RESOURCES_GIT_RELEASE_TAG.split(/\./)[3]
    unique_tag = "${unique_tag}.${release_version}"
    
    // add OS platform name
    if (os_platform_name != "ubuntu"){
        unique_tag = "${unique_tag}-${os_platform_name}${os_platform_version}"
    }
    image_tags.add(unique_tag)

    return image_tags
}

def image_build_handler(wso2_product, wso2_product_version, os_platform_name, os_platform_version, product_profile_docker_home, product_key) {
    def image_map = [:]
    def wum_timestamp = get_latest_wum_timestamp(wso2_product, wso2_product_version)
    def image_tags = generate_tags(wso2_product, wso2_product_version, os_platform_name, os_platform_version, product_profile_docker_home, wum_timestamp)
    
    for (def image_tag = 0; image_tag <image_tags.size(); image_tag++){
        println(image_tags[image_tag])
    }
    
    def image = build_image(wso2_product, wso2_product_version, os_platform_name, product_profile_docker_home, wum_timestamp, image_tags, product_key)
    image_map.put(image, image_tags)
    tag_images(image, image_tags)
    
    return image_map
}

def tag_images(image, image_tags) {
    println "Tagging image..."
    for (def image_tag = 1; image_tag <image_tags.size(); image_tag++){
        image.tag(image_tags[image_tag])
    }
}

def push_images(image_map) {
    println "Pushing tagged images..."
    image_map.collectMany { image, image_name -> image_name.collect { [object: image, param: it] } }
    .each { println it.object.push(it.param) }
}



//return this
