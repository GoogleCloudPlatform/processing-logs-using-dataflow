#!/bin/bash

mvn compile exec:java \
-Dexec.mainClass=com.google.cloud.solutions.LogAnalyticsPipeline \
-Dexec.args="\
--project=cloud-solutions-group \
--stagingLocation=gs://solutions-project-logs/staging \
--runner=BlockingDataflowPipelineRunner \
--configFile=LogAnalyticsConfig.properties"