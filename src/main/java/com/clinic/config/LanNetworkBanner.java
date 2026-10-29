package com.clinic.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@Component
public class LanNetworkBanner {

    private static final Logger log = LoggerFactory.getLogger(LanNetworkBanner.class);

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void printLanEndpoints() {
        String protocol = sslEnabled ? "https" : "http";
        List<String> lanUrls = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        lanUrls.add(String.format("%s://%s:%d  (%s)",
                                protocol, addr.getHostAddress(), serverPort, iface.getDisplayName()));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        log.info("=================================================================");
        log.info("  CLINIC INTERNAL LAN/WLAN SERVER IS ACTIVE AND READY");
        log.info("  Local Server Access : {}://localhost:{}", protocol, serverPort);
        for (String url : lanUrls) {
            log.info("  Workstation LAN URL : {}", url);
        }
        log.info("=================================================================");
    }
}