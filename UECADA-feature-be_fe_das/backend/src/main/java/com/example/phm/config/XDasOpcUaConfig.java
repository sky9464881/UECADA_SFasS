package com.example.phm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(XDasOpcUaProperties.class)
public class XDasOpcUaConfig {
}
