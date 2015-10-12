package com.google.cloud.solutions;

import com.google.api.client.json.JsonParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.api.services.logging.model.LogEntry;
import com.google.cloud.dataflow.sdk.Pipeline;
import com.google.cloud.dataflow.sdk.PipelineResult;
import com.google.cloud.dataflow.sdk.io.BigQueryIO;
import com.google.cloud.dataflow.sdk.io.PubsubIO;
import com.google.cloud.dataflow.sdk.io.TextIO;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.dataflow.sdk.transforms.*;
import com.google.cloud.dataflow.sdk.transforms.windowing.FixedWindows;
import com.google.cloud.dataflow.sdk.transforms.windowing.Window;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.PCollection;
import com.google.cloud.dataflow.sdk.values.PCollectionList;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogAnalyticsPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(LogAnalyticsPipeline.class);
    
    private static class EmitLogMessageFn extends DoFn<String,LogMessage> {
        private boolean outputWithTimestamp;
        private String regexPattern;

        public EmitLogMessageFn(boolean outputWithTimestamp, String regexPattern) {
            this.outputWithTimestamp = outputWithTimestamp;
            this.regexPattern = regexPattern;
        }

        @Override
        public void processElement(ProcessContext c) {
            Instant timestamp = getTimestampFromEntry(c.element());
            LogMessage logMessage = parseEntry(c.element());
            if(logMessage != null) {
                if(this.outputWithTimestamp) {
                    c.outputWithTimestamp(logMessage, timestamp);
                }
                else {
                    c.output(logMessage);
                }
            }
        }

        private Instant getTimestampFromEntry(String entry) {
            String timestamp = "";
            DateTimeFormatter fmt = ISODateTimeFormat.dateTimeNoMillis();

            try {
                JsonParser parser = new JacksonFactory().createJsonParser(entry);
                LogEntry logEntry = parser.parse(LogEntry.class);
                timestamp = logEntry.getMetadata().getTimestamp();
            }
            catch (IOException e) {
                LOG.error("IOException converting Cloud Logging JSON to LogEntry");
            }

            return fmt.parseDateTime(timestamp).toInstant();
        }

        private LogMessage parseEntry(String entry) {
            String logString = "";

            try {
                JsonParser parser = new JacksonFactory().createJsonParser(entry);
                LogEntry logEntry = parser.parse(LogEntry.class);
                logString = logEntry.getStructPayload().get("log").toString();
            }
            catch (IOException e) {
                LOG.error("IOException converting Cloud Logging JSON to LogEntry");
            }

            Pattern p = Pattern.compile(this.regexPattern);
            Matcher m = p.matcher(logString);

            if(m.find()) {
                DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy/MM/dd - HH:mm:ss");
                Instant timestamp = fmt.parseDateTime(m.group("timestamp")).toInstant();
                int httpStatusCode = Integer.valueOf(m.group("httpStatusCode"));

                double responseTime = 0;
                if(m.group("resolution").equals("ns")) {
                    responseTime = Double.valueOf(m.group("responseTime")) / 1e9;
                }
                else if(m.group("resolution").equals("µs")) {
                    responseTime = Double.valueOf(m.group("responseTime")) / 1e6;
                }
                else if(m.group("resolution").equals("ms")) {
                    responseTime = Double.valueOf(m.group("responseTime")) / 1e3;
                }

                String source = m.group("source");
                String httpMethod = m.group("httpMethod");
                String destination = m.group("destination");

                return new LogMessage(timestamp, httpStatusCode, responseTime, source, httpMethod, destination);
            }
            else {
                return null;
            }
        }
    }

    private static class LogMessageTableRowFn extends DoFn<LogMessage, TableRow> {
        @Override
        public void processElement(ProcessContext c) {
            LogMessage e = c.element();

            TableRow row = new TableRow()
              .set("timestamp", new DateTime(e.getTimestamp().toDateTime().toDate()))
              .set("httpStatusCode", e.getHttpStatusCode())
              .set("responseTime", e.getResponseTime())
              .set("source", e.getSource())
              .set("httpMethod", e.getHttpMethod())
              .set("destination", e.getDestination());

            c.output(row);
        }
    }

    private static class TableRowOutputTransform extends PTransform<PCollection<KV<String,Double>>,PCollection<TableRow>> {
        private String tableSchema;
        private String tableName;

        public TableRowOutputTransform(String tableSchema, String tableName) {
            this.tableSchema = tableSchema;
            this.tableName = tableName;
        }

        private static TableSchema createTableSchema(String schema) {
            String[] fieldTypePairs = schema.split(",");
            List<TableFieldSchema> fields = new ArrayList<TableFieldSchema>();

            for(String entry : fieldTypePairs) {
                String[] fieldAndType = entry.split(":");
                fields.add(new TableFieldSchema().setName(fieldAndType[0]).setType(fieldAndType[1]));
            }

            return new TableSchema().setFields(fields);
        }

        @Override
        public PCollection<TableRow> apply(PCollection<KV<String,Double>> input) {
            PCollection<TableRow> output = input.
              apply(ParDo.named("aggregateToTableRow").of(new DoFn<KV<String, Double>, TableRow>() {
                  @Override
                  public void processElement(ProcessContext c) {
                      KV<String, Double> e = c.element();

                      TableRow row = new TableRow()
                        .set("destination", e.getKey())
                        .set("aggResponseTime", e.getValue());

                      c.output(row);
                  }
              }));

            output.apply(BigQueryIO.Write
              .named("tableRowToBigQuery")
              .to(this.tableName)
              .withSchema(createTableSchema(this.tableSchema))
              .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
              .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED));

            return output;
        }
    }

    public static void main(String[] args) {
        // Setup LogAnalyticsPipelineOptions
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
        
        if(options.isStreaming()) {
            outputWithTimestamp = false;
            homeLogs = p.apply(PubsubIO.Read.named("homeLogsPubSubRead").subscription(options.getHomeLogSource()));
            browseLogs = p.apply(PubsubIO.Read.named("browseLogsPubSubRead").subscription(options.getBrowseLogSource()));
            locateLogs = p.apply(PubsubIO.Read.named("locateLogsPubSubRead").subscription(options.getLocateLogSource()));
        }
        else {
            outputWithTimestamp = true;
            homeLogs = p.apply(TextIO.Read.named("homeLogsTextRead").from(options.getHomeLogSource()));
            browseLogs = p.apply(TextIO.Read.named("browseLogsTextRead").from(options.getBrowseLogSource()));
            locateLogs = p.apply(TextIO.Read.named("locateLogsTextRead").from(options.getLocateLogSource()));
        }

        PCollection<String> allLogs = PCollectionList
          .of(homeLogs)
          .and(browseLogs)
          .and(locateLogs)
          .apply(Flatten.<String>pCollections());

        PCollection<LogMessage> logCollection = allLogs
          .apply(ParDo.named("allLogsToLogMessage").of(new EmitLogMessageFn(outputWithTimestamp, options.getLogRegexPattern())))
          .apply(Window.named("allLogMessageToDaily").<LogMessage>into(FixedWindows.of(Duration.standardDays(1))));

        // Create new PCollection containing LogMessage->TableRow
        PCollection<TableRow> logsAsTableRows = logCollection
          .apply(ParDo.named("logMessageToTableRow").of(new LogMessageTableRowFn()));

        // Output TableRow PCollection into BigQuery
        // - Append all writes to existing rows
        // - Create the table if it does not exist already
        TableSchema allLogsTableSchema = TableRowOutputTransform.createTableSchema(options.getAllLogsTableSchema());
        logsAsTableRows.apply(BigQueryIO.Write
          .named("allLogsToBigQuery")
          .to(options.getAllLogsTableName())
          .withSchema(allLogsTableSchema)
          .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
          .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED));

        // Create new PCollection
        // - Contains "destination,responseTime" key-value pairs
        // - Used for running simple aggregations
        PCollection<KV<String,Double>> destResponseTimeCollection = logCollection
          .apply(ParDo.named("logMessageToDestRespTime").of(new DoFn<LogMessage, KV<String, Double>>() {
              @Override
              public void processElement(ProcessContext processContext) throws Exception {
                  LogMessage l = processContext.element();
                  processContext.output(KV.of(l.getDestination(), l.getResponseTime()));
              }
          }));

        PCollection<TableRow> destMaxRespTimeRows = destResponseTimeCollection
          .apply(Combine.<String,Double,Double>perKey(new Max.MaxDoubleFn()))
          .apply(new TableRowOutputTransform(options.getMaxRespTimeTableSchema(), options.getMaxRespTimeTableName()));

        PCollection<TableRow> destMeanRespTimeRows = destResponseTimeCollection
          .apply(Mean.<String,Double>perKey())
          .apply(new TableRowOutputTransform(options.getMeanRespTimeTableSchema(), options.getMeanRespTimeTableName()));

        PipelineResult r = p.run();

        LOG.info(r.toString());
    }
}
