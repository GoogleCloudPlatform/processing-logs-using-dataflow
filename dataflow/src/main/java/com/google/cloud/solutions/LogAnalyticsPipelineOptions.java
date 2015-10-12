package com.google.cloud.solutions;

import com.google.cloud.dataflow.sdk.options.DataflowPipelineOptions;
import com.google.cloud.dataflow.sdk.options.Description;

@SuppressWarnings("unused")
public interface LogAnalyticsPipelineOptions extends DataflowPipelineOptions {
    @Description("Location of /home logs")
    String getHomeLogSource();
    void setHomeLogSource(String homeLogSource);

    @Description("Location of /browse logs")
    String getBrowseLogSource();
    void setBrowseLogSource(String browseLogSource);

    @Description("Location of /locate logs")
    String getLocateLogSource();
    void setLocateLogSource(String locateLogSource);

    @Description("Pipeline configuration file")
    String getPipelineConfigFile();
    void setPipelineConfigFile(String pipelineConfigFile);
}
