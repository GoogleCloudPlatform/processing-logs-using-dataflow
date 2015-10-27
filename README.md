# Building Log Analytics Pipelines using Cloud Dataflow

This tutorial demonstrates how to use [Cloud Dataflow](http://cloud.google.com/dataflow) to analyze logs collected and exported by [Cloud Logging](http://cloud.google.com/logging), highlighting support for batch and streaming, multiple data sources, windowing, aggregations, and [BigQuery](http://cloud.google.com/bigquery) output.

For more information, refer to the [Building Log Analytics Pipelines using Cloud Dataflow](http://cloud.google.com/solutions/building-log-analytics-pipelines-using-cloud-dataflow) solution paper.

## Prerequisites

* [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (version 1.7 or greater)
* [Maven](http://maven.apache.org) (version 3 or greater)
* [Google Cloud Platform](http://cloud.google.com) account
* Install and setup the [Google Cloud SDK](https://cloud.google.com/sdk/)

After installing the Google Cloud SDK, install/update the following additional components using the `gcloud components update` command:

* App Engine Command Line Interface (Preview)
* BigQuery Command Line Tool
* Cloud SDK Core Libraries
* gcloud Alpha Commands
* gcloud Beta Commands
* gcloud app Python Extensions
* kubectl

Now, set your preferred zone and project:

    $ gcloud config set compute/zone ZONE
    $ gcloud config set project PROJECT-ID

Finally, ensure the following APIs are enabled in the [Google Cloud Platform Console](https://console.developers.google.com/) (navigate to "APIs & auth" > "APIs"):

* BigQuery
* Google Cloud Dataflow
* Google Cloud Logging
* Google Cloud Pub/Sub
* Google Cloud Storage
* Google Container Engine

## Sample Web Applications

The `services` folder contains three simple applications (built using [Go](http://golang.org) and the [Gin](https://github.com/gin-gonic/gin) HTTP web framework). These applications will generate the logs to be analyzed by the Dataflow pipeline (below). The applications have been packaged as Docker images and are available via [Google Container Registry](https://gcr.io). **Note:** If you are interested in editing/updating these applications, refer to the [README](https://github.com/GoogleCloudPlatform/dataflow-log-analytics/tree/master/services).

Inside the `services` folder are several scripts that can be used to facilitate deployment, configuration, and testing of the sample web applications.

### Deploy Container Engine Cluster

First, change the current directory to `services`:

    $ cd dataflow-log-analytics/services

Next, deploy the Container Engine cluster along with the sample web applications:

    $ ./cluster.sh CLUSTER-NAME up PROJECT-ID

The script will deploy a single-node Container Engine cluster, deploy the web applications, and expose the applications as Kubernetes Services.

### Setup Cloud Logging

The next step is to configure Cloud Logging to export the web application logs to Google Cloud Storage. The following script will first create a Cloud Storage bucket, configure the appropriate permissions, and setup automated export from the web applications running on Container Engine to Cloud Storage.

    $ ./logging.sh batch up PROJECT-ID BUCKET-NAME

### Generate Requests

Now that the applications have been deployed and are logging via Cloud Logging, the following script can be used to generate requests against the applications:

    $ ./load.sh REQUESTS CONCURRENCY

The script uses the Apache Bench [ab](https://httpd.apache.org/docs/2.2/programs/ab.html) tool to generate load against the deployed web applications. `REQUESTS` controls how many requests are issued to each application and `CONCURRENCY` controls how many concurrent requests are issued. The logs from the applications are shipped/exported to Cloud Storage in hourly batches, and it might take up to two hours before log entries begin to appear. Refer to the [Cloud Logging documentation](https://cloud.google.com/logging/docs/export/using_exported_logs) for more information.

### Examining Logs

For information on examining logs or log structure in Cloud Storage, refer to the [Cloud Logging documentation](https://cloud.google.com/logging/docs/export/using_exported_logs#log_entries_in_google_cloud_storage)

## Dataflow Pipeline

The following diagram illustrates the structure and flow of the example Dataflow pipeline:

![Dataflow pipeline structure](images/dataflow-log-analytics-pipeline.png)

### Create BigQuery Dataset

Before deploying the pipeline, create the BigQuery dataset where output from the Dataflow pipeline will be stored:
    
    $ gcloud alpha bigquery datasets create DATASET-NAME

### Execute Pipeline

First, change the current directory to `dataflow`:

    $ cd dataflow-log-analytics/dataflow

Next, execute the pipeline using `mvn`:

    $ mvn compile exec:java \
        -Dexec.mainClass=com.google.cloud.solutions.LogAnalyticsPipeline \
        -Dexec.args="\
        --project=PROJECT-ID \
        --stagingLocation=gs://BUCKET-NAME/staging \
        --runner=BlockingDataflowPipelineRunner \
        --homeLogSource=gs://BUCKET-NAME/kubernetes.home-service*/*/*/*/*.json \
        --browseLogSource=gs://BUCKET-NAME/kubernetes.browse-service*/*/*/*/*.json \
        --locateLogSource=gs://BUCKET-NAME/kubernetes.locate-service*/*/*/*/*.json \
        --allLogsTableName=DATASET-NAME.all_logs_table \
        --maxRespTimeTableName=DATASET-NAME.max_response_time_table \
        --meanRespTimeTableName=DATASET-NAME.mean_response_time_table"

This command will build the code for the Dataflow pipeline, upload it to the specified staging area, and launch the job. To see all execution options available for this pipeline, run the following:

    $ mvn compile exec:java \
        -Dexec.mainClass=com.google.cloud.solutions.LogAnalyticsPipeline \
        -Dexec.args="--help=LogAnalyticsPipeline"

### Monitoring Pipeline

While the pipeline is running you can view its status from the [Google Developers Console](https://console.developers.google.com) and navigating to "Big Data" > "Cloud Dataflow". There, click on the running job ID and you can view a graphical rendering of the pipeline and examine job logging output along with information about each pipeline stage. Here is an example screenshot of a running Dataflow job:

![Running Dataflow job](images/dataflow-log-analytics-ui.png)

### View BigQuery Data

Once the job has completed execution, you can view the output in the [Google BigQuery console](https://bigquery.cloud.google.com) and issue queries against the data.

## Cleanup

To cleanup and remove all resources used in this example, refer to the following steps.

First, delete the BigQuery dataset:

    $ gcloud alpha bigquery datasets delete DATASET-NAME

Next, deactivate the Cloud Logging exports (**note:** this will delete the exports and the specified Cloud Storage bucket):

    $ cd dataflow-log-analytics/services
    $ ./logging.sh batch down PROJECT-ID BUCKET-NAME

Finally, delete the Container Engine cluster used to run the sample web applications:

    $ cd dataflow-log-analytics/services
    $ ./cluster.sh CLUSTER-NAME down PROJECT-ID
