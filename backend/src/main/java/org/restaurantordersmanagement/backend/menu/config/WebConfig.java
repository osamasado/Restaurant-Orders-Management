package org.restaurantordersmanagement.backend.menu.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves stored meal/raw-material images back over HTTP. A path persisted as
 * "meals/&lt;uuid&gt;.jpg" (see ImageStorageService) becomes reachable at
 * "/images/meals/&lt;uuid&gt;.jpg".
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public WebConfig(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/images/**")
                .addResourceLocations(location);
    }

}
