package net.hypercubemc.iris_installer.shaders;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * The shader packs in a shaderpacks folder that the installer can make MCME
 * edits of - copies that draw the fire eye (ShaderPackPatcher), saved next to
 * them as {@code <name>-MCME-edit}, with their Iris settings - and making them.
 */
public final class ShaderPacks {

    public static final String SUFFIX = "-MCME-edit";

    private ShaderPacks() {
    }

    /** A file of a version of a shader pack, on Modrinth. */
    public static final class Download {
        public final String variant, fileName, url, sha1;

        Download(String variant, String fileName, String url, String sha1) {
            this.variant = variant;
            this.fileName = fileName;
            this.url = url;
            this.sha1 = sha1;
        }
    }

    /**
     * A shader pack the installer knows: how its files are named, the recipe
     * that edits it, and the version of it the recipe was last checked
     * against, to download where a pack's own version can't be edited.
     */
    public static final class Family {
        public final String name, recipe, version, page;
        final Pattern pattern;
        final Download[] downloads;

        Family(String name, String pattern, String recipe, String version, String page, Download... downloads) {
            this.name = name;
            this.pattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            this.recipe = recipe;
            this.version = version;
            this.page = page;
            this.downloads = downloads;
        }

        /** Which of its files to get in place of a pack named name: Sildur's comes in several. */
        Download downloadFor(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (Download d : downloads) {
                if (d.variant != null && lower.contains(d.variant.toLowerCase(Locale.ROOT))) {
                    return d;
                }
            }
            return downloads[0];
        }
    }

    private static final String CDN = "https://cdn.modrinth.com/data/";

    /**
     * Checked with ShaderPackPatcher (and patch_shaderpack.py) against each
     * pack's three newest versions on Modrinth, 2026-10-02; the newest is the
     * one offered. In the order names are matched: Spooklementary and Unbound
     * before Complementary, Sildur's variants with a suffix before those
     * without.
     */
    static final Family[] FAMILIES = {
            new Family("Spooklementary", "spooklementary", "Complementary", "v2.0.4", "https://modrinth.com/shader/spooklementary",
                    new Download(null, "Spooklementary_v2.0.4.zip", CDN + "6uJCfiCH/versions/rcr90eRP/Spooklementary_v2.0.4.zip",
                            "38651af0c334b9a5d454b903c982ccb24db0869a")),
            new Family("Complementary Unbound", "unbound", "Complementary", "r5.9.3", "https://modrinth.com/shader/complementary-unbound",
                    new Download(null, "ComplementaryUnbound_r5.9.3.zip", CDN + "R6NEzAwj/versions/B1kyfoUZ/ComplementaryUnbound_r5.9.3.zip",
                            "2ee08300e1d6f039e63eae8484dddf57b3aaaf67")),
            new Family("Complementary Reimagined", "complementary|reimagined", "Complementary", "r5.9.3",
                    "https://modrinth.com/shader/complementary-reimagined",
                    new Download(null, "ComplementaryReimagined_r5.9.3.zip", CDN + "HVnmMxH1/versions/Bqen1mJX/ComplementaryReimagined_r5.9.3.zip",
                            "838139b54cddb56b2e83cd260d8efd960ac536d6")),
            new Family("Bliss", "bliss", "Bliss", "v2.1.2", "https://modrinth.com/shader/bliss-shader",
                    new Download(null, "Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip",
                            CDN + "ZvMtQlho/versions/kC2Y8q1P/Bliss_v2.1.2_%28Chocapic13_Shaders_edit%29.zip",
                            "ded6c8a98305f8aff90ef781e7585b33b8452cfd")),
            new Family("MakeUp - Ultra Fast", "make ?up", "MakeUp", "9.5f", "https://modrinth.com/shader/makeup-ultra-fast-shaders",
                    new Download(null, "MakeUp-UltraFast-9.5f.zip", CDN + "izsIPI7a/versions/IUFkxmHz/MakeUp-UltraFast-9.5f.zip",
                            "b3873dcc6b39093a6c13c2ec9c5d5eee90c06827")),
            new Family("Mellow", "mellow", "Mellow", "v3.4.1a", "https://modrinth.com/shader/mellow",
                    new Download(null, "Mellow v3.4.1a.zip", CDN + "BUxf36AP/versions/jIbGxrnZ/Mellow%20v3.4.1a.zip",
                            "5b96318ddd43ac9a37e6483f75d62f0f0f18f81e")),
            new Family("BSL", "bsl", "BSL", "v10.1.8", "https://modrinth.com/shader/bsl-shaders",
                    new Download(null, "BSL_v10.1.8.zip", CDN + "Q1vvjJYV/versions/Y68yiql9/BSL_v10.1.8.zip",
                            "e89818c8827d0131c102fb480c3c3446624db0df")),
            new Family("Solas", "solas", "Solas", "V3.7b", "https://modrinth.com/shader/solas-shader",
                    new Download(null, "Solas Shader V3.7b.zip", CDN + "EpQFjzrQ/versions/KcfQaN5J/Solas%20Shader%20V3.7b.zip",
                            "abf2e33feae040cc91894aea11e6d30750d50ee9")),
            new Family("Sildur's Vibrant", "sildur.*vibrant", "Sildur's", "v2.02", "https://modrinth.com/shader/sildurs-vibrant-shaders",
                    // High first: the one offered for a name with none of these
                    new Download("High", "Sildur's Vibrant Shaders v2.02 High.zip",
                            CDN + "z8EjLYqN/versions/U71uLBqm/Sildur%27s%20Vibrant%20Shaders%20v2.02%20High.zip",
                            "8ed755ae1397c9c17869a74cba8f529cb8eab7be"),
                    new Download("Extreme-VL", "Sildur's Vibrant Shaders v2.02 Extreme-VL.zip",
                            CDN + "z8EjLYqN/versions/QDQKRRS3/Sildur%27s%20Vibrant%20Shaders%20v2.02%20Extreme-VL.zip",
                            "494b41c4fd3e4c378f55524c2ddfb1fa42e2e11d"),
                    new Download("High-MB", "Sildur's Vibrant Shaders v2.02 High-MB.zip",
                            CDN + "z8EjLYqN/versions/admWtjGH/Sildur%27s%20Vibrant%20Shaders%20v2.02%20High-MB.zip",
                            "b2cdee8ebbb0551f6184666e71cdeb3c20692ba4"),
                    new Download("Extreme", "Sildur's Vibrant Shaders v2.02 Extreme.zip",
                            CDN + "z8EjLYqN/versions/J4RlzpKF/Sildur%27s%20Vibrant%20Shaders%20v2.02%20Extreme.zip",
                            "2362f4dd27178879c30374c8f33965999bc350c8"),
                    new Download("Medium", "Sildur's Vibrant Shaders v2.02 Medium.zip",
                            CDN + "z8EjLYqN/versions/EisvxIzu/Sildur%27s%20Vibrant%20Shaders%20v2.02%20Medium.zip",
                            "e10245b9801f14402a559008699923ba34c84ca7"),
                    new Download("Lite", "Sildur's Vibrant Shaders v2.02 Lite.zip",
                            CDN + "z8EjLYqN/versions/cianYi38/Sildur%27s%20Vibrant%20Shaders%20v2.02%20Lite.zip",
                            "9d2a2be38fd3f1645887b45a8d94ae653b97a72e")),
    };

    /** The families' names, for saying what is supported. */
    public static List<String> familyNames() {
        List<String> names = new ArrayList<>();
        for (Family f : FAMILIES) {
            names.add(f.name);
        }
        return names;
    }

    /** A shader pack found in the folder. */
    public static final class Pack {
        public final Path path;
        public final Family family;
        /** The recipe that edits it; null if none does - its version isn't one they know. */
        public final String recipe;

        Pack(Path path, Family family, String recipe) {
            this.path = path;
            this.family = family;
            this.recipe = recipe;
        }

        public String fileName() {
            return path.getFileName().toString();
        }

        /** Its name as Iris shows it: without .zip, nor Minecraft's formatting codes. */
        public String displayName() {
            return fileName().replaceAll("(?i)\\.zip$", "").replaceAll("§.", "");
        }

        public boolean supported() {
            return recipe != null;
        }

        boolean zipped() {
            return Files.isRegularFile(path);
        }

        /** Where its edit goes: next to it, named as it is with -MCME-edit before any .zip. */
        public Path edit() {
            String name = fileName();
            return zipped() && name.toLowerCase(Locale.ROOT).endsWith(".zip")
                    ? path.resolveSibling(name.substring(0, name.length() - 4) + SUFFIX + ".zip")
                    : path.resolveSibling(name + SUFFIX);
        }

        /** Whether its edit is there, made by this or patch_shaderpack.py. */
        public boolean edited() {
            return Files.exists(edit()) && isEdit(edit());
        }

        /** Whether something not made by this is where its edit goes - replaced only if the player says so. */
        public boolean editTakenByOther() {
            return Files.exists(edit()) && !isEdit(edit());
        }

        /** The family's version that can be edited, to get in its place. */
        public Download download() {
            return family.downloadFor(fileName());
        }
    }

    /**
     * The shader packs in shaderpacks the installer knows - each tried with
     * the recipes, on a copy - sorted by name; edits left out.
     */
    public static List<Pack> scan(Path shaderpacks, ShaderPackPatcher.Eye eye) throws IOException {
        List<Pack> packs = new ArrayList<>();
        if (!Files.isDirectory(shaderpacks)) {
            return packs;
        }
        List<Path> entries;
        try (Stream<Path> list = Files.list(shaderpacks)) {
            entries = list.sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .collect(Collectors.toList());
        }
        for (Path entry : entries) {
            String name = entry.getFileName().toString();
            boolean zip = Files.isRegularFile(entry) && name.toLowerCase(Locale.ROOT).endsWith(".zip");
            if (!(zip || Files.isDirectory(entry)) || isEdit(entry)) {
                continue;
            }
            String recipe = trial(entry, eye);
            Family family = familyOf(name, recipe);
            if (family != null) {
                packs.add(new Pack(entry, family, recipe));
            }
        }
        return packs;
    }

    /** Which family a pack named name, edited by recipe (null if none), belongs to; null if none. */
    static Family familyOf(String name, String recipe) {
        String plain = name.replaceAll("§.", "");
        for (Family f : FAMILIES) {
            if (f.pattern.matcher(plain).find() && (recipe == null || f.recipe.equals(recipe))) {
                return f;
            }
        }
        if (recipe != null) {
            // an edit of a pack named otherwise: the recipe's first family
            for (Family f : FAMILIES) {
                if (f.recipe.equals(recipe) && !f.name.equals("Spooklementary") && !f.name.equals("Complementary Unbound")) {
                    return f;
                }
            }
        }
        return null;
    }

    /** Whether path is an edit: named as one, or holding the patcher's marker. */
    static boolean isEdit(Path path) {
        String name = path.getFileName().toString().replaceAll("(?i)\\.zip$", "");
        if (name.endsWith(SUFFIX)) {
            return true;
        }
        if (Files.isDirectory(path)) {
            return Files.isRegularFile(path.resolve(ShaderPackPatcher.MARKER));
        }
        try (ZipFile zip = openZip(path)) {
            return zip.getEntry(ShaderPackPatcher.MARKER) != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** The recipe that edits the pack at path, tried on a copy; null if none does, or it can't be read. */
    static String trial(Path path, ShaderPackPatcher.Eye eye) {
        Path staging = null;
        try {
            staging = Files.createTempDirectory("mcme-shaderpack");
            Path root = unpack(path, staging);
            return root == null ? null : ShaderPackPatcher.patch(root, eye);
        } catch (IOException | ShaderPackPatcher.NotSupported | RuntimeException e) {
            return null;
        } finally {
            deleteQuietly(staging);
        }
    }

    /**
     * Makes pack's edit: a copy of it, patched, where pack.edit() says -
     * replacing what is there - with a copy of its Iris settings (the .txt
     * Iris keeps next to it) named after it. The recipe used.
     */
    public static String makeEdit(Pack pack, ShaderPackPatcher.Eye eye) throws IOException, ShaderPackPatcher.NotSupported {
        Path staging = Files.createTempDirectory("mcme-shaderpack");
        try {
            Path root = unpack(pack.path, staging);
            if (root == null) {
                throw new IOException("no shaders/shaders.properties in " + pack.fileName());
            }
            String recipe = ShaderPackPatcher.patch(root, eye);
            ShaderPackPatcher.write(root.resolve(ShaderPackPatcher.MARKER),
                    pack.fileName() + " with RP-Mordor's fire eye at " + eye.x + " " + eye.y + " " + eye.z + ", made by\n"
                            + "the MCME Installer (" + recipe + " recipe, from patch_shaderpack.py).\n"
                            + "The shader pack's own licence and credits still apply.\n");

            Path edit = pack.edit();
            if (pack.zipped()) {
                Path part = edit.resolveSibling(edit.getFileName() + ".part");
                zip(root, part);
                deleteQuietly(Files.isDirectory(edit) ? edit : null);
                Files.move(part, edit, StandardCopyOption.REPLACE_EXISTING);
            } else {
                deleteQuietly(edit);
                Files.deleteIfExists(edit);
                copyTree(root, edit);
            }

            Path settings = settingsOf(pack.path);
            if (Files.isRegularFile(settings)) {
                Files.copy(settings, settingsOf(edit), StandardCopyOption.REPLACE_EXISTING);
            }
            return recipe;
        } finally {
            deleteQuietly(staging);
        }
    }

    /** Where Iris keeps the settings of the pack at path: its name with .txt after it. */
    static Path settingsOf(Path pack) {
        return pack.resolveSibling(pack.getFileName() + ".txt");
    }

    /**
     * Downloads d into shaderpacks, checking it is the file the families name;
     * progress in percent. Where it went.
     */
    public static Path download(Download d, Path shaderpacks, IntConsumer progress) throws IOException {
        Files.createDirectories(shaderpacks);
        Path target = shaderpacks.resolve(d.fileName);
        Path part = shaderpacks.resolve(d.fileName + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(d.url).openConnection();
        connection.setRequestProperty("User-Agent", "MCME-Installer (github.com/MCME/mcme-installer)");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("the download answered " + connection.getResponseCode());
        }
        long size = connection.getContentLengthLong();
        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(part))) {
            byte[] buffer = new byte[16384];
            long read = 0;
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
                sha1.update(buffer, 0, n);
                read += n;
                if (size > 0) {
                    progress.accept((int) (read * 100 / size));
                }
            }
        } catch (IOException e) {
            Files.deleteIfExists(part);
            throw e;
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : sha1.digest()) {
            hex.append(String.format("%02x", b));
        }
        if (!hex.toString().equals(d.sha1)) {
            Files.deleteIfExists(part);
            throw new IOException(d.fileName + " didn't download whole - try again");
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    // ---------------------------------------------------------------- files

    /**
     * Copies the pack at path - a zip or a folder - into staging. Where in it
     * the pack's shaders/ is (as patch_shaderpack.py looks: at the top, or one
     * folder down); null if nowhere.
     */
    static Path unpack(Path path, Path staging) throws IOException {
        if (Files.isDirectory(path)) {
            copyTree(path, staging);
        } else {
            try (ZipFile zip = openZip(path)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    Path to = staging.resolve(entry.getName().replace('\\', '/').replaceFirst("^/+", "")).normalize();
                    if (!to.startsWith(staging) || to.equals(staging)) {
                        continue;
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(to);
                    } else {
                        Files.createDirectories(to.getParent());
                        try (InputStream in = zip.getInputStream(entry)) {
                            Files.copy(in, to, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        }
        List<Path> roots = new ArrayList<>();
        roots.add(staging);
        try (DirectoryStream<Path> children = Files.newDirectoryStream(staging)) {
            List<Path> folders = new ArrayList<>();
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    folders.add(child);
                }
            }
            Collections.sort(folders);
            roots.addAll(folders);
        }
        for (Path root : roots) {
            if (Files.isRegularFile(root.resolve("shaders/shaders.properties"))) {
                return root;
            }
        }
        return null;
    }

    /** A zip's names as Python reads them: UTF-8 where flagged so, else code page 437. */
    static ZipFile openZip(Path path) throws IOException {
        Charset names;
        try {
            names = Charset.forName("IBM437");
        } catch (RuntimeException e) {
            names = StandardCharsets.UTF_8;
        }
        return new ZipFile(path.toFile(), names);
    }

    static void zip(Path root, Path to) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> !p.equals(root)).sorted().collect(Collectors.toList());
        }
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(to)), StandardCharsets.UTF_8)) {
            for (Path p : files) {
                String name = root.relativize(p).toString().replace(File.separatorChar, '/');
                if (Files.isDirectory(p)) {
                    out.putNextEntry(new ZipEntry(name + "/"));
                } else {
                    out.putNextEntry(new ZipEntry(name));
                    Files.copy(p, out);
                }
                out.closeEntry();
            }
        }
    }

    static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Deletes a folder and what is in it, if it is there. */
    static void deleteQuietly(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            List<Path> all = walk.sorted(Collections.reverseOrder()).collect(Collectors.toList());
            for (Path p : all) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {
        }
    }
}
