/**
Copyright Google Inc. 2015
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
**/

package com.google.cloud.solutions;

import com.google.cloud.dataflow.sdk.options.DataflowPipelineOptions;
import com.google.cloud.dataflow.sdk.options.Default;
import com.google.cloud.dataflow.sdk.options.Description;

@SuppressWarnings("unused")
public interface LogAnalyticsPipelineOptions extends DataflowPipelineOptions {
    @Description("Location of /home logs, Cloud Storage path or Cloud Pub/Sub subscription")
    String getHomeLogSource();
    void setHomeLogSource(String homeLogSource);

    @Description("Location of /browse logs, Cloud Storage path or Cloud Pub/Sub subscription")
    String getBrowseLogSource();
    void setBrowseLogSource(String browseLogSource);

    @Description("Location of /locate logs, Cloud Storage path or Cloud Pub/Sub subscription")
    String getLocateLogSource();
    void setLocateLogSource(String locateLogSource);

    @Description("Regular expression pattern used to parse embedded log messages inside Cloud Logging entries")
    @Default.String("\\[GIN\\]\\s+(?<timestamp>\\d{4}/\\d{2}/\\d{2} \\- \\d{2}\\:\\d{2}\\:\\d{2}).*? (?<httpStatusCode>\\d{3}) .*?(?<responseTime>\\d+\\.?\\d*)(?<resolution>\\S{1,}) \\| (?<source>[0-9\\.:]+?) \\|\\S+?\\s+?\\S+?\\s+?(?<httpMethod>\\w+?)\\s+?(?<destination>[a-z0-9/]+)")
    String getLogRegexPattern();
    void setLogRegexPattern(String logRegexPattern);

    @Description("BigQuery table name for all-logs table")
    @Default.String("dataflow_log_analytics.all_logs_table")
    String getAllLogsTableName();
    void setAllLogsTableName(String allLogsTableName);

    @Description("BigQuery table schema for all-logs table, comma-separated values of [field-name]:[TYPE]")
    @Default.String("timestamp:TIMESTAMP,httpStatusCode:INTEGER,responseTime:FLOAT,source:STRING,httpMethod:STRING,destination:STRING")
    String getAllLogsTableSchema();
    void setAllLogsTableSchema(String allLogsTableSchema);

    @Description("BigQuery table name for max-response-time table")
    @Default.String("dataflow_log_analytics.destination_max_response_time_table")
    String getMaxRespTimeTableName();
    void setMaxRespTimeTableName(String maxRespTimeTableName);

    @Description("BigQuery table schema for max-response-time table, comma-separated values of [field-name]:[TYPE]")
    @Default.String("destination:STRING,aggResponseTime:FLOAT")
    String getMaxRespTimeTableSchema();
    void setMaxRespTimeTableSchema(String maxRespTimeTableSchema);

    @Description("BigQuery table name for mean-response-time table")
    @Default.String("dataflow_log_analytics.destination_mean_response_time_table")
    String getMeanRespTimeTableName();
    void setMeanRespTimeTableName(String meanRespTimeTableName);

    @Description("BigQuery table schema for mean-response-time table, comma-separated values of [field-name]:[TYPE]")
    @Default.String("destination:STRING,aggResponseTime:FLOAT")
    String getMeanRespTimeTableSchema();
    void setMeanRespTimeTableSchema(String meanRespTimeTableSchema);
}
