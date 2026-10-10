package org.restaurantordersmanagement.backend.web;

/**
 * The paths of the React app when the backend serves it itself (the single-image deployment, see SpaConfig). The app
 * shell holds no data, so these are public; every API path stays behind the rules in SecurityConfig.
 */
public final class SpaPaths {

    /** The screens of the single-page app: a direct visit or a reload gets index.html and the router takes over. */
    public static final String[] SCREENS = {"/guest", "/kitchen", "/hall", "/admin", "/admin/**"};

    /** Files from the frontend build (the version file is added by the image build). */
    public static final String[] FILES = {
        "/", "/index.html", "/version.txt", "/sw.js", "/manifest.webmanifest", "/favicon.svg", "/assets/**", "/icons/**"
    };

    private SpaPaths() {
    }

}
