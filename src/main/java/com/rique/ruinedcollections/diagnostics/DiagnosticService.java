package com.rique.ruinedcollections.diagnostics;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class DiagnosticService {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final JavaPlugin plugin;
    private final Object lock = new Object();

    private volatile boolean enabled;
    private volatile boolean mirrorWarningsToConsole;
    private volatile boolean includeStackTraces;
    private volatile boolean debugTrackingSkips;
    private volatile boolean debugProgress;
    private volatile boolean debugRewards;
    private volatile boolean debugCommands;
    private volatile long maxBytes;
    private volatile int maxArchives;
    private volatile File logFile;
    private BufferedWriter writer;

    public DiagnosticService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        synchronized (lock) {
            closeWriter();
            FileConfiguration config = plugin.getConfig();
            enabled = config.getBoolean("diagnostics.enabled", true);
            mirrorWarningsToConsole = config.getBoolean("diagnostics.mirror-warnings-to-console", true);
            includeStackTraces = config.getBoolean("diagnostics.include-stack-traces", true);
            debugTrackingSkips = config.getBoolean("diagnostics.debug.tracking-skips", false);
            debugProgress = config.getBoolean("diagnostics.debug.progress", false);
            debugRewards = config.getBoolean("diagnostics.debug.rewards", false);
            debugCommands = config.getBoolean("diagnostics.debug.commands", false);
            maxBytes = Math.max(1L, config.getLong("diagnostics.max-file-size-mb", 8L)) * BYTES_PER_MB;
            maxArchives = Math.max(0, config.getInt("diagnostics.max-archives", 5));
            logFile = resolveFile(config.getString("diagnostics.file", "logs/diagnostics.log"));
            if (!enabled) {
                return;
            }
            try {
                File parent = logFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Could not create diagnostics folder " + parent.getAbsolutePath());
                }
                ensureWriter();
            } catch (IOException exception) {
                enabled = false;
                plugin.getLogger().log(Level.SEVERE, "Could not open diagnostics log.", exception);
                return;
            }
        }
        info("diagnostics", "Diagnostics loaded", fields(
                "file", logPath(),
                "debugTracking", debugTrackingSkips,
                "debugProgress", debugProgress,
                "debugRewards", debugRewards,
                "debugCommands", debugCommands
        ));
    }

    public void close() {
        info("diagnostics", "Diagnostics closed");
        synchronized (lock) {
            closeWriter();
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean debugTrackingSkips() {
        return debugTrackingSkips;
    }

    public boolean debugProgress() {
        return debugProgress;
    }

    public boolean debugRewards() {
        return debugRewards;
    }

    public boolean debugCommands() {
        return debugCommands;
    }

    public String logPath() {
        return logFile == null ? "not configured" : logFile.getAbsolutePath();
    }

    public long logSizeBytes() {
        return logFile != null && logFile.exists() ? logFile.length() : 0L;
    }

    public void info(String category, String message) {
        write(Level.INFO, category, message, Map.of(), null);
    }

    public void info(String category, String message, Map<String, ?> context) {
        write(Level.INFO, category, message, context, null);
    }

    public void warn(String category, String message) {
        write(Level.WARNING, category, message, Map.of(), null);
    }

    public void warn(String category, String message, Map<String, ?> context) {
        write(Level.WARNING, category, message, context, null);
    }

    public void warn(String category, String message, Map<String, ?> context, Throwable throwable) {
        write(Level.WARNING, category, message, context, throwable);
    }

    public void error(String category, String message, Throwable throwable) {
        write(Level.SEVERE, category, message, Map.of(), throwable);
    }

    public void error(String category, String message, Map<String, ?> context, Throwable throwable) {
        write(Level.SEVERE, category, message, context, throwable);
    }

    public void debug(String category, String message, Map<String, ?> context) {
        if (!debugEnabled(category)) {
            return;
        }
        write(Level.FINE, category, message, context, null);
    }

    public List<String> tail(int lines) {
        if (logFile == null || !logFile.exists()) {
            return List.of("Diagnostics log does not exist yet: " + logPath());
        }
        int count = Math.max(1, Math.min(lines, 50));
        try {
            List<String> all = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
            int start = Math.max(0, all.size() - count);
            return new ArrayList<>(all.subList(start, all.size()));
        } catch (IOException exception) {
            return List.of("Could not read diagnostics log: " + exception.getMessage());
        }
    }

    public static Map<String, Object> fields(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private void write(Level level, String category, String message, Map<String, ?> context, Throwable throwable) {
        if (mirrorWarningsToConsole && level.intValue() >= Level.WARNING.intValue()) {
            plugin.getLogger().log(level, "[" + category + "] " + message, throwable);
        }
        if (!enabled) {
            return;
        }
        synchronized (lock) {
            try {
                rotateIfNeeded();
                ensureWriter();
                writer.write(formatLine(level, category, message, context, throwable));
                writer.newLine();
                if (throwable != null && includeStackTraces) {
                    writer.write(stackTrace(throwable));
                    writer.newLine();
                }
                if (level.intValue() >= Level.WARNING.intValue()) {
                    writer.flush();
                }
            } catch (IOException exception) {
                enabled = false;
                plugin.getLogger().log(Level.SEVERE, "Could not write diagnostics log.", exception);
            }
        }
    }

    private String formatLine(Level level, String category, String message, Map<String, ?> context, Throwable throwable) {
        StringBuilder builder = new StringBuilder()
                .append(TIMESTAMP.format(ZonedDateTime.now()))
                .append(" level=").append(levelName(level))
                .append(" category=").append(clean(category))
                .append(" message=").append(quote(message));
        for (Map.Entry<String, ?> entry : context.entrySet()) {
            builder.append(' ')
                    .append(clean(entry.getKey()))
                    .append('=')
                    .append(quote(String.valueOf(entry.getValue())));
        }
        if (throwable != null) {
            builder.append(" throwable=").append(quote(throwable.getClass().getName()))
                    .append(" throwableMessage=").append(quote(throwable.getMessage()));
        }
        return builder.toString();
    }

    private void ensureWriter() throws IOException {
        if (writer != null) {
            return;
        }
        writer = Files.newBufferedWriter(logFile.toPath(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void rotateIfNeeded() throws IOException {
        if (logFile == null || !logFile.exists() || logFile.length() < maxBytes) {
            return;
        }
        closeWriter();
        if (maxArchives <= 0) {
            Files.deleteIfExists(logFile.toPath());
            return;
        }
        for (int index = maxArchives - 1; index >= 1; index--) {
            File source = archive(index);
            if (source.exists()) {
                Files.move(source.toPath(), archive(index + 1).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.move(logFile.toPath(), archive(1).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private File archive(int index) {
        return new File(logFile.getParentFile(), logFile.getName() + "." + index);
    }

    private File resolveFile(String value) {
        File file = new File(value == null || value.isBlank() ? "logs/diagnostics.log" : value);
        return file.isAbsolute() ? file : new File(plugin.getDataFolder(), file.getPath());
    }

    private void closeWriter() {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
            writer.close();
        } catch (IOException ignored) {
        } finally {
            writer = null;
        }
    }

    private boolean debugEnabled(String category) {
        return switch (category.toLowerCase()) {
            case "tracking" -> debugTrackingSkips;
            case "progress" -> debugProgress;
            case "rewards" -> debugRewards;
            case "commands" -> debugCommands;
            default -> false;
        };
    }

    private String levelName(Level level) {
        if (level == Level.SEVERE) {
            return "ERROR";
        }
        if (level == Level.WARNING) {
            return "WARN";
        }
        if (level == Level.FINE) {
            return "DEBUG";
        }
        return "INFO";
    }

    private String clean(String value) {
        String safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return safe.isBlank() ? "unknown" : safe;
    }

    private String quote(String value) {
        String safe = value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return '"' + safe + '"';
    }

    private String stackTrace(Throwable throwable) {
        StringWriter output = new StringWriter();
        throwable.printStackTrace(new PrintWriter(output));
        return output.toString();
    }
}
