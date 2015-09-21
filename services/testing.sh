#!/bin/bash

TEMPLATE="{{(index ((index .items 0).status.loadBalancer.ingress) 0).ip}}:{{(index ((index .items 0).spec.ports) 0).port}}"

echo -n "* Getting IP/Port information for each microservice..."
HOME_HOST="$(kubectl get services -l name=home-service -o template --template="${TEMPLATE}" | tr -d '[[:space:]]')"
BROWSE_HOST="$(kubectl get services -l name=browse-service -o template --template="${TEMPLATE}" | tr -d '[[:space:]]')"
LOCATE_HOST="$(kubectl get services -l name=locate-service -o template --template="${TEMPLATE}" | tr -d '[[:space:]]')"
echo "done"

echo "* Testing ${HOME_HOST}/home"
ab -n 10000 -c 10 ${HOME_HOST}/home >/dev/null 2>&1 &

echo "* Testing ${BROWSE_HOST}/browse"
for i in {1..100}; do
    ab -n 10 -c 10 ${BROWSE_HOST}/browse/$i >/dev/null 2>&1 &
done

echo "* Testing ${LOCATE_HOST}/locate"
for i in {1..100}; do
    ab -n 10 -c 10 ${LOCATE_HOST}/locate/$i?zipcode=12345 >/dev/null 2>&1 &
done
