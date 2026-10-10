package org.restaurantordersmanagement.backend.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the React app from the backend, for the single-image deployment (Dockerfile.combined) where the frontend build
 * is copied into the jar's static folder. Only active when that build is there ({@code static/index.html}), so the
 * normal backend image and the tests are not affected. It does what the frontend image's nginx does: every screen path
 * answers with index.html, hashed assets are cached for a year, and the pages that must always be revalidated (so an
 * update reaches installed apps) are not cached. The condition's location is a property (app.spa.index) only so a test can
 * point it at a small fake build.
 */
@Configuration
@ConditionalOnResource(resources = "${app.spa.index:classpath:/static/index.html}")
public class SpaConfig implements WebMvcConfigurer {

    private static final Set<String> NEVER_CACHED = Set.of(
            "/", "/index.html", "/version.txt", "/sw.js", "/manifest.webmanifest",
            "/guest", "/kitchen", "/hall", "/admin");

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String screen : SpaPaths.SCREENS) {
            registry.addViewController(screen).setViewName("forward:/index.html");
        }
    }

    /** Java's media type list does not know the web app manifest; installability wants application/manifest+json. */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webManifestMediaType() {
        return factory -> {
            MimeMappings mappings = new MimeMappings();
            mappings.add("webmanifest", "application/manifest+json");
            factory.addMimeMappings(mappings);
        };
    }

    @Bean
    public OncePerRequestFilter staticCacheHeadersFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                String path = request.getRequestURI();
                if (path.startsWith("/assets/")) {
                    response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
                } else if (NEVER_CACHED.contains(path) || path.startsWith("/admin/")) {
                    response.setHeader("Cache-Control", "no-cache");
                }
                chain.doFilter(request, response);
            }
        };
    }

}
