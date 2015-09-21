#!/bin/bash

set -e

PROJECT_ID=${2}
CLUSTER_NAME=${3}
MACHINE_TYPE=g1-small
ZONE=us-central1-f
NUM_NODES=1

function error_exit
{
    echo "$1" 1>&2
    exit 1
}

function usage
{
    echo "$ cluster.sh up [project-id] [cluster-name]"
    echo "$ cluster.sh down [project-id] [cluster-name]"
}

case "$1" in
    up )
        echo -n "* Creating Google Container Engine cluster ${CLUSTER_NAME} under project ${PROJECT_ID}..."
        gcloud container clusters create ${CLUSTER_NAME} \
          --scopes monitoring,logging-write \
          --project ${PROJECT_ID} \
          --machine-type ${MACHINE_TYPE} \
          --zone ${ZONE} \
          --num-nodes ${NUM_NODES} \
          --quiet >/dev/null || error_exit "Error creating Google Container Engine cluster"
        echo "done"

        echo "* Deploying microservices Replication Controllers..."
        for f in `ls kubernetes/*-controller.yaml`; do
            kubectl create -f $f
        done

        echo "* Deploying microservices Services..."
        for f in `ls kubernetes/*-service.yaml`; do
            kubectl create -f $f
        done

        echo "* Waiting 2 minutes for Controllers/Services to be deployed..."
        sleep 120

        echo "* Getting Replication Controllers:"
        kubectl get rc

        echo "* Getting Pods:"
        kubectl get pods

        echo "* Getting Services:"
        kubectl get services        
        ;;
    down )
        echo -n "* Deleting Google Container Engine cluster ${CLUSTER_NAME} under project ${PROJECT_ID}..."
        gcloud container clusters delete ${CLUSTER_NAME} >/dev/null || error_exit "Error deleting Google Container Engine cluster"
        echo "done"
        ;;
    help )
        usage
        ;;
esac

