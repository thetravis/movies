package com.example.movies.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the OpenAPI contract file as-is at {@code /openapi.yaml}.
 * <p>
 * Swagger UI is configured (see {@code springdoc.swagger-ui.url} in application.yml) to display this
 * file, so the documentation always shows the contract itself rather than a spec reconstructed
 * from the code.
 */
@Configuration
public class OpenApiContractConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/openapi.yaml")
                .addResourceLocations("classpath:/openapi/");
    }
}
