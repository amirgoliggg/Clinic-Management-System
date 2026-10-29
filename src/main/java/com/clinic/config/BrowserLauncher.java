package com.clinic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

@Component
@ConditionalOnProperty(name = "clinic.browser.auto-open", havingValue = "true", matchIfMissing = false)
public class BrowserLauncher {

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void launchBrowser() {
        System.setProperty("java.awt.headless", "false");
        String protocol = sslEnabled ? "https" : "http";
        String url = protocol + "://localhost:" + serverPort;
        String os = System.getProperty("os.name", "").toLowerCase();

        if (tryLaunchAppModeWindow(os, url)) {
            return;
        }
        if (tryLaunchAwtDesktop(url)) {
            return;
        }
        tryLaunchOsDefaultBrowser(os, url);
    }

    private boolean tryLaunchAppModeWindow(String os, String url) {
        String appArg = "--app=" + url;
        String sizeArg = "--window-size=1440,920";
        String ignoreCertArg = "--ignore-certificate-errors";

        if (os.contains("win")) {
            return executeCommand("cmd", "/c", "start", "msedge", appArg, sizeArg, ignoreCertArg)
                    || executeCommand("cmd", "/c", "start", "chrome", appArg, sizeArg, ignoreCertArg);
        }
        if (os.contains("mac")) {
            return executeCommand("open", "-na", "Google Chrome", "--args", appArg, sizeArg, ignoreCertArg);
        }
        if (os.contains("nix") || os.contains("nux")) {
            return executeCommand("google-chrome", appArg, sizeArg, ignoreCertArg);
        }
        return false;
    }

    private boolean tryLaunchAwtDesktop(String url) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(URI.create(url));
                return true;
            } catch (IOException ignored) {
                return false;
            }
        }
        return false;
    }

    private void tryLaunchOsDefaultBrowser(String os, String url) {
        if (os.contains("win")) {
            executeCommand("rundll32", "url.dll,FileProtocolHandler", url);
        } else if (os.contains("mac")) {
            executeCommand("open", url);
        } else if (os.contains("nix") || os.contains("nux")) {
            executeCommand("xdg-open", url);
        }
    }

    private boolean executeCommand(String... command) {
        try {
            new ProcessBuilder(command).start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}