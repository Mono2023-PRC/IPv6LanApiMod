package com.example.ipv6lanapi;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

@Mod(value = "ipv6lanapi", dist = Dist.CLIENT)
public class IPv6LanApiMod {
    public static final Logger LOGGER = LoggerFactory.getLogger("IPv6LanApi");

    private static WebApiServer webServer;
    private static IPv6Fetcher ipv6Fetcher;

    public IPv6LanApiMod(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, IPv6LanApiConfig.CONFIG_SPEC);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("Server started, checking if it's a LAN server...");
        FileLogger.info("Server started, checking if it's a LAN server...");

        try {
            Class<?> integratedServerClass = Class.forName("net.minecraft.server.integrated.IntegratedServer");

            if (integratedServerClass.isInstance(event.getServer())) {
                Field lanPortField = integratedServerClass.getDeclaredField("lanPort");
                lanPortField.setAccessible(true);
                int port = lanPortField.getInt(event.getServer());

                LOGGER.info("LAN world detected on port {}", port);
                FileLogger.info("LAN world detected on port " + port);
                startServices(port);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to detect LAN server: {}", e.getMessage());
            FileLogger.warn("Failed to detect LAN server: " + e.getMessage());
        }
    }

    private void startServices(int lanPort) {
        LOGGER.info("LAN world opened on port {}, starting IPv6 API service...", lanPort);
        FileLogger.info("LAN world opened on port " + lanPort + ", starting IPv6 API service...");

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
        FileLogger.info("Server stopped, shutting down IPv6 API service...");
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