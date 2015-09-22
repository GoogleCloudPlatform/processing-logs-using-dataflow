#!/bin/bash

set -e

PROJECT_ID=${2}
TEMPLATE="{{(index .items 0).metadata.name}}_{{(index .items 0).metadata.namespace}}_{{(index ((index .items 0).spec.containers) 0).name}}"

function error_exit
{
    echo "$1" 1>&2
    exit 1
}

function usage
{
    echo "logging.sh up [project-id]"
    echo "logging.sh down [project-id]"

}

function service_names
{
    echo -n "* Getting microservices service names..."
    SERVICE_NAMES=("home" "browse" "locate")
    for i in 0 1 2; do
        SERVICE_NAMES[$i]="$(kubectl get pods -l name=${SERVICE_NAMES[$i]}-service -o template --template="${TEMPLATE}" | tr -d '[[:space:]]')"
    done
    echo "done"
}

case "$1" in
    up )
        service_names

        echo "* Creating Google Cloud Storage bucket gs://microservices-logs"
        gsutil -q mb gs://microservices-logs
        gsutil -q acl ch -g cloud-logs@google.com:O gs://microservices-logs

        echo -n "* Creating Log Export Sinks..."
        for s in ${SERVICE_NAMES[@]}; do
            gcloud beta logging sinks create ${s} storage.googleapis.com/microservices-logs \
              --log="kubernetes.${s}" --quiet >/dev/null || error_exit "Error creating Log Export Sinks"
        done
        echo "done"
        ;;
    down )
        service_names

        echo -n "* Deleting Log Export Sinks..."
        for s in ${SERVICE_NAMES[@]}; do
            gcloud beta logging sinks delete ${s} --log="kubernetes.${s}" \
              --quiet >/dev/null || error_exit "Error deleting Log Export Sinks"
        done
        echo "done"

        echo "* Deleting Google Cloud Storage bucket gs://microservices-logs"
        gsutil -q rm -rf "gs://microservices-logs/*"
        gsutil -q rb gs://microservices-logs

        ;;
    help )
        usage
        ;;
esac
