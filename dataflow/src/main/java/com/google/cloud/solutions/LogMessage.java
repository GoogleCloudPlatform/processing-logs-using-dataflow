package com.google.cloud.solutions;

import com.google.cloud.dataflow.sdk.coders.AvroCoder;
import com.google.cloud.dataflow.sdk.coders.DefaultCoder;
import org.apache.avro.reflect.Nullable;
import org.joda.time.Instant;

@DefaultCoder(AvroCoder.class)
class LogMessage {
    @Nullable private Instant timestamp;
    @Nullable private int httpStatusCode;
    @Nullable private double responseTime;
    @Nullable private String source;
    @Nullable private String httpMethod;
    @Nullable private String destination;

    public LogMessage() {}

    public LogMessage(Instant timestamp, int httpStatusCode, double responseTime,
                      String source, String httpMethod, String destination) {
        this.timestamp = timestamp;
        this.httpStatusCode = httpStatusCode;
        this.responseTime = responseTime;
        this.source = source;
        this.httpMethod = httpMethod;
        this.destination = destination;
    }

    public Instant getTimestamp() {
        return this.timestamp;
    }

    public int getHttpStatusCode() {
        return this.httpStatusCode;
    }

    public double getResponseTime() {
        return this.responseTime;
    }

    public String getSource() {
        return this.source;
    }

    public String getHttpMethod() {
        return this.httpMethod;
    }

    public String getDestination() {
        return this.destination;
    }
}
