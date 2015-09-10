package com.google.cloud.solutions;

import com.google.api.services.bigquery.model.TableCell;
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.cloud.dataflow.sdk.Pipeline;
import com.google.cloud.dataflow.sdk.PipelineResult;
import com.google.cloud.dataflow.sdk.io.BigQueryIO;
import com.google.cloud.dataflow.sdk.io.PubsubIO;
import com.google.cloud.dataflow.sdk.io.TextIO;
import com.google.cloud.dataflow.sdk.options.DataflowPipelineOptions;
import com.google.cloud.dataflow.sdk.options.DataflowWorkerLoggingOptions;
import com.google.cloud.dataflow.sdk.options.Description;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.dataflow.sdk.transforms.*;
import com.google.cloud.dataflow.sdk.transforms.windowing.CalendarWindows;
import com.google.cloud.dataflow.sdk.transforms.windowing.Window;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.PCollection;
import com.google.cloud.dataflow.sdk.values.PCollectionList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

    private static class EmitLogEntryFn extends DoFn<String,LogEntry> {
        private boolean withTimestamp;
        private String regexPattern;

        public EmitLogEntryFn(boolean withTimestamp, String regexPattern) {
            this.withTimestamp = withTimestamp;
            this.regexPattern = regexPattern;
        }

        @Override
        public void processElement(ProcessContext c) {
            if(withTimestamp) {
                Instant timestamp = getTimestampFromEntry(c.element());
                LogEntry logEntry = parseEntry(c.element());
                c.outputWithTimestamp(logEntry, timestamp);
            }
            else {
                LogEntry logEntry = parseEntry(c.element());
                c.output(logEntry);
            }
        }

        private Instant getTimestampFromEntry(String entry) {
            JsonParser parser = new JsonParser();
            JsonElement rootElement = parser.parse(entry);

            JsonObject metadata = rootElement
              .getAsJsonObject()
              .getAsJsonObject("metadata");
            JsonObject timestamp = metadata.getAsJsonObject("timestamp");

            DateTimeFormatter fmt = ISODateTimeFormat.dateTime();

            return fmt.parseDateTime(timestamp.getAsString()).toInstant();
        }

        private LogEntry parseEntry(String entry) {
            JsonParser parser = new JsonParser();
            JsonElement rootElement = parser.parse(entry);
            JsonObject structPayload = rootElement
              .getAsJsonObject()
              .getAsJsonObject("structPayload");
            JsonObject log = structPayload.getAsJsonObject("log");

            Pattern p = Pattern.compile(this.regexPattern);
            Matcher m = p.matcher(log.getAsString());

            DateTimeFormatter fmt = DateTimeFormat.forPattern("yyy/MM/dd - HH:mm:ss");
            Instant timestamp = fmt.parseDateTime(m.group(1)).toInstant();
            int httpStatusCode = Integer.valueOf(m.group(2));
            double responseTime = Double.valueOf(m.group(3)) / 1000000;
            String source = m.group(4);
            String httpMethod = m.group(5);
            String destination = m.group(6);

            return new LogEntry(timestamp, httpStatusCode, responseTime, source, httpMethod, destination);
        }
    }

    private static class LogEntryTableRowFn extends DoFn<LogEntry, TableRow> {
        @Override
        public void processElement(ProcessContext c) {
            LogEntry e = c.element();
            List<TableCell> cells = new ArrayList<TableCell>();

            cells.add(new TableCell().set("timestamp", e.getTimestamp().toDateTime()));
            cells.add(new TableCell().set("httpStatusCode", e.getHttpStatusCode()));
            cells.add(new TableCell().set("responseTime", e.getResponseTime()));
            cells.add(new TableCell().set("source", e.getSource()));
            cells.add(new TableCell().set("httpMethod", e.getHttpMethod()));
            cells.add(new TableCell().set("destination", e.getDestination()));

            c.output(new TableRow().setF(cells));
        }
    }

    private static class AggregateTableRowFn extends DoFn<KV<String,Double>, TableRow> {
        @Override
        public void processElement(ProcessContext c) {
            KV<String,Double> e = c.element();
            List<TableCell> cells = new ArrayList<TableCell>();

            cells.add(new TableCell().set("destination", e.getKey()));
            cells.add(new TableCell().set("responseTime", e.getValue()));

            c.output(new TableRow().setF(cells));
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
        // - PubSubIO source
        // - Extract individual LogEntry objects from each PubSub CloudLogging message (structPayload.log)
        // - Change windowing from Global to 1 day fixed windows
        PCollection<LogEntry> homeLogsDaily = p
          .apply(PubsubIO.Read.subscription(props.getProperty("homeLogSource")))
          .apply(ParDo.of(new EmitLogEntryFn(false, logRegexPattern)))
          .apply(Window.<LogEntry>into(CalendarWindows.days(1)));

        // PCollection from
        // - Cloud Storage source
        // - Extract individual LogEntry objects from CloudLogging message (structPayload.log)
        // - Change windowing from "all" to 1 day fixed windows
        PCollection<LogEntry> browseLogsDaily = p
          .apply(TextIO.Read.from(props.getProperty("browseLogSource")))
          .apply(ParDo.of(new EmitLogEntryFn(true, logRegexPattern)))
          .apply(Window.<LogEntry>into(CalendarWindows.days(1)));

        // PCollection from
        // - Cloud Storage source
        // - Extract individual LogEntry objects from CloudLogging message (structPayload.log)
        // - Change windowing from "all" to 1 day fixed windows
        PCollection<LogEntry> locateLogsDaily = p
          .apply(TextIO.Read.from(props.getProperty("locateLogSource")))
          .apply(ParDo.of(new EmitLogEntryFn(true, logRegexPattern)))          
          .apply(Window.<LogEntry>into(CalendarWindows.days(1)));

        // Create a single PCollection by flattening three (windowed) PCollections
        PCollection<LogEntry> logCollection = PCollectionList
          .of(homeLogsDaily)
          .and(browseLogsDaily)
          .and(locateLogsDaily)
          .apply(Flatten.<LogEntry>pCollections());

        // Create new PCollection containing LogEntry->TableRow
        PCollection<TableRow> logsAsTableRows = logCollection
          .apply(ParDo.of(new LogEntryTableRowFn()));

        // Output TableRow PCollection into BigQuery
        // - Append all writes to existing rows
        // - Create the table if it does not exist already
        TableSchema allLogsTableSchema = createTableSchema(props.getProperty("allLogsTableSchema"));
        logsAsTableRows.apply(BigQueryIO.Write
          .to(props.getProperty("allLogsTableName"))
          .withSchema(allLogsTableSchema)
          .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
          .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED));

        // Create new PCollection
        // - Contains "destination,responseTime" key-value pairs
        // - Used for running simple aggregations
        PCollection<KV<String,Double>> destResponseTimeCollection = logCollection
          .apply(ParDo.of(new DoFn<LogEntry, KV<String, Double>>() {
              @Override
              public void processElement(ProcessContext processContext) throws Exception {
                  LogEntry l = processContext.element();
                  processContext.output(KV.of(l.getDestination(), l.getResponseTime()));
              }
          }));

        // Create new PCollection
        // - First, find the max responseTime for each destination
        // - Convert the result into PCollection of TableRow
        PCollection<TableRow> destMaxRespTimeRows = destResponseTimeCollection
          .apply(Combine.<String,Double,Double>perKey(new Max.MaxDoubleFn()))
          .apply(ParDo.of(new AggregateTableRowFn()));

        // Output destination->maxResponseTime PCollection to BigQuery
        TableSchema maxRespTimeTableSchema = createTableSchema(props.getProperty("maxRespTimeTableSchema"));
        destMaxRespTimeRows.apply(BigQueryIO.Write
          .to(props.getProperty("maxRespTimeTableName"))
          .withSchema(maxRespTimeTableSchema)
          .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
          .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED));

        // Create new PCollection
        // - First, compute the mean responseTime for each destination
        // - Convert the result into PCollection of TableRow
        PCollection<TableRow> destMeanRespTimeRows = destResponseTimeCollection
          .apply(Mean.<String,Double>perKey())
          .apply(ParDo.of(new AggregateTableRowFn()));

        // Output destination->meanResponseTime PCollection to BigQuery
        TableSchema meanRespTimeTableSchema = createTableSchema(props.getProperty("meanRespTimeTableSchema"));
        destMeanRespTimeRows.apply(BigQueryIO.Write
          .to(props.getProperty("meanRespTimeTableName"))
          .withSchema(meanRespTimeTableSchema)
          .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
          .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED));

        PipelineResult r = p.run();

        LOG.info(r.toString());
    }
}
