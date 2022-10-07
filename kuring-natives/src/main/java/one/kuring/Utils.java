package one.kuring;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Objects;

class Utils {

    private static final String NORMALIZED_ARCH = normalizeArch(System.getProperty("os.arch", ""));
    private static final String NORMALIZED_OS = normalizeOs(System.getProperty("os.name", ""));
    private static final String BASE_NAME = "libs/";

    public static void loadLibrary(ClassLoader classLoader, String libName) {
        String libFileName = libFilename(libName);
        try {
            System.loadLibrary(libFileName);
        } catch (UnsatisfiedLinkError ex) {
            String name = BASE_NAME + NORMALIZED_OS + "-" + NORMALIZED_ARCH + "/" + libFileName;
            try {
                final URL url;
                if (classLoader != null) {
                    url = classLoader.getResource(name);
                } else {
                    url = ClassLoader.getSystemResource(name);
                }
                File file = Files.createTempFile("jni", libFilename(nameOnly(libName))).toFile();
                file.deleteOnExit();
                file.delete();
                try (InputStream in = Objects.requireNonNull(url).openStream()) {
                    Files.copy(in, file.toPath());
                }
                System.load(file.getCanonicalPath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static String normalizeOs(String value) {
        value = normalize(value);
        if (value.startsWith("linux")) {
            return "linux";
        }
        if (value.startsWith("windows")) {
            return "windows";
        }

        return "unknown";
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
    }

    private static String normalizeArch(String value) {
        value = normalize(value);
        if (value.matches("^(x8664|amd64|ia32e|em64t|x64)$")) {
            return "x86_64";
        }
        if (value.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) {
            return "x86";
        }
        if (value.matches("^(arm|arm32)$")) {
            return "arm_32";
        }
        if ("aarch64".equals(value)) {
            return "aarch_64";
        }
        return "unknown";
    }

    private static String libFilename(String libName) {
        if (NORMALIZED_OS.contains("win")) {
            return libName + ".dll";
        } else {
            return libName + ".so";
        }
    }

    private static String nameOnly(String libName) {
        int pos = libName.lastIndexOf('/');
        if (pos >= 0) {
            return libName.substring(pos + 1);
        }
        return libName;
    }
}
