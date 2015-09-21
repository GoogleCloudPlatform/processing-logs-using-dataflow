# Deploying Test Microservices

## Cluster Setup

    $ ./cluster.sh up [project-id] [cluster-name]

## Logging Setup

    $ ./logging.sh up [project-id] [home-logs-destination] [browse-logs-destination] [locate-logs-destination]

## Testing Microservices

    $ ./testing.sh

## Cleanup

    $ ./logging.sh down [project-id] [home-logs-destination] [browse-logs-destination] [locate-logs-destination]
    $ ./cluster.sh down [project-id] [cluster-name]