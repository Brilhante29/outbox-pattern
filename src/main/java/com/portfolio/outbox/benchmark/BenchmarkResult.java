package com.portfolio.outbox.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

public class BenchmarkResult {
    private String project;
    private String metric;
    private double value;
    private String unit;
    private Instant timestamp;
    private Map<String, Object> environment;
    private Map<String, Object> parameters;
    private String command;

    public BenchmarkResult() {}

    @JsonProperty("project")
    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }

    @JsonProperty("metric")
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    @JsonProperty("value")
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    @JsonProperty("unit")
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    @JsonProperty("timestamp")
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    @JsonProperty("environment")
    public Map<String, Object> getEnvironment() { return environment; }
    public void setEnvironment(Map<String, Object> environment) { this.environment = environment; }

    @JsonProperty("parameters")
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    @JsonProperty("command")
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
}
