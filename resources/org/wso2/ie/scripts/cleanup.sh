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

# capture the location of executables of command line utility tools used for the WSO2 product update process
readonly AWK=`which awk`
readonly GREP=`which grep`
readonly ECHO=`which echo`
readonly TEST=`which test`
readonly REMOVE=`which rm`
readonly WUM=`which wum`

declare -a product_packs

function get_product_packs() {
    # obtain the currently available WSO2 product packs for a particular product based on the WUM channel
    local product_pack_name="${wso2_product_name}-${wso2_product_version}"
    product_packs=$(${WUM} list 2> /dev/null | ${AWK} '{print $3}' | ${GREP} -e "${product_pack_name}.*full\.zip")
    echo "Product packs found: ${product_packs[@]}"
}

function clean_product_pack_dir() {
    echo "Cleaning..."
    for product_pack in ${product_packs}; do
        echo "Removing ${product_pack} from ${wso2_product_host_location}"
        ${TEST} -d ${wso2_product_host_location} && ${REMOVE} -r ${wso2_product_host_location}/${product_pack}
    done
}

get_product_packs
clean_product_pack_dir
