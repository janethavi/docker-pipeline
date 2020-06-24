#!/usr/bin/env bash

# ----------------------------------------------------------------------------
#
# Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
#
# WSO2 Inc. licenses this file to you under the Apache License,
# Version 2.0 (the "License"); you may not use this file except
# in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0

# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
# ----------------------------------------------------------------------------

readonly wso2_product_name=$1
readonly wso2_product_version=$2
readonly wso2_product_host_location="${WORKSPACE}/product-packs"
readonly WUM_HOME="${HOME}/.wum3"
readonly properties_file_name="timestamp.properties"
product_pack_name=""

# capture the location of executables of command line utility tools used for the WSO2 product update process
readonly COPY=`which cp`
readonly MOVE=`which mv`
readonly AWK=`which awk`
readonly GREP=`which grep`
readonly ECHO=`which echo`
readonly TEST=`which test`
readonly REMOVE=`which rm`
readonly WUM=`which wum`
readonly CUT=`which cut`

function download_apim_product() {
    echo "Adding ${wso2_product_name}-${wso2_product_version}"
    wum add ${wso2_product_name}-${wso2_product_version} -y &
    local pid=$!
    wait $pid
    echo "Updating ${wso2_product_name}-${wso2_product_version}"
    wum update ${wso2_product_name}-${wso2_product_version} full &
    pid=$!
    wait $pid
}

function move_pack_to_destination() {
    local make_directory=$(which mkdir)
    local wum_product_home="${WUM_HOME}/products/${wso2_product_name}/${wso2_product_version}"
    local pack_name_with_timestamp=$(ls ${wum_product_home}/full |  ${GREP} -e "${product_pack_name}.*full\.zip" )
    local product_pack_path="${wum_product_home}/full/${pack_name_with_timestamp}"

    # echo "----------"
    # ls ${wum_product_home}
    # ls ${product_pack_path}
    # echo "----------"

    # # clean the existing product pack folder
    # echo "Cleaning Hosting pack directory"
    # ${TEST} -d ${wso2_product_host_location} && ${REMOVE} -r ${wso2_product_host_location}/*

    echo "Moving ${wso2_product} to $wso2_product_host_location"
    [[ ${make_directory} ]] && ${TEST} ! -d ${wso2_product_host_location} && ${make_directory} ${wso2_product_host_location}
    ${TEST} -f ${product_pack_path} && ${TEST} -d ${wso2_product_host_location} && ${MOVE} ${product_pack_path} ${wso2_product_host_location}
}

# function get_product_packs() {
#     # obtain the currently available WSO2 product packs for a particular product based on the WUM channel
#     product_pack_name="${wso2_product_name}-${wso2_product_version}"
#     product_packs=$(${WUM} list 2> /dev/null | ${AWK} '{print $3}' | ${GREP} -e "${product_pack_name}.*full\.zip")

#     wso2_product_packs=()
#     for product_pack in ${product_packs}; do
#         wso2_product_packs+=("${product_pack}")
#     done
# }

# function clean_up() {
#     # remove unnecessary product packs
#     local removable_packs=("${wso2_product_packs[@]:0:${#wso2_product_packs[@]}-1}")
#     echo ${wso2_product_packs[@]}
#     for pack in ${removable_packs[@]}
#     do
#     if ! ${WUM} delete -f ${pack}; then
#         echo "Failed to remove the WUM based product pack ${pack}. Exiting !"
#     else
#         echo "WUM based product pack ${pack} is deleted !"
#     fi
#     done
# }

function write_config_file(){
    echo "Writing timestamp to properties file"
    # echo "loc"
    # ls ${wso2_product_host_location}
    # echo "grep"
    # ls ${wso2_product_host_location} | grep -e '${wso2_product_name}-${wso2_product_version}.*full.zip'
    local timestamp=$(ls ${wso2_product_host_location} | ${GREP} -e "${wso2_product_name}-${wso2_product_version}.*full.zip" | ${CUT} -d'+' -f2 | ${CUT} -d'.' -f1)
    # echo $timestamp
    local string_value="wum_timestamp=${timestamp}"
    echo ${string_value} > $properties_file_name
}

function host_products(){
    echo "Hosting product pack in localhost:8888"
    pushd ${wso2_product_host_location}
    python3 -m http.server 8888 &
    sleep 5
    popd
    # curl http://localhost:8888/
}

download_apim_product
#get_product_packs
move_pack_to_destination
#clean_up
write_config_file
host_products

# echo "List product-pack Dir..."
# ls ${wso2_product_host_location}