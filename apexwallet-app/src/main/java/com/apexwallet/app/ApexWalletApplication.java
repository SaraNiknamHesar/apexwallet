package com.apexwallet.app;

import com.apexwallet.app.config.ApexProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ApexProperties.class)
public class ApexWalletApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApexWalletApplication.class, args);
    }
}
