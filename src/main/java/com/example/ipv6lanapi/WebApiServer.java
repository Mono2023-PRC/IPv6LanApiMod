package com.example.ipv6lanapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class WebApiServer {
    private static final int PORT = 24016;
    private HttpServer server;

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/", new IpHandler());
            server.createContext("/ip", new IpHandler());
            server.setExecutor(null);
            server.start();
            IPv6LanApiMod.LOGGER.info("Web API server started on http://0.0.0.0:{}", PORT);
        } catch (IOException e) {
            IPv6LanApiMod.LOGGER.error("Failed to start Web API server on port {}", PORT, e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            IPv6LanApiMod.LOGGER.info("Web API server stopped");
        }
    }

    static class IpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String ip = IPv6Fetcher.getCurrentIp();
            String response = String.format("{\"ip\":\"%s\"}", ip);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}