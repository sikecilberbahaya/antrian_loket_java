package com.panggilan.loket;

import com.panggilan.loket.config.CounterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CounterProperties.class)
public class PanggilanLoketApplication {

    public static void main(String[] args) {
        SpringApplication.run(PanggilanLoketApplication.class, args);
    }
}
