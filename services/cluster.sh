#!/bin/bash

set -e

MACHINE_TYPE=g1-small
ZONE=us-central1-f
NUM_NODES=1
PROJECT_ID=${3}
CLUSTER_NAME=${1}

function error_exit
{
    echo "$1" 1>&2
    exit 1
}

function usage
{
    echo "$ cluster.sh [cluster-name] [up|down] [project-id]"
}

if [[ -z $@ ]]; then
    usage
    exit 0
fi

case "$2" in
    up )
        echo -n "\n* Creating Google Container Engine cluster ${CLUSTER_NAME} under project ${PROJECT_ID}..."
        gcloud container clusters create ${CLUSTER_NAME} \
          --scopes monitoring,logging-write \
          --project ${PROJECT_ID} \
          --machine-type ${MACHINE_TYPE} \
          --zone ${ZONE} \
          --num-nodes ${NUM_NODES} \
          --quiet >/dev/null || error_exit "Error creating Google Container Engine cluster"
        echo "done"

        echo "\n* Deploying microservices Replication Controllers..."
        for f in `ls kubernetes/*-controller.yaml`; do
            kubectl create -f $f
        done

        echo "\n* Deploying microservices Services..."
        for f in `ls kubernetes/*-service.yaml`; do
            kubectl create -f $f
        done

        echo "\n* Waiting 2 minutes for Controllers/Services to be deployed..."
        sleep 120

        echo "\n* Getting Replication Controllers:"
        kubectl get rc

        echo "\n* Getting Pods:"
        kubectl get pods

        echo "\n* Getting Services:"
        kubectl get services        
        ;;
    down )
        echo -n "* Deleting Google Container Engine cluster ${CLUSTER_NAME} under project ${PROJECT_ID}..."
        gcloud container clusters delete ${CLUSTER_NAME} \
        --project=${PROJECT_ID} \
        --quiet >/dev/null || error_exit "Error deleting Google Container Engine cluster"
        echo "done"
        ;;
esac

