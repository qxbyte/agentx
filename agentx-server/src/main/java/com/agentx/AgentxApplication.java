package com.agentx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.agentx")
public class AgentxApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentxApplication.class, args);
    }
}
