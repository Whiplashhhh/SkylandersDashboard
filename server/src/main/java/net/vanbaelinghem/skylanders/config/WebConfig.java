package net.vanbaelinghem.skylanders.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<IngestTokenFilter> ingestTokenFilter(
            @Value("${skylanders.ingest.token}") String token) {
        FilterRegistrationBean<IngestTokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new IngestTokenFilter(token));
        // Couvre /api/ingest et le cycle de vie des scans, qui sont tous deux des ecritures
        // venues de l'agent. La lecture /api/scans reste libre pour l'UI.
        registration.addUrlPatterns("/api/ingest", "/api/ingest/*");
        registration.setName("ingestTokenFilter");
        return registration;
    }
}
