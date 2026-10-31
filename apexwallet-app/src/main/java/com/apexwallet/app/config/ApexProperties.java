package com.apexwallet.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "apex")
public class ApexProperties {

    private long approvalThresholdMinor = 100_000;
    private long dailyLimitMinor = 500_000;
    private Duration idempotencyTtl = Duration.ofHours(24);
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(7);
    private int loginMaxFailures = 5;
    private Duration loginLockout = Duration.ofMinutes(15);
    private int transfersPerMinute = 30;
    private List<String> corsAllowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));
    private String activeKeyId = "dev-1";
    private Map<String, String> dataKeys = new LinkedHashMap<>();
    private String hmacKey = "";
    private String modelUrl = "";
    private String modelToken = "";
    private String grafanaUrl = "";
    private String assistantUrl = "";
    private String kafkaBootstrapServers = "localhost:9092";
    private String kafkaTopic = "apexwallet.ledger";

    public long getApprovalThresholdMinor() {
        return approvalThresholdMinor;
    }

    public void setApprovalThresholdMinor(long approvalThresholdMinor) {
        this.approvalThresholdMinor = approvalThresholdMinor;
    }

    public long getDailyLimitMinor() {
        return dailyLimitMinor;
    }

    public void setDailyLimitMinor(long dailyLimitMinor) {
        this.dailyLimitMinor = dailyLimitMinor;
    }

    public Duration getIdempotencyTtl() {
        return idempotencyTtl;
    }

    public void setIdempotencyTtl(Duration idempotencyTtl) {
        this.idempotencyTtl = idempotencyTtl;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public int getLoginMaxFailures() {
        return loginMaxFailures;
    }

    public void setLoginMaxFailures(int loginMaxFailures) {
        this.loginMaxFailures = loginMaxFailures;
    }

    public Duration getLoginLockout() {
        return loginLockout;
    }

    public void setLoginLockout(Duration loginLockout) {
        this.loginLockout = loginLockout;
    }

    public int getTransfersPerMinute() {
        return transfersPerMinute;
    }

    public void setTransfersPerMinute(int transfersPerMinute) {
        this.transfersPerMinute = transfersPerMinute;
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public Map<String, String> getDataKeys() {
        return dataKeys;
    }

    public void setDataKeys(Map<String, String> dataKeys) {
        this.dataKeys = dataKeys;
    }

    public String getHmacKey() {
        return hmacKey;
    }

    public void setHmacKey(String hmacKey) {
        this.hmacKey = hmacKey;
    }

    public String getModelUrl() {
        return modelUrl;
    }

    public void setModelUrl(String modelUrl) {
        this.modelUrl = modelUrl;
    }

    public String getModelToken() {
        return modelToken;
    }

    public void setModelToken(String modelToken) {
        this.modelToken = modelToken;
    }

    public String getGrafanaUrl() {
        return grafanaUrl;
    }

    public void setGrafanaUrl(String grafanaUrl) {
        this.grafanaUrl = grafanaUrl;
    }

    public String getAssistantUrl() {
        return assistantUrl;
    }

    public void setAssistantUrl(String assistantUrl) {
        this.assistantUrl = assistantUrl;
    }

    public String getKafkaBootstrapServers() {
        return kafkaBootstrapServers;
    }

    public void setKafkaBootstrapServers(String kafkaBootstrapServers) {
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    public String getKafkaTopic() {
        return kafkaTopic;
    }

    public void setKafkaTopic(String kafkaTopic) {
        this.kafkaTopic = kafkaTopic;
    }
}
