package com.example.focusquest.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.List;

/**
 * Serves the built web app (frontend/dist, packaged into classpath:/static/ by the Gradle build) from the
 * backend's own origin. A route that is not one of its files gets index.html, so the web app's router can
 * show that route after a reload or from a bookmark. Without a built web app (a plain test run) nothing is
 * served here and every such path is a 404.
 */
@Configuration
public class WebAppConfig implements WebMvcConfigurer {

    /** Paths that belong to the backend, never to the web app: they are not served index.html. */
    private static final List<String> BACKEND_PATHS = List.of("/api", "/h2-console", "/actuator", "/error");

    /**
     * A GET for one of the web app's files or routes. These hold no data, so security lets them through
     * without a token; everything else, unknown paths outside the web app included, still needs one.
     */
    static boolean isWebAppRequest(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) return false;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !isBackendPath(path);
    }

    private static boolean isBackendPath(String path) {
        String normalized = path.startsWith("/") ? path : "/" + path;
        return BACKEND_PATHS.stream().anyMatch(prefix -> normalized.equals(prefix) || normalized.startsWith(prefix + "/"));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource file = location.createRelative(resourcePath);
                        if (file.exists() && file.isReadable()) return file;
                        // A missing file (an old asset name, say) is a 404, not the page.
                        if (isBackendPath(resourcePath) || resourcePath.substring(resourcePath.lastIndexOf('/') + 1).contains(".")) {
                            return null;
                        }
                        Resource index = location.createRelative("index.html");
                        return index.exists() && index.isReadable() ? index : null;
                    }
                });
    }
}
