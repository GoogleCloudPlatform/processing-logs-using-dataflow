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
