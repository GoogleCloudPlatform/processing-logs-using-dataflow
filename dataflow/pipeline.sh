#!/bin/bash

# Copyright Google Inc. 2015
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e

COMMAND=${4}
PROJECT_ID=${1}
DATASET_NAME=${2}
BUCKET_NAME=${3}
REGION=${REGION:-us-east1}

function usage
{
    echo "$ pipeline.sh PROJECT_ID DATASET_NAME BUCKET_NAME [run|options]"
}

if [[ -z $@ ]]; then
    usage
    exit 0
fi

case "$COMMAND" in
    options )
        mvn compile exec:java \
        -Dexec.mainClass=com.google.cloud.solutions.LogAnalyticsPipeline \
        -Dexec.args="--help=LogAnalyticsPipelineOptions"
        ;;
    run )
        mvn compile exec:java \
        -Dexec.mainClass=com.google.cloud.solutions.LogAnalyticsPipeline \
        -Dexec.args="\
        --project=${PROJECT_ID} \
        --region=${REGION} \
        --stagingLocation=gs://${BUCKET_NAME}/staging \
        --runner=DataflowRunner \
        --homeLogSource=gs://${BUCKET_NAME}/home-service/*/*/*/*.json \
        --browseLogSource=gs://${BUCKET_NAME}/browse-service/*/*/*/*.json \
        --locateLogSource=gs://${BUCKET_NAME}/locate-service/*/*/*/*.json \
        --allLogsTableName=${DATASET_NAME}.all_logs_table \
        --maxRespTimeTableName=${DATASET_NAME}.max_response_time_table \
        --meanRespTimeTableName=${DATASET_NAME}.mean_response_time_table"        
        ;;
    * )
        usage
        exit 1
        ;;
esac
