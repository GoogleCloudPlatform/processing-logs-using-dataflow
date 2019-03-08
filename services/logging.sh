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

PROJECT_ID=${1}
GCS_BUCKET=${2}
MODE=${3}
COMMAND=${4}
# TEMPLATE="{{(index .items 0).metadata.name}}_{{(index .items 0).metadata.namespace}}_{{(index ((index .items 0).spec.containers) 0).name}}"
TEMPLATE="{{(index ((index .items 0).spec.containers) 0).name}}"

function error_exit
{
    echo "$1" 1>&2
    exit 1
}

function usage
{
    echo "$(basename $0) [PROJECT_ID] [BUCKET_NAME] [streaming|batch] [up|down]"
    echo -e "\nScript will fail with existing buckets, please supply a new BUCKET_NAME"
}

function get_service_names
{
    echo -n "* Getting microservices service names..."
    SERVICE_BASENAMES=("home" "browse" "locate")
    for i in 0 1 2; do
        SERVICE_NAMES[$i]="$(kubectl get pods -l name=${SERVICE_BASENAMES[$i]}-service -o template --template="${TEMPLATE}" | tr -d '[[:space:]]')"
    done
    echo "done"
}

if [[ -z $@ ]]; then
    usage
    exit 0
fi

get_service_names

case "$MODE" in
    streaming )
        case "$COMMAND" in
            up )
                echo -n "* Creating Pub/Sub topics..."
                for s in ${SERVICE_NAMES[@]}; do 
                    gcloud pubsub topics create ${s} \
                    --project ${PROJECT_ID} \
                    --quiet >/dev/null || error_exit "Error creating Pub/Sub topics"
                done
                echo "done"

                echo -n "* Creating Pub/Sub subscriptions..."
                for s in ${SERVICE_NAMES[@]}; do 
                    gcloud pubsub subscriptions create ${s} \
                    --topic ${s} \
                    --project ${PROJECT_ID} \
                    --quiet >/dev/null || error_exit "Error creating Pub/Sub subscriptions"
                done                
                echo "done"

                echo -n "* Creating Log Export sinks..."
                for s in ${SERVICE_NAMES[@]}; do
                    gcloud logging sinks create ${s} \
                    pubsub.googleapis.com/projects/${PROJECT_ID}/topics/${s} \
                    --log-filter="${s}" \
                    --project=${PROJECT_ID} \
                    --quiet >/dev/null || error_exit "Error creating Log Export sinks"
                done
                echo "done"
                ;;
            down )
                echo -n "* Deleting Log Export sinks..."
                for s in ${SERVICE_NAMES[@]}; do
                    gcloud logging sinks delete ${s} \
                    --log-filter="${s}" \
                    --project=${PROJECT_ID} \
                    --quiet >/dev/null || error_exit "Error deleting Log Export sinks"
                done                    
                echo "done"
                
                echo -n "* Deleting Pub/Sub subscriptions..."
                for s in ${SERVICE_NAMES[@]}; do
                    gcloud pubsub subscriptions delete ${s} \
                    --project=${PROJECT_ID} \
                    --quiet >/dev/null || error_exit "Error deleting Pub/Sub subscriptions"
                done
                echo "done"
                
                echo -n "* Deleting Pub/Sub topics..."
                for s in ${SERVICE_NAMES[@]}; do
                    gcloud pubsub topics delete ${s} \
                    --project=${PROJECT_ID} \
                    --quiet >/dev/null || error_exit "Error deleting Pub/Sub topics"
                done
                echo "done"                
                ;;
        esac
        ;;
    batch )
        case "$COMMAND" in
            up )
                echo -n "* Creating Google Cloud Storage bucket gs://${GCS_BUCKET}..."

                # if [[ $(gsutil ls | grep '${GCS_BUCKET}') -eq 0 ]]; then
                #     error_exit "gs://${GCS_BUCKET} exists, please choose a new bucket name"
                # fi

                gsutil -q mb gs://${GCS_BUCKET}
                gsutil -q acl ch -g cloud-logs@google.com:O gs://${GCS_BUCKET}
                echo "done"

                echo -n "* Creating Log Export sinks..."
                for s in ${SERVICE_NAMES[@]}; do
                    gcloud logging sinks create ${s} \
                    storage.googleapis.com/${GCS_BUCKET} \
                    --log-filter="${s}" \
                    --project=${PROJECT_ID} \
                    --quiet >/dev/null || error_exit "Error creating Log Export sinks"
                done
                echo "done"
                ;;
            down )
                echo -n "* Deleting Log Export sinks..."
                for s in ${SERVICE_NAMES[@]}; do
                    gcloud logging sinks delete ${s} \
                    --project=${PROJECT_ID} \
                    --quiet >/dev/null || error_exit "Error deleting Log Export Sinks"
                done
                echo "done"

                echo -n "* Deleting Google Cloud Storage bucket gs://${GCS_BUCKET}..."
                gsutil -q rm -rf "gs://${GCS_BUCKET}/*"
                gsutil -q rb gs://${GCS_BUCKET}
                echo "done"
                ;;
        esac
        ;;
    help )
        usage
        ;;
    * )
        usage
        exit 1
        ;;

esac
