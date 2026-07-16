package com.example.ipv6lanapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileLogger {
    private static final Path LOG_DIR = Paths.get("logs");
    private static final Path LOG_FILE = LOG_DIR.resolve("ipv6lanapi.log");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static synchronized void write(String level, String message) {
        try {
            Files.createDirectories(LOG_DIR);
            String timestamp = LocalDateTime.now().format(FORMATTER);
            String line = String.format("[%s] [%s] %s%n", timestamp, level, message);
            Files.writeString(LOG_FILE, line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Silently ignore file logging errors
        }
    }

    public static void info(String message) {
        write("INFO", message);
    }

    public static void warn(String message) {
        write("WARN", message);
    }

    public static void error(String message) {
        write("ERROR", message);
    }

    public static void error(String message, Throwable t) {
        write("ERROR", message + ": " + t.getMessage());
    }
}