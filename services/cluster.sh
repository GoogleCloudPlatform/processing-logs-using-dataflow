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
    echo "$ cluster.sh CLUSTER_NAME [up|down] PROJECT_ID"
}

if [[ -z $@ ]]; then
    usage
    exit 0
fi

case "$2" in
    up )
        echo -n "* Creating Google Container Engine cluster ${CLUSTER_NAME} under project ${PROJECT_ID}..."
        gcloud container clusters create ${CLUSTER_NAME} \
          --scopes monitoring,logging-write \
          --project ${PROJECT_ID} \
          --machine-type ${MACHINE_TYPE} \
          --zone ${ZONE} \
          --num-nodes ${NUM_NODES} \
          --quiet 2>&1 >/dev/null || error_exit "Error creating Google Container Engine cluster"
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
        gcloud container clusters delete ${CLUSTER_NAME} \
        --project=${PROJECT_ID} \
        --quiet >/dev/null || error_exit "Error deleting Google Container Engine cluster"
        echo "done"
        ;;
esac

