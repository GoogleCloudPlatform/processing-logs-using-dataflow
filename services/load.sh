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

REQUESTS=${1}
CONCURRENT=${2}
TEMPLATE="{{(index ((index .items 0).status.loadBalancer.ingress) 0).ip}}:{{(index ((index .items 0).spec.ports) 0).port}}"

function usage
{
    echo "load.sh REQUESTS CONCURRENCY"
    echo -e "\nexample:"
    echo "load.sh 1000 100"
}

if [[ -z $@ ]]; then
    usage
    exit 0
fi

echo -n "* Getting IP/Port information for each microservice..."
HOME_HOST="$(kubectl get services -l name=home-service -o template --template="${TEMPLATE}" | tr -d '[[:space:]]')"
BROWSE_HOST="$(kubectl get services -l name=browse-service -o template --template="${TEMPLATE}" | tr -d '[[:space:]]')"
LOCATE_HOST="$(kubectl get services -l name=locate-service -o template --template="${TEMPLATE}" | tr -d '[[:space:]]')"
echo "done"    

echo "* Testing ${HOME_HOST}/home"
ab -n ${REQUESTS} -c ${CONCURRENT} "${HOME_HOST}/home" >/dev/null

echo "* Testing 20 iterations of ${BROWSE_HOST}/browse"
for (( i = 1; i < 20; i++ )); do
    ab -n ${REQUESTS} -c ${CONCURRENT} "${BROWSE_HOST}/browse/product/$i" >/dev/null
done



echo "* Testing 20 iterations of ${LOCATE_HOST}/locate"
for (( i = 0; i < 20; i++ )); do
    ab -n ${REQUESTS} -c ${CONCURRENT} "${LOCATE_HOST}/locate/$i?zipcode=12345" >/dev/null
done

