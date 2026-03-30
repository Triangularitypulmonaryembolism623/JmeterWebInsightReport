package com.jmeterwebinsightreport.core.model;

/**
 * Aggregated error summary — groups errors by type/code with occurrence counts.
 */
public class ErrorSummary {

    private String samplerName;
    private String errorType;
    private String responseCode;
    private String responseMessage;
    private long occurrenceCount;
    private double percentageOfErrors;
    private double percentageOfAllSamples;

    public ErrorSummary() {
    }

    public String getSamplerName() {
        return samplerName;
    }

    public void setSamplerName(String samplerName) {
        this.samplerName = samplerName;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }

    public long getOccurrenceCount() {
        return occurrenceCount;
    }

    public void setOccurrenceCount(long occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }

    public double getPercentageOfErrors() {
        return percentageOfErrors;
    }

    public void setPercentageOfErrors(double percentageOfErrors) {
        this.percentageOfErrors = percentageOfErrors;
    }

    public double getPercentageOfAllSamples() {
        return percentageOfAllSamples;
    }

    public void setPercentageOfAllSamples(double percentageOfAllSamples) {
        this.percentageOfAllSamples = percentageOfAllSamples;
    }
}
