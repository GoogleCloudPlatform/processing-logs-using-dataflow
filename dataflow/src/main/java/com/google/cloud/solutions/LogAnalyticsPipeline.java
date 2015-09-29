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
import com.google.cloud.dataflow.sdk.io.TextIO;
import com.google.cloud.dataflow.sdk.options.DataflowPipelineOptions;
import com.google.cloud.dataflow.sdk.options.DataflowWorkerLoggingOptions;
import com.google.cloud.dataflow.sdk.options.Description;
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogAnalyticsPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(LogAnalyticsPipeline.class);

    @SuppressWarnings("unused")
    public interface LogAnalyticsPipelineOptions extends DataflowPipelineOptions {
        @Description("Log sources configuration file")
        String getConfigFile();
        void setConfigFile(String configFile);
    }

    private static Properties parseConfigFile(String configFileName) {
        Properties p = new Properties();
        InputStream configFile = null;

        try {
            LOG.debug("loading properties from " + configFileName);
            configFile = new FileInputStream(configFileName);
            p.load(configFile);
            LOG.debug("loaded " + p.size() + " properties");
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        finally {
            if(configFile != null) {
                try {
                    configFile.close();
                }
                catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return p;
    }

    private static class EmitLogMessageFn extends DoFn<String,LogMessage> {
        private String regexPattern;

        public EmitLogMessageFn(String regexPattern) {
            this.regexPattern = regexPattern;
        }

        @Override
        public void processElement(ProcessContext c) {
            Instant timestamp = getTimestampFromEntry(c.element());
            LogMessage logMessage = parseEntry(c.element());
            if(logMessage != null) {
                c.outputWithTimestamp(logMessage, timestamp);
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

    private static class AggregateTableRowFn extends DoFn<KV<String,Double>, TableRow> {
        @Override
        public void processElement(ProcessContext c) {
            KV<String,Double> e = c.element();

            TableRow row = new TableRow()
              .set("destination", e.getKey())
              .set("aggResponseTime", e.getValue());

            c.output(row);
        }
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

    public static void main(String[] args) {
        // Setup LogAnalyticsPipelineOptions
        PipelineOptionsFactory.register(LogAnalyticsPipelineOptions.class);
        LogAnalyticsPipelineOptions options = PipelineOptionsFactory
          .fromArgs(args)
                .withValidation()
                .as(LogAnalyticsPipelineOptions.class);

        // Set DEBUG log level for all workers
        options.setDefaultWorkerLogLevel(DataflowWorkerLoggingOptions.Level.DEBUG);

        // Get .properties file containing pipeline options
        Properties props = parseConfigFile(options.getConfigFile());
        String logRegexPattern = props.getProperty("logRegexPattern");

        // Create pipeline
        Pipeline p = Pipeline.create(options);

        // PCollection from
        // - Cloud Storage source
        // - Extract individual LogMessage objects from each PubSub CloudLogging message (structPayload.log)
        // - Change windowing from Global to 1 day fixed windows
        PCollection<LogMessage> homeLogsDaily = p
          .apply(TextIO.Read.named("homeLogsRead").from(props.getProperty("homeLogSource")))
          .apply(ParDo.named("homeLogsToLogEntry").of(new EmitLogMessageFn(logRegexPattern)))
          .apply(Window.named("homeLogEntryToDaily").<LogMessage>into(FixedWindows.of(Duration.standardDays(1))));

        // PCollection from
        // - Cloud Storage source
        // - Extract individual LogMessage objects from CloudLogging message (structPayload.log)
        // - Change windowing from "all" to 1 day fixed windows
        PCollection<LogMessage> browseLogsDaily = p
          .apply(TextIO.Read.named("browseLogsRead").from(props.getProperty("browseLogSource")))
          .apply(ParDo.named("browseLogsToLogEntry").of(new EmitLogMessageFn(logRegexPattern)))
          .apply(Window.named("browseLogEntryToDaily").<LogMessage>into(FixedWindows.of(Duration.standardDays(1))));

        // PCollection from
        // - Cloud Storage source
        // - Extract individual LogMessage objects from CloudLogging message (structPayload.log)
        // - Change windowing from "all" to 1 day fixed windows
        PCollection<LogMessage> locateLogsDaily = p
          .apply(TextIO.Read.named("locateLogsRead").from(props.getProperty("locateLogSource")))
          .apply(ParDo.named("locateLogsToLogEntry").of(new EmitLogMessageFn(logRegexPattern)))
          .apply(Window.named("locateLogEntryToDaily").<LogMessage>into(FixedWindows.of(Duration.standardDays(1))));

        // Create a single PCollection by flattening three (windowed) PCollections
        PCollection<LogMessage> logCollection = PCollectionList
          .of(homeLogsDaily)
          .and(browseLogsDaily)
          .and(locateLogsDaily)
          .apply(Flatten.<LogMessage>pCollections());

        // Create new PCollection containing LogMessage->TableRow
        PCollection<TableRow> logsAsTableRows = logCollection
          .apply(ParDo.named("logMessageToTableRow").of(new LogMessageTableRowFn()));

        // Output TableRow PCollection into BigQuery
        // - Append all writes to existing rows
        // - Create the table if it does not exist already
        TableSchema allLogsTableSchema = createTableSchema(props.getProperty("allLogsTableSchema"));
        logsAsTableRows.apply(BigQueryIO.Write
          .named("allLogsToBigQuery")
          .to(props.getProperty("allLogsTableName"))
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

        // Create new PCollection
        // - First, find the max responseTime for each destination
        // - Convert the result into PCollection of TableRow
        PCollection<TableRow> destMaxRespTimeRows = destResponseTimeCollection
          .apply(Combine.<String,Double,Double>perKey(new Max.MaxDoubleFn()))
          .apply(ParDo.named("maxRespTimeToTableRow").of(new AggregateTableRowFn()));

        // Output destination->maxResponseTime PCollection to BigQuery
        TableSchema maxRespTimeTableSchema = createTableSchema(props.getProperty("maxRespTimeTableSchema"));
        destMaxRespTimeRows.apply(BigQueryIO.Write
          .named("maxRespTimeToBigQuery")
          .to(props.getProperty("maxRespTimeTableName"))
          .withSchema(maxRespTimeTableSchema)
          .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
          .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED));

        // Create new PCollection
        // - First, compute the mean responseTime for each destination
        // - Convert the result into PCollection of TableRow
        PCollection<TableRow> destMeanRespTimeRows = destResponseTimeCollection
          .apply(Mean.<String,Double>perKey())
          .apply(ParDo.named("meanRespTimeToTableRow").of(new AggregateTableRowFn()));

        // Output destination->meanResponseTime PCollection to BigQuery
        TableSchema meanRespTimeTableSchema = createTableSchema(props.getProperty("meanRespTimeTableSchema"));
        destMeanRespTimeRows.apply(BigQueryIO.Write
          .named("meanRespTimeToBigQuery")
          .to(props.getProperty("meanRespTimeTableName"))
          .withSchema(meanRespTimeTableSchema)
          .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
          .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED));

        PipelineResult r = p.run();

        LOG.info(r.toString());
    }
}
