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

import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod(value = "ipv6lanapi", dist = Dist.CLIENT)
public class IPv6LanApiMod {
    public static final Logger LOGGER = LoggerFactory.getLogger("IPv6LanApi");

    private static WebApiServer webServer;
    private static IPv6Fetcher ipv6Fetcher;
    private static ScheduledExecutorService lanDetector;
    private static volatile boolean servicesRunning = false;

    public IPv6LanApiMod(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, IPv6LanApiConfig.CONFIG_SPEC);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("Server started, waiting for LAN to be opened...");
        FileLogger.info("Server started, waiting for LAN to be opened...");

        servicesRunning = false;
        lanDetector = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IPv6-LAN-Detector");
            t.setDaemon(true);
            return t;
        });

        // Polling schedule: 15, 20, 25, 30, 40 seconds, then restart after 5 min
        int[] waitSeconds = {15, 20, 25, 30, 40};
        long cumulativeDelay = 0;

        for (int i = 0; i < waitSeconds.length; i++) {
            cumulativeDelay += waitSeconds[i];
            final int attempt = i + 1;
            final long delay = cumulativeDelay;
            lanDetector.schedule(() -> checkLanStatus(event, attempt), delay, TimeUnit.SECONDS);
        }

        // After all attempts fail, wait 5 minutes and restart the cycle
        cumulativeDelay += 300;
        lanDetector.schedule(() -> restartCycle(event), cumulativeDelay, TimeUnit.SECONDS);
    }

    private void checkLanStatus(ServerStartedEvent event, int attempt) {
        if (servicesRunning) return;

        try {
            Class<?> integratedServerClass = Class.forName("net.minecraft.client.server.IntegratedServer");

            if (integratedServerClass.isInstance(event.getServer())) {
                Object server = event.getServer();

                // Try isPublished() method
                Method isPublished = integratedServerClass.getDeclaredMethod("isPublished");
                isPublished.setAccessible(true);
                boolean published = (boolean) isPublished.invoke(server);

                if (published) {
                    // Get the port
                    Method getPort = server.getClass().getMethod("getPort");
                    getPort.setAccessible(true);
                    int port = (int) getPort.invoke(server);

                    LOGGER.info("LAN world detected on attempt {} (port {})", attempt, port);
                    FileLogger.info("LAN world detected on attempt " + attempt + " (port " + port + ")");
                    startServices(port);
                } else {
                    LOGGER.info("Attempt {}: LAN not yet opened", attempt);
                    FileLogger.info("Attempt " + attempt + ": LAN not yet opened");
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Attempt {}: Failed to detect LAN server: {}", attempt, e.getMessage());
            FileLogger.warn("Attempt " + attempt + ": Failed to detect LAN server: " + e.getMessage());
        }
    }

    private void restartCycle(ServerStartedEvent event) {
        if (servicesRunning) return;

        LOGGER.info("Restarting LAN detection cycle...");
        FileLogger.info("Restarting LAN detection cycle...");

        int[] waitSeconds = {15, 20, 25, 30, 40};
        long cumulativeDelay = 0;

        for (int i = 0; i < waitSeconds.length; i++) {
            cumulativeDelay += waitSeconds[i];
            final int attempt = i + 1;
            final long delay = cumulativeDelay;
            lanDetector.schedule(() -> checkLanStatus(event, attempt), delay, TimeUnit.SECONDS);
        }

        cumulativeDelay += 300;
        lanDetector.schedule(() -> restartCycle(event), cumulativeDelay, TimeUnit.SECONDS);
    }

    private void startServices(int lanPort) {
        servicesRunning = true;
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
        servicesRunning = false;
        if (lanDetector != null) {
            lanDetector.shutdownNow();
            lanDetector = null;
        }
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