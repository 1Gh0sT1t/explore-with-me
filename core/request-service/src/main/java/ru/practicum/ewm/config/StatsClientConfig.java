package ru.practicum.ewm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.practicum.stats.client.CollectorClient;

@Configuration
@EnableScheduling
@Import(CollectorClient.class)
public class StatsClientConfig {
}
