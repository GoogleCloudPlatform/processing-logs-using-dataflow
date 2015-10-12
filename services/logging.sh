#!/bin/bash

set -e

PROJECT_ID=${3}
TEMPLATE="{{(index .items 0).metadata.name}}_{{(index .items 0).metadata.namespace}}_{{(index ((index .items 0).spec.containers) 0).name}}"

function error_exit
{
    echo "$1" 1>&2
    exit 1
}

function usage
{
    echo "logging.sh [streaming|batch] [up|down] [project-id]"
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

case "$1" in
    streaming )
        case "$2" in
            up )
                echo -n "* Creating Pub/Sub topics..."
                for s in ${SERVICE_NAMES[@]}; do 
                    gcloud alpha pubsub topics create ${s} \
                    --project ${PROJECT_ID} \
                    --quiet >/dev/null || error_exit "Error creating Pub/Sub topics"
                done
                echo "done"

                echo -n "* Creating Pub/Sub subscriptions..."
                for s in ${SERVICE_NAMES[@]}; do 
                    gcloud alpha pubsub subscriptions create ${s} \
                    --topic ${s} \
                    --project ${PROJECT_ID} \
                    --quiet >/dev/null || error_exit "Error creating Pub/Sub subscriptions"
                done                
                echo "done"

                echo -n "* Creating Log Export sinks..."
                for s in ${SERVICE_NAMES[@]}; do
                    gcloud beta logging sinks create ${s} \
                    pubsub.googleapis.com/projects/$PROJECT_ID/topics/{s}
                    --log="kubernetes.${s}" \
                    --project=${PROJECT_ID}
                    --quiet >/dev/null || error_exit "Error creating Log Export sinks"
                echo "done"
                ;;
            down )
                echo -n "* Deleting Log Export sinks..."
                for s in ${SERVICE_NAMES[@]}; do
                    gcloud beta logging sinks delete ${s} \
                    --log="kubernetes.${s}" \
                    --project=${PROJECT_ID}
                    --quiet >/dev/null || error_exit "Error deleting Log Export sinks"
                done                    
                echo "done"
                
                echo -n "* Deleting Pub/Sub subscriptions..."
                for s in ${SERVICE_NAMES[@]}; do
                    gcloud alpha pubsub subscriptions delete ${s} \
                    --project=${PROJECT_ID}
                    --quiet >/dev/null || error_exit "Error deleting Pub/Sub subscriptions"
                done
                echo "done"
                
                echo -n "* Deleting Pub/Sub topics..."
                for s in ${SERVICE_NAMES[@]}; do
                    gcloud alpha pubsub topics delete ${s} \
                    --project=${PROJECT_ID}
                    --quiet >/dev/null || error_exit "Error deleting Pub/Sub topics"
                echo "done"                
                ;;
        esac
        ;;
    batch )
        case "$2" in
            up )
                echo -n "* Creating Google Cloud Storage bucket gs://microservices-logs..."
                gsutil -q mb gs://microservices-logs
                gsutil -q acl ch -g cloud-logs@google.com:O gs://microservices-logs
                echo "done"

                echo -n "* Creating Log Export sinks..."
                for s in ${SERVICE_NAMES[@]}; do
                    gcloud beta logging sinks create ${s} \
                    storage.googleapis.com/microservices-logs \
                    --log="kubernetes.${s}" \
                    --project=${PROJECT_ID}
                    --quiet >/dev/null || error_exit "Error creating Log Export sinks"
                done
                echo "done"
                ;;
            down )
                echo -n "* Deleting Log Export sinks..."
                for s in ${SERVICE_NAMES[@]}; do
                    gcloud beta logging sinks delete ${s} \
                    --log="kubernetes.${s}" \
                    --project=${PROJECT_ID}
                    --quiet >/dev/null || error_exit "Error deleting Log Export Sinks"
                done
                echo "done"

                echo -n "* Deleting Google Cloud Storage bucket gs://microservices-logs..."
                gsutil -q rm -rf "gs://microservices-logs/*"
                gsutil -q rb gs://microservices-logs
                echo "done"
                ;;
        esac
        ;;
    help )
        usage
        ;;
esac
