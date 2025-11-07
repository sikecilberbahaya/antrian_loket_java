package com.panggilan.loket.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queue")
public class CounterProperties {

    private List<CounterDefinition> counters = new ArrayList<>();

    public List<CounterDefinition> getCounters() {
        return counters;
    }

    public void setCounters(List<CounterDefinition> counters) {
        this.counters = counters;
    }

    public static class CounterDefinition {
        private String id;
        private String name;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
