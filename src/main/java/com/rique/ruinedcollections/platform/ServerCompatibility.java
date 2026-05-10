package com.rique.ruinedcollections.platform;

import com.rique.ruinedcollections.diagnostics.DiagnosticService;
import org.bukkit.Bukkit;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerCompatibility {
    public static final String SUPPORTED_RANGE = "1.21 - 26.1.2";

    private static final MinecraftVersion MINIMUM = new MinecraftVersion(1, 21, 0);
    private static final MinecraftVersion MAXIMUM = new MinecraftVersion(26, 1, 2);
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[-+].*)?$");

    private final String serverName;
    private final String minecraftVersion;
    private final String bukkitVersion;
    private final MinecraftVersion parsedVersion;
    private final boolean supportedPlatform;
    private final CompatibilityStatus status;

    private ServerCompatibility(
            String serverName,
            String minecraftVersion,
            String bukkitVersion,
            MinecraftVersion parsedVersion,
            boolean supportedPlatform,
            CompatibilityStatus status
    ) {
        this.serverName = serverName;
        this.minecraftVersion = minecraftVersion;
        this.bukkitVersion = bukkitVersion;
        this.parsedVersion = parsedVersion;
        this.supportedPlatform = supportedPlatform;
        this.status = status;
    }

    public static ServerCompatibility inspect() {
        String serverName = Bukkit.getName();
        String minecraftVersion = Bukkit.getMinecraftVersion();
        String bukkitVersion = Bukkit.getBukkitVersion();
        MinecraftVersion parsedVersion = MinecraftVersion.parse(minecraftVersion);
        boolean supportedPlatform = isPaperOrFolia(serverName);
        CompatibilityStatus status = CompatibilityStatus.from(parsedVersion);
        return new ServerCompatibility(serverName, minecraftVersion, bukkitVersion, parsedVersion, supportedPlatform, status);
    }

    public void log(DiagnosticService diagnostics) {
        if (status == CompatibilityStatus.SUPPORTED && supportedPlatform) {
            diagnostics.info("compatibility", "Server version is supported", DiagnosticService.fields(
                    "server", serverName,
                    "minecraft", minecraftVersion,
                    "bukkit", bukkitVersion,
                    "range", SUPPORTED_RANGE
            ));
            return;
        }
        diagnostics.warn("compatibility", status.message(supportedPlatform), DiagnosticService.fields(
                "server", serverName,
                "minecraft", minecraftVersion,
                "bukkit", bukkitVersion,
                "parsed", parsedVersion == null ? "unknown" : parsedVersion.toString(),
                "range", SUPPORTED_RANGE,
                "platformSupported", supportedPlatform
        ));
    }

    public boolean blocksStartup() {
        return status == CompatibilityStatus.BELOW_MINIMUM || status == CompatibilityStatus.UNKNOWN_VERSION;
    }

    public String serverName() {
        return serverName;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public String bukkitVersion() {
        return bukkitVersion;
    }

    public String statusLabel() {
        if (!supportedPlatform) {
            return "unsupported platform";
        }
        return status.label();
    }

    private static boolean isPaperOrFolia(String serverName) {
        String lower = serverName == null ? "" : serverName.toLowerCase(Locale.ROOT);
        return "paper".equals(lower) || "folia".equals(lower);
    }

    private enum CompatibilityStatus {
        SUPPORTED("supported"),
        BELOW_MINIMUM("below supported range"),
        ABOVE_MAXIMUM("newer than validated range"),
        UNKNOWN_VERSION("unknown version");

        private final String label;

        CompatibilityStatus(String label) {
            this.label = label;
        }

        private static CompatibilityStatus from(MinecraftVersion version) {
            if (version == null) {
                return UNKNOWN_VERSION;
            }
            if (version.compareTo(MINIMUM) < 0) {
                return BELOW_MINIMUM;
            }
            if (version.compareTo(MAXIMUM) > 0) {
                return ABOVE_MAXIMUM;
            }
            return SUPPORTED;
        }

        private String label() {
            return label;
        }

        private String message(boolean supportedPlatform) {
            if (!supportedPlatform) {
                return "Server platform is not Paper or Folia";
            }
            return switch (this) {
                case BELOW_MINIMUM -> "Server version is below the supported range";
                case ABOVE_MAXIMUM -> "Server version is newer than the validated support range";
                case UNKNOWN_VERSION -> "Could not read the Minecraft server version";
                case SUPPORTED -> "Server version is supported";
            };
        }
    }

    private record MinecraftVersion(int major, int minor, int patch) implements Comparable<MinecraftVersion> {
        private static MinecraftVersion parse(String value) {
            if (value == null) {
                return null;
            }
            Matcher matcher = VERSION_PATTERN.matcher(value.trim());
            if (!matcher.matches()) {
                return null;
            }
            return new MinecraftVersion(
                    parsePart(matcher.group(1)),
                    parsePart(matcher.group(2)),
                    parsePart(matcher.group(3))
            );
        }

        private static int parsePart(String value) {
            return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
        }

        @Override
        public int compareTo(MinecraftVersion other) {
            int majorCompare = Integer.compare(major, other.major);
            if (majorCompare != 0) {
                return majorCompare;
            }
            int minorCompare = Integer.compare(minor, other.minor);
            if (minorCompare != 0) {
                return minorCompare;
            }
            return Integer.compare(patch, other.patch);
        }

        @Override
        public String toString() {
            if (patch == 0) {
                return major + "." + minor;
            }
            return major + "." + minor + "." + patch;
        }
    }
}
