#!/bin/bash

mvn compile exec:java \
-Dexec.mainClass=com.google.cloud.solutions.LogAnalyticsPipeline \
-Dexec.args="\
--project=parikhs-dataflow-test \
--stagingLocation=gs://k8slogs/staging \
--runner=BlockingDataflowPipelineRunner \
--homeLogSource=gs://k8slogs/kubernetes.home-service*/*/*/*/*.json \
--browseLogSource=gs://k8slogs/kubernetes.browse-service*/*/*/*/*.json \
--locateLogSource=gs://k8slogs/kubernetes.locate-service*/*/*/*/*.json \
--pipelineConfigFile=LogAnalyticsConfig.properties"