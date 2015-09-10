package com.google.cloud.solutions;

import org.joda.time.Instant;

/**
 * Created by parikhs on 9/10/15.
 */
class LogEntry {
    private Instant timestamp;
    private int httpStatusCode;
    private double responseTime;
    private String source;
    private String httpMethod;
    private String destination;

    public LogEntry(Instant timestamp, int httpStatusCode, double responseTime,
                    String source, String httpMethod, String destination) {
        this.timestamp = timestamp;
        this.httpStatusCode = httpStatusCode;
        this.responseTime = responseTime;
        this.source = source;
        this.httpMethod = httpMethod;
        this.destination = destination;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public double getResponseTime() {
        return responseTime;
    }

    public String getSource() {
        return source;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getDestination() {
        return destination;
    }
}
