package net.vanbaelinghem.skylanders.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

    /**
     * Serves the application shell on the detached-portal URL.
     *
     * <p>The frontend has no router — it picks its root component from {@code location.pathname}
     * — so {@code /portail} has to reach {@code index.html} instead of the static handler's 404.
     * Vite's dev server does this on its own; the packaged JAR does not.
     */
    @Bean
    public WebMvcConfigurer spaRoutes() {
        return new WebMvcConfigurer() {
            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addViewController("/portail").setViewName("forward:/index.html");
            }
        };
    }
}
