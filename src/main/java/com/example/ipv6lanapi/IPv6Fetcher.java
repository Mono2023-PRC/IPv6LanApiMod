package com.example.ipv6lanapi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class IPv6Fetcher {
    private static final String[] API_URLS = {
        "https://v6.yinghualuo.cn/bejson",
        "https://ifconfig.co",
        "https://api6.ipify.org?format=json",
        "https://ipv6.ip.sb"
    };

    private static volatile String currentIp = "";
    private ScheduledExecutorService scheduler;

    public void start() {
        fetchIp();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IPv6-Fetcher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::fetchIp, 30, 30, TimeUnit.MINUTES);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public static String getCurrentIp() {
        return currentIp;
    }

    private void fetchIp() {
        for (String apiUrl : API_URLS) {
            try {
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (IPv6-LAN-API)");
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code == 200) {
                    try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("ip")) {
                            String ip = json.get("ip").getAsString();
                            if (isValidIPv6(ip)) {
                                currentIp = ip;
                                IPv6LanApiMod.LOGGER.info("IPv6 address updated via {}: {}", apiUrl, ip);
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                IPv6LanApiMod.LOGGER.warn("IPv6 API failed {}: {}", apiUrl, e.getMessage());
            }
        }
        IPv6LanApiMod.LOGGER.warn("All IPv6 APIs failed. Keeping current IP: {}", currentIp);
    }

    private boolean isValidIPv6(String ip) {
        return ip != null && !ip.isEmpty() && ip.contains(":");
    }
}