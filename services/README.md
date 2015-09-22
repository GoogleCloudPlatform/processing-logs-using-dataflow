# Deploying Test Microservices

## Microservices 
* Three endpoints (`home`, `browse`, `locate`)
* Each one was built with [Go](http://golang.org) and the [Gin](https://github.com/gin-gonic/gin) HTTP web framework

## Cluster Setup

    $ ./cluster.sh up [project-id] [cluster-name]

## Logging Setup

    $ ./logging.sh up [project-id]

## Testing Microservices

    $ ./testing.sh

## Cleanup

    $ ./logging.sh down [project-id]
    $ ./cluster.sh down [project-id] [cluster-name]