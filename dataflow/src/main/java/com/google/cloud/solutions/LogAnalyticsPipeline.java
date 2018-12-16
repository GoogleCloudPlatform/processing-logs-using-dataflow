/*
  Copyright Google Inc. 2015
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  <p>
  http://www.apache.org/licenses/LICENSE-2.0
  <p>
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package com.google.cloud.solutions;

import com.google.api.client.json.JsonParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;

import com.google.api.services.logging.v2.model.LogEntry;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.transforms.display.DisplayData;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogAnalyticsPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(LogAnalyticsPipeline.class);

    /**
     * EmitLogMessageFn is a custom DoFn which transforms String objects to LogMessage objects
     * - The input String is a Cloud Logging LogEntry JSON object
     * - The "structPayload.log" field contains the log message to be parsed
     */
    private static class EmitLogMessageFn extends DoFn<String, LogMessage> {
        private boolean outputWithTimestamp;
        private String regexPattern;

        public EmitLogMessageFn(boolean outputWithTimestamp, String regexPattern) {
            this.outputWithTimestamp = outputWithTimestamp;
            this.regexPattern = regexPattern;
        }


        @ProcessElement
        public void processElement(@Element String element, OutputReceiver<LogMessage> receiver) {
            LogMessage logMessage = parseEntry(element);
            if (logMessage != null) {
                if (this.outputWithTimestamp) {
                    receiver.outputWithTimestamp(logMessage, logMessage.getTimestamp());
                } else {
                    receiver.output(logMessage);
                }
            }
        }

        private LogMessage parseEntry(String entry) {
            String logString = "";

            try {
                JsonParser parser = new JacksonFactory().createJsonParser(entry);
                LogEntry logEntry = parser.parse(LogEntry.class);
                logString = logEntry.getTextPayload();
            } catch (IOException e) {
                LOG.error("IOException parsing entry: " + e.getMessage());
            } catch (NullPointerException e) {
                LOG.error("NullPointerException parsing entry: " + e.getMessage());
            }

            Pattern p = Pattern.compile(this.regexPattern);
            Matcher m = p.matcher(logString);

            if (m.find()) {
                DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy/MM/dd - HH:mm:ss");
                Instant timestamp = fmt.parseDateTime(m.group("timestamp")).toInstant();
                int httpStatusCode = Integer.valueOf(m.group("httpStatusCode"));

                double responseTime = 0;
                if (m.group("resolution").equals("ns")) {
                    responseTime = Double.valueOf(m.group("responseTime")) / 1e9;
                } else if (m.group("resolution").equals("µs")) {
                    responseTime = Double.valueOf(m.group("responseTime")) / 1e6;
                } else if (m.group("resolution").equals("ms")) {
                    responseTime = Double.valueOf(m.group("responseTime")) / 1e3;
                }

                String source = m.group("source");
                String httpMethod = m.group("httpMethod");
                String destination = m.group("destination");

                return new LogMessage(timestamp, httpStatusCode, responseTime, source, httpMethod, destination);
            } else {
                return null;
            }
        }
    }

    /**
     * LogMessageTableRowFn is a custom DoFn
     * - Transforms LogMessage objects to BigQuery TableRow objects
     */
    private static class LogMessageTableRowFn extends DoFn<LogMessage, TableRow> {
        @ProcessElement
        public void processElement(@Element LogMessage msg, OutputReceiver<TableRow> receiver) {
            TableRow row = new TableRow()
                    .set("timestamp", msg.getTimestamp().toString())
                    .set("httpStatusCode", msg.getHttpStatusCode())
                    .set("responseTime", msg.getResponseTime())
                    .set("source", msg.getSource())
                    .set("httpMethod", msg.getHttpMethod())
                    .set("destination", msg.getDestination());

            receiver.output(row);
        }
    }

    /**
     * TableRowOutputTransform is a custom PTransform that transforms
     * - An input PCollection<KV<String,Double>> to an output PCollection<TableRow>
     * - Creates a BigQuery TableSchema from an input String
     * - Writes the output PCollection<TableRow> to BigQuery
     */
    private static class TableRowOutputTransform extends PTransform<PCollection<KV<String, Double>>, PCollection<TableRow>> {
        private String tableSchema;
        private String tableName;

        public TableRowOutputTransform(String tableSchema, String tableName) {
            this.tableSchema = tableSchema;
            this.tableName = tableName;
        }

        public static TableSchema createTableSchema(String schema) {
            String[] fieldTypePairs = schema.split(",");
            List<TableFieldSchema> fields = new ArrayList<TableFieldSchema>();

            for (String entry : fieldTypePairs) {
                String[] fieldAndType = entry.split(":");
                fields.add(new TableFieldSchema().setName(fieldAndType[0]).setType(fieldAndType[1]));
            }

            return new TableSchema().setFields(fields);
        }


        @Override
        public PCollection<TableRow> expand(PCollection<KV<String, Double>> input) {
            PCollection<TableRow> output = input.
                    apply(ParDo.of(new DoFn<KV<String, Double>, TableRow>() {
                        @ProcessElement
                        public void processElement(ProcessContext c) {
                            KV<String, Double> e = c.element();

                            TableRow row = new TableRow()
                                    .set("destination", e.getKey())
                                    .set("aggResponseTime", e.getValue());

                            c.output(row);
                        }
                    }));

            output.apply(BigQueryIO.<TableRow>write()
                    .to(this.tableName)
                    .withSchema(createTableSchema(this.tableSchema))
                    .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
                    .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED)
                    .withFormatFunction(new SimpleFunction<TableRow, TableRow>() {
                        @Override
                        public TableRow apply(TableRow input) {
                            return input;
                        }
                    }));


            return output;
        }
    }

    public static void main(String[] args) {
        PipelineOptionsFactory.register(LogAnalyticsPipelineOptions.class);
        LogAnalyticsPipelineOptions options = PipelineOptionsFactory
                .fromArgs(args)
                .withValidation()
                .as(LogAnalyticsPipelineOptions.class);

        Pipeline p = Pipeline.create(options);

        PCollection<String> homeLogs;
        PCollection<String> browseLogs;
        PCollection<String> locateLogs;
        boolean outputWithTimestamp;

        /*
          If the pipeline is started in "streaming" mode, treat the input sources as Pub/Sub subscriptions
         */
        if (options.isStreaming()) {
            outputWithTimestamp = false;
//            homeLogs = p.apply(PubsubIO.readStrings().fromSubscription(options.getHomeLogSource()));
//            browseLogs = p.apply(PubsubIO.readStrings().fromSubscription(options.getBrowseLogSource()));
            locateLogs = p.apply(PubsubIO.readStrings().fromSubscription(options.getLocateLogSource()));
        }
        /*
          If the pipeline is not started in "streaming" mode, treat the input sources as Cloud Storage paths
         */
        else {
            outputWithTimestamp = true;
            // [START readingData]
//            homeLogs = p.apply(TextIO.read().from(options.getHomeLogSource()));
//            browseLogs = p.apply(TextIO.read().from(options.getBrowseLogSource()));
            locateLogs = p.apply(TextIO.read().from(options.getLocateLogSource()));
            // [END readingData]
        }

        /*
          Flatten all input PCollections into a single PCollection
         */
        // [START flattenCollections]
        PCollection<String> allLogs = PCollectionList
                .of(locateLogs)
//                .and(browseLogs)
//                .and(locateLogs)
                .apply(Flatten.<String>pCollections());
        // [END flattenCollections]

        /*
          Transform "allLogs" PCollection<String> to PCollection<LogMessage> and apply custom windowing scheme
         */
        // [START transformStringToLogMessage]
        PCollection<LogMessage> allLogMessages = allLogs
                .apply(ParDo.of(new EmitLogMessageFn(outputWithTimestamp, options.getLogRegexPattern())));
        // [END transformStringToLogMessage]

        // [START applyWindowing]
        PCollection<LogMessage> allLogMessagesDaily = allLogMessages
                .apply(Window.<LogMessage>into(FixedWindows.of(Duration.standardDays(1))));
        // [END applyWindowing]

        //language=RegExp
        String foo = "\\[GIN\\]\\s+(?<timestamp>\\d{4}/\\d{2}/\\d{2} \\- \\d{2}\\:\\d{2}\\:\\d{2}).*? (?<httpStatusCode>\\d{3}) .*?(?<responseTime>\\d+\\.?\\d*)(?<resolution>\\S{1,}) \\| (?<source>[0-9\\.:]+?) \\|\\S+?\\s+?\\S+?\\s+?(?<httpMethod>\\w+?)\\s+?(?<destination>[a-z0-9/]+)";
        /*
          Transform "allLogs" PCollection<LogMessage> to PCollection<TableRow>
         */
        // [START logMessageToTableRow]
        PCollection<TableRow> logsAsTableRows = allLogMessagesDaily
                .apply(ParDo.of(new LogMessageTableRowFn()));
        // [END logMessageToTableRow]

        /*
          Output "allLogs" PCollection<TableRow> to BigQuery
         */
        TableSchema allLogsTableSchema = TableRowOutputTransform.createTableSchema(options.getAllLogsTableSchema());
        // [START allLogsToBigQuery]
        logsAsTableRows.apply(BigQueryIO.<TableRow>write()
                .withFormatFunction(new SimpleFunction<TableRow, TableRow>() {
                    @Override
                    public TableRow apply(TableRow input) {
                        return input;
//                        return super.apply(input);
                    }
                })
                .to(options.getAllLogsTableName())
                .withSchema(allLogsTableSchema)
                .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
                .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED));
        // [END allLogsToBigQuery]

        /*
          Create new PCollection<KV<String,Double>>
          - Contains "destination->responseTime" key-value pairs
          - Used for computing responseTime aggregations
         */
        PCollection<KV<String, Double>> destResponseTimeCollection = allLogMessagesDaily
                .apply(ParDo.of(new DoFn<LogMessage, KV<String, Double>>() {

                    @ProcessElement
                    public void processElement(ProcessContext processContext) throws Exception {
                        LogMessage l = processContext.element();
                        processContext.output(KV.of(l.getDestination(), l.getResponseTime()));
                    }
                }));

        /*
          Transform PCollection<KV<String,Double>> to PCollection<TableRow>
          - First aggregate "destination->responseTime" key-value pairs into
            - destination->maxResponseTime and destination->meanResponseTime
          - Use custom PTransform to output PCollection to BigQuery
         */

        // [START computeAggregations]
        PCollection<KV<String, Double>> destMaxRespTime = destResponseTimeCollection
                .apply(Combine.<String, Double, Double>perKey(Max.ofDoubles()));

        PCollection<KV<String, Double>> destMeanRespTime = destResponseTimeCollection
                .apply(Mean.<String, Double>perKey());
        // [END computeAggregations]


        // see https://beam.apache.org/releases/javadoc/2.3.0/org/apache/beam/sdk/io/gcp/bigquery/BigQueryIO.html
        PCollection<TableRow> destMaxRespTimeRows = destMaxRespTime
                .apply(new TableRowOutputTransform(options.getMaxRespTimeTableSchema(), options.getMaxRespTimeTableName()));

        PCollection<TableRow> destMeanRespTimeRows = destMeanRespTime
                .apply(new TableRowOutputTransform(options.getMeanRespTimeTableSchema(), options.getMeanRespTimeTableName()));

        PipelineResult r = p.run();

        LOG.info(r.toString());
    }
}
