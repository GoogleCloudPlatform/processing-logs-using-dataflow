#!/bin/bash

set -e

PROJECT_ID=${2}
HOME_LOGS=${3}
BROWSE_LOGS=${4}
LOCATE_LOGS=${5}

PUBSUB_TOPIC="projects/${PROJECT_ID}/topics/${HOME_LOGS}"
TEMPLATE="{{(index .items 0).metadata.name}}_{{(index .items 0).metadata.namespace}}_{{(index ((index .items 0).spec.containers) 0).name}}"

function error_exit
{
    echo "$1" 1>&2
    exit 1
}

function usage
{
    echo "logging.sh up [project-id] [home-logs-destination] [browse-logs-destination] [locate-logs-destination]"
    echo "logging.sh down [project-id] [home-logs-destination] [browse-logs-destination] [locate-logs-destination]"
}

function service_names
{
    echo -n "* Getting microservices service names..."
    HOME_SERVICE="$(kubectl get pods -l name=home-service -o template --template="${TEMPLATE}" | tr -d '[[:space:]]')"
    BROWSE_SERVICE="$(kubectl get pods -l name=browse-service -o template --template="${TEMPLATE}" | tr -d '[[:space:]]')"
    LOCATE_SERVICE="$(kubectl get pods -l name=locate-service -o template --template="${TEMPLATE}" | tr -d '[[:space:]]')"
    echo "done"
}

case "$1" in
    up )
        service_names

        echo "* Creating Cloud Pub/Sub topic ${HOME_LOGS}..."
        gcloud alpha pubsub topics create "${HOME_LOGS}" --quiet 

        echo "* Creating Google Cloud Storage bucket ${BROWSE_LOGS}..."
        gsutil -q mb gs://${BROWSE_LOGS}
        gsutil -q acl ch -g cloud-logs@google.com:O gs://${BROWSE_LOGS}

        echo "* Creating Google Cloud Storage bucket ${LOCATE_LOGS}..."
        gsutil -q mb gs://${LOCATE_LOGS}
        gsutil -q acl ch -g cloud-logs@google.com:O gs://${LOCATE_LOGS}

        echo "* Creating Log Export Sinks..."
        gcloud beta logging sinks create ${HOME_SERVICE} pubsub.googleapis.com/${PUBSUB_TOPIC} --log="kubernetes.${HOME_SERVICE}" --quiet
        gcloud beta logging sinks create ${BROWSE_SERVICE} storage.googleapis.com/${BROWSE_LOGS} --log="kubernetes.${BROWSE_SERVICE}" --quiet
        gcloud beta logging sinks create ${LOCATE_SERVICE} storage.googleapis.com/${LOCATE_LOGS} --log="kubernetes.${LOCATE_SERVICE}" --quiet
        ;;
    down )
        service_names

        echo "* Deleting Log Export Sinks..."
        for s in $HOME_SERVICE $BROWSE_SERVICE $LOCATE_SERVICE; do
            gcloud beta logging sinks delete ${s} --log="kubernetes.${s}" --quiet
        done

        echo "* Deleting Google Cloud Storage bucket ${BROWSE_LOGS}..."
        gsutil -q rm -rf "gs://${BROWSE_LOGS}/*"
        gsutil -q rb gs://${BROWSE_LOGS}

        echo "* Deleting Google Cloud Storage bucket ${LOCATE_LOGS}..."
        gsutil -q rm -rf "gs://${LOCATE_LOGS}/*"
        gsutil -q rb gs://${LOCATE_LOGS}

        echo "* Deleting Cloud Pub/Sub topic ${HOME_LOGS}..."
        gcloud alpha pubsub topics delete "${HOME_LOGS}" --quiet 
        ;;
    help )
        usage
        ;;
esac
