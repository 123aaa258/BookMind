package io.bookmind.spi;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantic SPI compatibility checker (major.minor).
 *
 * <p>Compatibility rule: a plugin is compatible with the host if and only if
 * {@code plugin.major == host.major && plugin.minor <= host.minor}.
 */
public final class SpiVersion {

    public static final String CURRENT_API_VERSION = "1.0";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)$");

    private final int major;
    private final int minor;

    private SpiVersion(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    public static SpiVersion parse(String versionText) {
        Objects.requireNonNull(versionText, "versionText must not be null");
        Matcher matcher = VERSION_PATTERN.matcher(versionText.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid SPI API version (expected major.minor): " + versionText);
        }
        return new SpiVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2))
        );
    }

    public static boolean isCompatibleWithHost(String pluginApiVersion, String hostApiVersion) {
        SpiVersion plugin = parse(pluginApiVersion);
        SpiVersion host = parse(hostApiVersion);
        return plugin.major == host.major && plugin.minor <= host.minor;
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
