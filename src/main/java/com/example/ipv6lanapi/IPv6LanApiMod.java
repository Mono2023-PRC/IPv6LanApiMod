package com.example.ipv6lanapi;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = "ipv6lanapi", dist = Dist.CLIENT)
public class IPv6LanApiMod {
    public static final Logger LOGGER = LoggerFactory.getLogger("IPv6LanApi");

    private static WebApiServer webServer;
    private static IPv6Fetcher ipv6Fetcher;

    public IPv6LanApiMod() {
        NeoForge.EVENT_BUS.register(this);
    }

    public static void onLanOpened(int port) {
        LOGGER.info("LAN world opened on port {}, starting IPv6 API service...", port);

        if (webServer == null) {
            webServer = new WebApiServer();
            webServer.start();
        }
        if (ipv6Fetcher == null) {
            ipv6Fetcher = new IPv6Fetcher();
            ipv6Fetcher.start();
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        LOGGER.info("Server stopped, shutting down IPv6 API service...");
        shutdown();
    }

    public static void shutdown() {
        if (ipv6Fetcher != null) {
            ipv6Fetcher.stop();
            ipv6Fetcher = null;
        }
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
    }
}