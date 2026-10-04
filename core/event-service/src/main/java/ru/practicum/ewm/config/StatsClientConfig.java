package ru.practicum.ewm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.practicum.stats.client.AnalyzerClient;
import ru.practicum.stats.client.CollectorClient;

@Configuration
@Import({CollectorClient.class, AnalyzerClient.class})
public class StatsClientConfig {
}
