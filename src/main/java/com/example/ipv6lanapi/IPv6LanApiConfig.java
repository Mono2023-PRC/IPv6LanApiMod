package com.example.ipv6lanapi;

import net.neoforged.neoforge.common.ModConfigSpec;

public class IPv6LanApiConfig {
    public static final ModConfigSpec CONFIG_SPEC;
    public static final IPv6LanApiConfig CONFIG;

    public final ModConfigSpec.IntValue webApiPort;
    public final ModConfigSpec.IntValue fetchIntervalMinutes;
    public final ModConfigSpec.ConfigValue<String> pingUrl;

    static {
        var pair = new ModConfigSpec.Builder()
                .configure(IPv6LanApiConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    private IPv6LanApiConfig(ModConfigSpec.Builder builder) {
        builder.comment("IPv6 LAN API Configuration")
                .push("server");

        webApiPort = builder
                .comment("Port for the Web API server")
                .defineInRange("web_api_port", 24016, 1, 65535);

        builder.pop()
                .push("fetcher");

        fetchIntervalMinutes = builder
                .comment("Interval in minutes to refresh IPv6 address")
                .defineInRange("fetch_interval_minutes", 30, 1, 1440);

        builder.pop()
                .push("network");

        pingUrl = builder
                .comment("URL to ping for network connectivity check (e.g., 192.168.3.22:24017/ping)")
                .define("ping_url", "http://192.168.3.22:24017/ping");

        builder.pop();
    }
}