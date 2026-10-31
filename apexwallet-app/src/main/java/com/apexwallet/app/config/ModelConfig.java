package com.apexwallet.app.config;

import com.apexwallet.ai.ModelPort;
import com.apexwallet.app.ai.HttpModelClient;
import com.apexwallet.app.workspace.KeywordModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelConfig {

    @Bean
    @ConditionalOnProperty(prefix = "apex", name = "model-url")
    ModelPort httpModel(ApexProperties properties) {
        return new HttpModelClient(properties.getModelUrl(), properties.getModelToken());
    }

    @Bean
    @ConditionalOnMissingBean(ModelPort.class)
    ModelPort keywordModel() {
        return new KeywordModel();
    }
}
