#!/bin/bash

mvn compile exec:java \
-Dexec.mainClass=com.google.cloud.solutions.LogAnalyticsPipeline \
-Dexec.args="\
--project=parikhs-dataflow-test \
--stagingLocation=gs://k8slogs/staging \
--runner=DirectPipelineRunner \
--configFile=LogAnalyticsConfig.properties"

# --runner=BlockingDataflowPipelineRunner \
