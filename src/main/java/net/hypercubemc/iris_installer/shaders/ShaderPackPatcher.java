package net.hypercubemc.iris_installer.shaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes a copy of an Iris shader pack draw RP-Mordor's fire eye.
 *
 * A port of patch_shaderpack.py (MCME/ResourcePackScripts, patchShaderpack/),
 * which says why and how: under a shader pack the resource pack's terrain
 * shaders don't run, so the eye is drawn over the pack's finished scene,
 * after fog and clouds and before bloom, at its block. Recipe for recipe it
 * writes the same files as the script, so a change to one belongs in both.
 *
 * Packs are recognised by their code, not their name: each recipe checks
 * everything it needs before changing anything, and refuses (Unsupported)
 * a pack that isn't its own or whose code has changed.
 *
 * The eye's includes (far_terrain.glsl, fire_eye_config.glsl, fire_eye.glsl)
 * are RP-Mordor's assets/minecraft/shaders/include files, bundled in
 * /mcme/fire_eye/; the eye's block is fire_eye_config.glsl's FIRE_EYE_BLOCK.
 */
public final class ShaderPackPatcher {

    public static final String MARKER = "MCME-PATCH.txt";
    static final String[] EYE_INCLUDES = {"far_terrain.glsl", "fire_eye_config.glsl", "fire_eye.glsl"};

    // how bright the eye and its glow are, relative to the resource pack's look
    static final String BRIGHTNESS = "1.5";
    static final String GLOW = "1.0";

    private ShaderPackPatcher() {
    }

    /** Why a recipe can't patch a pack. */
    public static class Unsupported extends Exception {
        public Unsupported(String message) {
            super(message);
        }
    }

    /** No recipe could patch a pack: each one's reason, by recipe. */
    public static class NotSupported extends Exception {
        public final Map<String, String> refusals;

        NotSupported(Map<String, String> refusals) {
            super("not a supported shader pack");
            this.refusals = refusals;
        }
    }

    /** The eye: its includes' text, by name, and its block. */
    public static final class Eye {
        public final Map<String, String> includes;
        public final int x, y, z;

        public Eye(Map<String, String> includes, int x, int y, int z) {
            this.includes = includes;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** The includes bundled in the installer, the eye where they put it. */
        public static Eye bundled() throws IOException {
            Map<String, String> includes = new LinkedHashMap<>();
            for (String name : EYE_INCLUDES) {
                includes.put(name, resource("/mcme/fire_eye/" + name));
            }
            Matcher m = re("#define FIRE_EYE_BLOCK ivec3\\((-?\\d+), *(-?\\d+), *(-?\\d+)\\)").matcher(includes.get("fire_eye_config.glsl"));
            if (!m.find()) {
                throw new IOException("the bundled fire_eye_config.glsl has no FIRE_EYE_BLOCK");
            }
            return new Eye(includes, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        }
    }

    // ---------------------------------------------------------------- shaders
    // The script's FACE_GLSL, DRAW_GLSL, PASS_VSH, PASS_FSH and LOD_GLSL, in
    // /mcme/patch/.

    static String drawDefines(Eye eye, boolean linear) {
        return "#define MCME_EYE_BLOCK ivec3(" + eye.x + ", " + eye.y + ", " + eye.z + ")\n"
                + "#define MCME_LINEAR " + (linear ? 1 : 0) + "\n"
                + "#define MCME_BRIGHTNESS " + BRIGHTNESS + "\n"
                + "#define MCME_GLOW " + GLOW + "\n";
    }

    static final String[][] LOD_UNIFORMS = {{"sampler2D", "dhDepthTex0"}, {"mat4", "dhProjectionInverse"},
            {"sampler2D", "vxDepthTexOpaque"}, {"mat4", "vxProjInv"}};

    static List<String> names(String[][] uniforms) {
        List<String> names = new ArrayList<>();
        for (String[] u : uniforms) {
            names.add(u[1]);
        }
        return names;
    }

    // ---------------------------------------------------------------- editing

    /** Patterns as the script's: . and ^ go by \n alone, \s and \b by Unicode. */
    static Pattern re(String regex) {
        return Pattern.compile(regex, Pattern.UNIX_LINES | Pattern.UNICODE_CHARACTER_CLASS);
    }

    static boolean search(String regex, String text) {
        return re(regex).matcher(text).find();
    }

    static String resource(String name) throws IOException {
        try (InputStream in = ShaderPackPatcher.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("missing resource " + name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
            // as the script has them, whatever a checkout did to their line ends
            return new String(out.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    static String read(Path path) throws IOException, Unsupported {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
        } catch (CharacterCodingException e) {
            throw new Unsupported(path.getFileName() + " isn't UTF-8");
        }
    }

    static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    static boolean isFile(Path path) {
        return Files.isRegularFile(path);
    }

    static String nl(String text) {
        return text.contains("\r\n") ? "\r\n" : "\n";
    }

    /** Python's str.rstrip(). */
    static String rstrip(String text) {
        int end = text.length();
        while (end > 0 && isPythonSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    static boolean isPythonSpace(char c) {
        return (c >= '\t' && c <= '\r') || (c >= '\u001c' && c <= ' ') || c == '\u0085' || c == ' ' || c == ' '
                || (c >= ' ' && c <= ' ') || c == ' ' || c == ' ' || c == ' ' || c == ' '
                || c == '　';
    }

    static Matcher findOne(String text, String anchor, String what) throws Unsupported {
        Matcher m = re(anchor).matcher(text);
        Matcher first = null;
        int count = 0;
        while (m.find()) {
            if (count++ == 0) {
                first = re(anchor).matcher(text);
                first.find(m.start());
            }
        }
        if (count != 1) {
            throw new Unsupported("expected one '" + anchor + "' in " + what + ", found " + count);
        }
        return first;
    }

    static String insert(String text, String anchor, String what, String addition, boolean before) throws Unsupported {
        Matcher m = findOne(text, anchor, what);
        int i = before ? m.start() : m.end();
        return text.substring(0, i) + addition.replace("\n", nl(text)) + text.substring(i);
    }

    static String insert(String text, String anchor, String what, String addition) throws Unsupported {
        return insert(text, anchor, what, addition, false);
    }

    /** The index just past the brace closing the one at open, skipping comments. */
    static int endOfBlock(String text, int open) throws Unsupported {
        int depth = 0, i = open;
        while (i < text.length()) {
            if (text.startsWith("//", i)) {
                i = text.indexOf('\n', i);
                i = i < 0 ? text.length() : i;
            } else if (text.startsWith("/*", i)) {
                i = text.indexOf("*/", i) + 2;
                if (i < 2) {
                    break;
                }
            } else {
                if (text.charAt(i) == '{') {
                    depth++;
                } else if (text.charAt(i) == '}') {
                    depth--;
                    if (depth == 0) {
                        return i + 1;
                    }
                }
                i++;
            }
        }
        throw new Unsupported("unbalanced braces");
    }

    static final Pattern INCLUDE = re("#include\\s+\"([^\"]+)\"");

    /** Where an #include of name points (Iris style: /-rooted at shaders, or relative to here); null if nowhere. */
    static Path included(Path shaders, Path here, String name) {
        try {
            return name.startsWith("/") ? shaders.resolve(name.replaceFirst("^/+", "")) : here.resolve(name);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /** text with its #includes expanded, every branch kept - for finding declarations. */
    static String expand(Path shaders, String text, Path here, Set<Path> seen) throws IOException, Unsupported {
        Matcher m = INCLUDE.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            last = m.end();
            Path path = included(shaders, here, m.group(1));
            if (path == null) {
                continue;
            }
            path = path.toAbsolutePath().normalize();
            if (seen.contains(path) || !isFile(path)) {
                continue;
            }
            seen.add(path);
            out.append(expand(shaders, read(path), path.getParent(), seen));
        }
        return out.append(text.substring(last)).toString();
    }

    static final Pattern COMMENT = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    /** text with its comments blanked out, everything else where it was. */
    static String blankComments(String text) {
        Matcher m = COMMENT.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            for (int i = m.start(); i < m.end(); i++) {
                out.append(text.charAt(i) == '\n' ? '\n' : ' ');
            }
            last = m.end();
        }
        return out.append(text.substring(last)).toString();
    }

    /** Where the lines holding text's own declarations of uniform name end. */
    static List<Integer> declarationEnds(String text, String name) {
        List<Integer> ends = new ArrayList<>();
        Matcher m = re("\\buniform\\b[^;{}]*\\b" + name + "\\b[^;{}]*;").matcher(blankComments(text));
        while (m.find()) {
            int nl = text.indexOf('\n', m.end());
            ends.add(nl < 0 ? text.length() : nl + 1);
        }
        return ends;
    }

    static boolean declares(String text, String name) {
        return !declarationEnds(text, name).isEmpty();
    }

    /** The files text includes, directly or not, each once. */
    static List<Path> includes(Path shaders, String text, Path here) throws IOException, Unsupported {
        List<Path> found = new ArrayList<>();
        walk(shaders, text, here, found);
        return found;
    }

    private static void walk(Path shaders, String text, Path here, List<Path> found) throws IOException, Unsupported {
        Matcher m = INCLUDE.matcher(blankComments(text));
        while (m.find()) {
            Path path = included(shaders, here, m.group(1));
            if (path == null) {
                continue;
            }
            path = path.normalize();
            if (!found.contains(path) && isFile(path)) {
                found.add(path);
                walk(shaders, read(path), path.getParent(), found);
            }
        }
    }

    static String current(Map<Path, String> edits, Path path) throws IOException, Unsupported {
        return edits.containsKey(path) ? edits.get(path) : read(path);
    }

    /**
     * path's text, with #define MCME_DECLARED_name after each of the shader
     * pack's own declarations of the uniforms named that come before at - in
     * text, or in what it includes, whose new text goes into edits - so what is
     * added at at declares each only where the pack's declaration isn't
     * compiled: shader packs declare many uniforms only under some settings.
     * Unsupported if one is declared after at (-1: the end).
     */
    static String markDeclarations(Path shaders, Path path, String text, int at, List<String> names, Map<Path, String> edits)
            throws IOException, Unsupported {
        at = at < 0 ? text.length() : at;
        String before = text.substring(0, at), after = text.substring(at);
        for (String name : names) {
            boolean later = declares(after, name);
            if (!later) {
                for (Path f : includes(shaders, after, path.getParent())) {
                    if (declares(current(edits, f), name)) {
                        later = true;
                        break;
                    }
                }
            }
            if (later) {
                throw new Unsupported(path.getFileName() + " declares " + name + " after where it is needed");
            }
        }
        for (String name : names) {
            for (Path f : includes(shaders, before, path.getParent())) {
                if (declares(current(edits, f), name)) {
                    edits.put(f, marked(current(edits, f), name));
                }
            }
            before = marked(before, name);
        }
        return before + after;
    }

    private static String marked(String text, String name) {
        String define = "#define MCME_DECLARED_" + name + nl(text);
        List<Integer> ends = declarationEnds(text, name);
        Collections.reverse(ends);
        for (int end : ends) {
            if (!text.startsWith(define, end)) {
                text = text.substring(0, end) + define + text.substring(end);
            }
        }
        return text;
    }

    /**
     * path's text with the uniforms - {type, name} - declared at at, each only
     * where the shader pack's own declaration of it isn't compiled (see
     * markDeclarations).
     */
    static String declareUniforms(Path shaders, Path path, String text, int at, String[][] uniforms, Map<Path, String> edits)
            throws IOException, Unsupported {
        String marked = markDeclarations(shaders, path, text, at, names(uniforms), edits);
        at += marked.length() - text.length();
        return marked.substring(0, at) + uniformLines(uniforms).replace("\n", nl(text)) + marked.substring(at);
    }

    static String uniformLines(String[][] uniforms) {
        StringBuilder lines = new StringBuilder();
        for (String[] u : uniforms) {
            lines.append("#ifndef MCME_DECLARED_").append(u[1]).append("\nuniform ").append(u[0]).append(' ').append(u[1])
                    .append(";\n#endif\n");
        }
        return lines.toString();
    }

    /**
     * path's text, its vertex main() - the first after startMarker - renamed
     * name, and wrapper after it: the new main(), calling it, with the uniforms
     * it declares, named names, marked (markDeclarations).
     */
    static String wrapVertexMain(Path shaders, Path path, String name, String wrapper, Map<Path, String> edits,
                                 List<String> names, String startMarker) throws IOException, Unsupported {
        String text = current(edits, path);
        int start = startMarker != null ? text.indexOf(startMarker) : 0;
        Matcher m = re("void\\s+main\\s*\\(\\s*\\)").matcher(text);
        if (start < 0 || !m.find(start)) {
            throw new Unsupported("no vertex main() in " + path.getFileName());
        }
        int brace = text.indexOf('{', m.end());
        if (brace < 0) {
            throw new Unsupported("no vertex main() in " + path.getFileName());
        }
        int end = endOfBlock(text, brace);
        String renamed = text.substring(0, m.start()) + "void " + name + "()" + text.substring(m.end(), end);
        String whole = renamed + text.substring(end);
        String marked = markDeclarations(shaders, path, whole, renamed.length(), names, edits);
        int at = renamed.length() + marked.length() - whole.length();
        return marked.substring(0, at) + wrapper.replace("\n", nl(text)) + marked.substring(at);
    }

    /** path's text, its vertex main() wrapped so the eye block's faces collapse to a point. */
    static String dropEyeFaces(Path shaders, Path path, Map<Path, String> edits, String startMarker)
            throws IOException, Unsupported {
        return wrapVertexMain(shaders, path, "mcmePackMain",
                "\n\n// MCME: the fire eye block's faces are dropped - the eye is drawn over the scene\n"
                        + "#include \"/lib/mcme/fire_eye_face.glsl\"\n"
                        + "void main() {\n"
                        + "    mcmePackMain();\n"
                        + "    if (mcmeFireEyeFace((gl_TextureMatrix[0] * gl_MultiTexCoord0).xy)) gl_Position = vec4(0.0);\n"
                        + "}\n", edits, Collections.singletonList("gtexture"), startMarker);
    }

    static String dropEyeFaces(Path shaders, Path path, Map<Path, String> edits) throws IOException, Unsupported {
        return dropEyeFaces(shaders, path, edits, null);
    }

    /**
     * path's text, its vertex main() - particles' - wrapped so that, while a
     * distant terrain mod draws the world, particles past the server's view
     * distance collapse to a point: they come from chunks loaded but not
     * drawn, and would show through the mod's terrain in front of them. Only
     * in programs defining guard, if given.
     */
    static String dropFarParticles(Path shaders, Path path, Map<Path, String> edits, String startMarker, String guard)
            throws IOException, Unsupported {
        String test = (guard != null ? "    #ifdef " + guard + "\n" : "")
                + "    if (length((gl_ModelViewMatrix * gl_Vertex).xyz) > SERVER_VIEW_DISTANCE) gl_Position = vec4(0.0);\n"
                + (guard != null ? "    #endif\n" : "");
        return wrapVertexMain(shaders, path, "mcmeParticleMain",
                "\n\n// MCME: with a distant terrain mod, particles past the server's view distance\n"
                        + "// are dropped - they would show through its terrain (lib/mcme/far_terrain.glsl)\n"
                        + "#include \"/lib/mcme/far_terrain.glsl\"\n"
                        + "void main() {\n"
                        + "    mcmeParticleMain();\n"
                        + "#if defined DISTANT_HORIZONS || defined VOXY\n" + test + "#endif\n"
                        + "}\n", edits, Collections.<String>emptyList(), startMarker);
    }

    static String dropFarParticles(Path shaders, Path path, Map<Path, String> edits) throws IOException, Unsupported {
        return dropFarParticles(shaders, path, edits, null, null);
    }

    /** The files of an overworld compositeN, in folder, drawing the eye over colortex buffer. */
    static Map<Path, String> newPass(Path shaders, int number, int buffer, String scale, boolean linear, Eye eye,
                                     String settings, String folder) throws IOException, Unsupported {
        Path world = shaders.resolve(folder).normalize();
        for (String ext : new String[]{"vsh", "fsh"}) {
            if (Files.exists(world.resolve("composite" + number + "." + ext))) {
                throw new Unsupported("world0/composite" + number + "." + ext + " is taken");
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("settings", settings);
        values.put("buffer", Integer.toString(buffer));
        values.put("lod", resource("/mcme/patch/lod.glsl"));
        values.put("defines", drawDefines(eye, linear));
        values.put("scale", scale);
        Matcher m = Pattern.compile("\\{(settings|buffer|lod|defines|scale)\\}").matcher(resource("/mcme/patch/pass.fsh"));
        StringBuffer fsh = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(fsh, Matcher.quoteReplacement(values.get(m.group(1))));
        }
        m.appendTail(fsh);
        Map<Path, String> files = new LinkedHashMap<>();
        files.put(world.resolve("composite" + number + ".vsh"), resource("/mcme/patch/pass.vsh"));
        files.put(world.resolve("composite" + number + ".fsh"), fsh.toString());
        return files;
    }

    static Map<Path, String> newPass(Path shaders, int number, int buffer, String scale, boolean linear, Eye eye)
            throws IOException, Unsupported {
        return newPass(shaders, number, buffer, scale, linear, eye, "", "world0");
    }

    // ---------------------------------------------------------------- recipes
    // Each checks everything it needs before changing anything, and returns
    // the files it changes; Unsupported if the pack isn't its.

    interface Recipe {
        Map<Path, String> apply(Path shaders, Eye eye) throws IOException, Unsupported;
    }

    static final String[][] BLISS_UNIFORMS = {{"ivec3", "cameraPositionInt"}, {"vec3", "cameraPositionFract"}, {"float", "far"},
            {"mat4", "gbufferModelViewInverse"}, {"float", "frameTimeCounter"}, {"sampler2D", "colortex4"}};

    /**
     * Bliss (Chocapic13 edit), 2.1 and its development builds: faces dropped in
     * dimensions/all_translucent.vsh; the eye drawn in composite3, after fog,
     * clouds and the translucents, with Bliss's own view position and distance
     * (Distant Horizons' included: swappedDepth) and its exposure.
     */
    static Map<Path, String> bliss(Path shaders, Eye eye) throws IOException, Unsupported {
        Path dims = shaders.resolve("dimensions");
        Path vsh = dims.resolve("all_translucent.vsh"), comp = dims.resolve("composite3.fsh");
        if (!(isFile(vsh) && isFile(comp) && isFile(shaders.resolve("world0/gbuffers_water.vsh")))) {
            throw new Unsupported("not Bliss");
        }
        String text = read(comp);
        // its view position: viewPos in development builds, fragpos in 2.1
        String view = null;
        for (String v : new String[]{"viewPos", "fragpos"}) {
            if (search("vec3 " + v + " = toScreenSpace_DH\\(", text)) {
                view = v;
                break;
            }
        }
        if (view == null || !search("float swappedDepth = ", text) || !search("float linearDistance = ", text)) {
            throw new Unsupported("Bliss's composite3 has changed");
        }
        Path particles = dims.resolve("all_particles.vsh");
        if (!isFile(particles)) {
            throw new Unsupported("Bliss's particles have moved");
        }
        Map<Path, String> edits = new LinkedHashMap<>();
        boolean clouds = blissClouds(shaders, eye, edits);
        String name = comp.getFileName().toString();
        text = insert(text, "void main\\(\\) \\{", name, "// MCME: the fire eye, at its block (patch_shaderpack.py)\n"
                + "#ifdef OVERWORLD_SHADER\n"
                + drawDefines(eye, true) + "#include \"/lib/mcme/fire_eye_draw.glsl\"\n"
                + (clouds ? "uniform sampler2D mcmeEyeCloudSampler;\n" : "") + "#endif\n"
                + "\n", true);
        text = declareUniforms(shaders, comp, text, text.indexOf("// MCME: the fire eye, at its block"), BLISS_UNIFORMS, edits);
        edits.put(vsh, dropEyeFaces(shaders, vsh, edits));
        edits.put(particles, dropFarParticles(shaders, particles, edits));
        text = insert(text, "gl_FragData\\[0\\](\\.r)? = (vec4\\()?bloomyFogMult", name, "\n"
                + "  // MCME: the fire eye, after fog and clouds, so it shows at any distance,\n"
                + "  // Distant Horizons' too, and nothing cuts its glow off"
                + (clouds ? " - faded by the clouds between it and the camera, which composite2 measures" : "") + "\n"
                + "  #ifdef OVERWORLD_SHADER\n"
                + "    " + (clouds ? "mcmeFireVisibility = texelFetch(mcmeEyeCloudSampler, ivec2(0), 0).r;" : "") + "\n"
                + "    color.rgb = mcmeDrawFireEye(color.rgb, " + view + ", swappedDepth >= 1.0 ? 1.0e9 : linearDistance,\n"
                + "                                1.0 / max(texelFetch(colortex4, ivec2(10, 37), 0).r, 1.0e-4));\n"
                + "  #endif\n"
                + "\n", true);
        edits.put(comp, text);
        return edits;
    }

    // How much light gets through Bliss's clouds between the camera and the
    // eye, for composite3 to fade the eye by: Bliss's own clouds, from its own
    // cloud function, on the line to the eye, marched only as far as the eye -
    // so the eye fades only behind clouds Bliss draws in front of it. Once a
    // frame, by one pixel, into a 1x1 image.
    static String blissCloudsGlsl(String defines) {
        return "\n"
                + "// MCME: the clouds between the camera and the fire eye (patch_shaderpack.py)\n"
                + "#ifdef OVERWORLD_SHADER\n"
                + "layout(r16f) uniform image2D mcmeEyeCloud;\n"
                + defines + "#endif\n"
                + "\n";
    }

    static final String BLISS_CLOUDS_MAIN = "\n"
            + "\t\t// MCME: the clouds between the camera and the fire eye, once a frame:\n"
            + "\t\t// Bliss's own, as far as the eye\n"
            + "\t\tif (all(lessThan(gl_FragCoord.xy, vec2(1.0)))) {\n"
            + "\t\t\tvec3 mcmeEye = vec3(MCME_EYE_BLOCK - cameraPositionInt) + 0.5 - cameraPositionFract;\n"
            + "\t\t\tfloat mcmeCloudDistance = cloudPlaneDistance;\n"
            + "\t\t\tvec4 mcmeClouds = GetVolumetricClouds((gbufferModelView * vec4(mcmeEye, 1.0)).xyz, vec2(0.5), WsunVec,\n"
            + "\t\t\t                                      directLightColor, indirectLightColor, mcmeCloudDistance, phaseLevels, backScatterPhase);\n"
            + "\t\t\timageStore(mcmeEyeCloud, ivec2(0), vec4(mcmeClouds.a));\n"
            + "\t\t}\n";

    /**
     * Adds to edits those measuring the clouds in front of the eye - none where
     * Bliss's clouds aren't the ones this knows (2.1's). Whether it did.
     */
    static boolean blissClouds(Path shaders, Eye eye, Map<Path, String> edits) throws IOException, Unsupported {
        Path comp2 = shaders.resolve("dimensions/composite2.fsh");
        if (!isFile(comp2)) {
            return false;
        }
        String text = current(edits, comp2);
        String full = expand(shaders, text, comp2.getParent(), new HashSet<Path>());
        String anchor = "vec4 VolumetricClouds = GetVolumetricClouds\\(viewPos0, BN, WsunVec, directLightColor, indirectLightColor, "
                + "cloudPlaneDistance, phaseLevels, backScatterPhase\\);";
        if (!search(anchor, text) || !full.contains("vec4 GetVolumetricClouds(")) {
            return false;
        }
        String name = comp2.getFileName().toString();
        text = "#extension GL_ARB_shader_image_load_store : enable\n" + text;
        text = insert(text, "void main\\(\\) \\{", name,
                blissCloudsGlsl("#define MCME_EYE_BLOCK ivec3(" + eye.x + ", " + eye.y + ", " + eye.z + ")\n"), true);
        text = declareUniforms(shaders, comp2, text, text.indexOf("// MCME: the clouds between the camera and the fire eye (patch"),
                new String[][]{{"ivec3", "cameraPositionInt"}, {"vec3", "cameraPositionFract"}, {"mat4", "gbufferModelView"}}, edits);
        text = insert(text, anchor, name, BLISS_CLOUDS_MAIN);
        Path props = shaders.resolve("shaders.properties");
        String nl = nl(read(props));
        edits.put(comp2, text);
        edits.put(props, rstrip(current(edits, props)) + nl + nl
                + "# MCME: the clouds between the camera and the fire eye (patch_shaderpack.py)" + nl
                + "image.mcmeEyeCloud = mcmeEyeCloudSampler RED R16F HALF_FLOAT false false 1 1" + nl);
        return true;
    }

    /**
     * MakeUp (and edits): faces dropped in common/water_blocks_vertex.glsl; the
     * eye drawn in composite (its first, after the forward-rendered scene and
     * its fog) before it takes its bloom source, in its gamma-space colours and
     * exposure. It has no free pass, so this goes into its own.
     */
    static Map<Path, String> makeup(Path shaders, Eye eye) throws IOException, Unsupported {
        Path vertex = shaders.resolve("common/water_blocks_vertex.glsl"), comp = shaders.resolve("common/composite_fragment.glsl");
        if (!(isFile(vertex) && isFile(comp))) {
            throw new Unsupported("not MakeUp");
        }
        Map<Path, String> edits = new LinkedHashMap<>();
        String text = read(comp);
        String name = comp.getFileName().toString();
        // the LOD part declares the distant terrain mods' uniforms
        text = markDeclarations(shaders, comp, text, findOne(text, "// MAIN FUNCTION -+", name).start(), names(LOD_UNIFORMS), edits);
        text = insert(text, "// MAIN FUNCTION -+", name, "// MCME: the fire eye, at its block (patch_shaderpack.py)\n"
                + "#if !defined THE_END && !defined NETHER\n"
                + resource("/mcme/patch/lod.glsl") + drawDefines(eye, false) + "#include \"/lib/mcme/fire_eye_draw.glsl\"\n"
                + "#endif\n"
                + "\n", true);
        text = declareUniforms(shaders, comp, text, text.indexOf("// MCME: the fire eye, at its block"), new String[][]{
                {"mat4", "gbufferProjectionInverse"}, {"mat4", "gbufferModelViewInverse"}, {"ivec3", "cameraPositionInt"},
                {"vec3", "cameraPositionFract"}, {"float", "frameTimeCounter"}, {"float", "far"}}, edits);
        text = insert(text, "    #ifdef BLOOM\\s*\\n\\s*// Bloom source", name, "    // MCME: the fire eye, before the bloom source, so it blooms\n"
                + "    #if !defined THE_END && !defined NETHER\n"
                + "    {\n"
                + "        vec4 mcmeView = gbufferProjectionInverse * vec4(vec3(texcoord, d) * 2.0 - 1.0, 1.0);\n"
                + "        mcmeView /= mcmeView.w;\n"
                + "        float mcmeDistance = mcmeLodDistance(texcoord, ivec2(gl_FragCoord.xy), d < 1.0 ? length(mcmeView.xyz) : 1.0e9);\n"
                + "        blockColor.rgb = mcmeDrawFireEye(blockColor.rgb, mcmeView.xyz, mcmeDistance, 1.0 / exposure);\n"
                + "    }\n"
                + "    #endif\n"
                + "\n", true);
        Path particles = shaders.resolve("common/solid_blocks_vertex.glsl");
        Path textured = shaders.resolve("world0/gbuffers_textured.vsh");
        if (!isFile(particles) || !isFile(textured) || !read(textured).contains("GBUFFER_TEXTURED")) {
            throw new Unsupported("MakeUp's particles have moved");
        }
        edits.put(comp, text);
        edits.put(vertex, dropEyeFaces(shaders, vertex, edits));
        edits.put(particles, dropFarParticles(shaders, particles, edits, null, "GBUFFER_TEXTURED"));
        return edits;
    }

    /**
     * Mellow: faces dropped in program/gbuffers_water.vsh; the eye drawn in a
     * pass of its own, composite1 - its composites start at 2, with bloom - over
     * colortex0, its linear scene, with its fixed EXPOSURE.
     */
    static Map<Path, String> mellow(Path shaders, Eye eye) throws IOException, Unsupported {
        Path water = shaders.resolve("program/gbuffers_water.vsh"), fin = shaders.resolve("program/composite8.fsh");
        Path settings = shaders.resolve("lib/settings.glsl");
        if (!(isFile(water) && isFile(fin) && isFile(settings))) {
            throw new Unsupported("not Mellow");
        }
        Path waterFsh = shaders.resolve("program/gbuffers_water.fsh");
        if (!read(fin).contains("Color.rgb *= EXPOSURE;") || !isFile(waterFsh) || !read(waterFsh).contains("DRAWBUFFERS:0")) {
            throw new Unsupported("Mellow's scene buffer or exposure have changed");
        }
        Map<Path, String> edits = newPass(shaders, 1, 0, "1.0 / EXPOSURE", true, eye, "#include \"/lib/settings.glsl\"", "world0");
        Path particles = shaders.resolve("program/gbuffers_basic.vsh");
        if (!isFile(particles)) {
            throw new Unsupported("Mellow's particles have moved");
        }
        edits.put(water, dropEyeFaces(shaders, water, edits));
        edits.put(particles, dropFarParticles(shaders, particles, edits));
        return edits;
    }

    /**
     * Complementary - Reimagined, Unbound and edits such as Spooklementary:
     * faces dropped in program/gbuffers_water.glsl's vertex shader; the eye
     * drawn in a pass of its own, composite2 - after composite1's refraction,
     * reflections, volumetric light and fog, before composite3's blur and the
     * bloom - over colortex0, its linear scene.
     */
    static Map<Path, String> complementary(Path shaders, Eye eye) throws IOException, Unsupported {
        Path water = shaders.resolve("program/gbuffers_water.glsl"), comp1 = shaders.resolve("program/composite1.glsl");
        if (!(isFile(water) && isFile(comp1) && isFile(shaders.resolve("program/composite3.glsl")))) {
            throw new Unsupported("not Complementary");
        }
        String marker = "//////////Vertex Shader//////////";
        if (!read(water).contains(marker) || !read(comp1).contains("/* DRAWBUFFERS:0 */")) {
            throw new Unsupported("Complementary's water or composite1 have changed");
        }
        Map<Path, String> edits = newPass(shaders, 2, 0, "1.0", true, eye);
        Path particles = shaders.resolve("program/gbuffers_textured.glsl");
        if (!isFile(particles) || !read(particles).contains(marker)) {
            throw new Unsupported("Complementary's particles have moved");
        }
        edits.put(water, dropEyeFaces(shaders, water, edits, marker));
        edits.put(particles, dropFarParticles(shaders, particles, edits, marker, null));
        return edits;
    }

    /**
     * BSL (v10, and edits): faces dropped in program/gbuffers_water.glsl's
     * vertex shader; the eye drawn at the end of program/composite.glsl - its
     * one composite always on, after fog, the translucents and the distant
     * terrain mods' terrain, before its light shafts and bloom - in its linear
     * colours, by its fixed exposure (exp2(2 + EXPOSURE), in composite5).
     */
    static Map<Path, String> bsl(Path shaders, Eye eye) throws IOException, Unsupported {
        Path program = shaders.resolve("program");
        Path water = program.resolve("gbuffers_water.glsl"), comp = program.resolve("composite.glsl"), tonemap = program.resolve("composite5.glsl");
        Path particles = program.resolve("gbuffers_textured.glsl");
        for (Path p : new Path[]{water, comp, tonemap, particles}) {
            if (!isFile(p)) {
                throw new Unsupported("not BSL");
            }
        }
        String marker = "//Vertex Shader//";
        String text = read(comp);
        if (!read(water).contains(marker) || !read(particles).contains(marker) || !read(tonemap).contains("color *= exp2(2.0 + EXPOSURE);")
                || !search("vec4 viewPos = gbufferProjectionInverse \\* \\(screenPos \\* 2\\.0 - 1\\.0\\);", text)
                || !search("color\\.rgb \\*= color\\.rgb;", text)) {
            throw new Unsupported("BSL's composite, exposure or water have changed");
        }
        Map<Path, String> edits = new LinkedHashMap<>();
        String name = comp.getFileName().toString();
        String main = "void main\\(\\) \\{\\s*\\n\\s*vec4 color = texture2D\\(colortex0, texCoord\\);\\s*\\n\\s*float z0 = ";
        text = insert(text, main, name, "// MCME: the fire eye, at its block (patch_shaderpack.py)\n"
                + "#ifdef OVERWORLD\n"
                + drawDefines(eye, true) + "#include \"/lib/mcme/fire_eye_draw.glsl\"\n"
                + "#endif\n"
                + "\n", true);
        text = declareUniforms(shaders, comp, text, text.indexOf("// MCME: the fire eye, at its block"), new String[][]{
                {"ivec3", "cameraPositionInt"}, {"vec3", "cameraPositionFract"}, {"float", "far"},
                {"mat4", "gbufferModelViewInverse"}, {"float", "frameTimeCounter"}}, edits);
        text = insert(text, "[ \\t]*/\\*DRAWBUFFERS:01\\*/", name, "\t// MCME: the fire eye, after fog, the translucents and the distant terrain\n"
                + "\t// mods' terrain (viewPos is theirs where they are nearest), before the\n"
                + "\t// light shafts and bloom\n"
                + "\t#ifdef OVERWORLD\n"
                + "\t{\n"
                + "\t\tfloat mcmeDistance = z0 < 1.0 ? length(viewPos.xyz) : 1.0e9;\n"
                + "\t\t#ifdef DISTANT_HORIZONS\n"
                + "\t\tif (z0 >= 1.0 && dhZ0 < 1.0) mcmeDistance = length(viewPos.xyz);\n"
                + "\t\t#endif\n"
                + "\t\t#ifdef VOXY\n"
                + "\t\tif (z0 >= 1.0 && vxZ0 < 1.0) mcmeDistance = length(viewPos.xyz);\n"
                + "\t\t#endif\n"
                + "\t\tcolor.rgb = mcmeDrawFireEye(color.rgb, viewPos.xyz, mcmeDistance, 1.0 / exp2(2.0 + EXPOSURE));\n"
                + "\t}\n"
                + "\t#endif\n"
                + "\n", true);
        edits.put(comp, text);
        edits.put(water, dropEyeFaces(shaders, water, edits, marker));
        edits.put(particles, dropFarParticles(shaders, particles, edits, marker, null));
        return edits;
    }

    /**
     * Solas: faces dropped in programs/gbuffers_water.glsl's vertex shader; the
     * eye drawn in a pass of its own, composite4 - after its water fog,
     * volumetric fog and refraction (composite to composite3), before its bloom
     * (composite13) - over colortex0, its linear scene. Its overworld programs
     * are its root's, which its other dimensions fall back to, so the pass is
     * switched off in those.
     */
    static Map<Path, String> solas(Path shaders, Eye eye) throws IOException, Unsupported {
        Path programs = shaders.resolve("programs");
        Path water = programs.resolve("gbuffers_water.glsl"), particles = programs.resolve("gbuffers_textured.glsl");
        Path tonemap = programs.resolve("composite14.glsl"), bloom = programs.resolve("composite13.glsl");
        for (Path p : new Path[]{water, particles, tonemap, bloom, shaders.resolve("composite3.fsh")}) {
            if (!isFile(p)) {
                throw new Unsupported("not Solas");
            }
        }
        String marker = "#ifdef VSH";
        Path comp3 = programs.resolve("composite3.glsl");
        if (!read(water).contains(marker) || !read(particles).contains(marker)
                || !read(tonemap).contains("Uncharted2Tonemap(color * TONEMAP_BRIGHTNESS)")
                || !read(bloom).contains("computeBloom") || !isFile(comp3) || !read(comp3).contains("DRAWBUFFERS:0")) {
            throw new Unsupported("Solas's passes have changed");
        }
        Map<Path, String> edits = newPass(shaders, 4, 0, "1.0", true, eye, "", ".");
        Path props = shaders.resolve("shaders.properties");
        String p = read(props);
        String nl = nl(p);
        edits.put(props, rstrip(p) + nl + nl + "# MCME: the fire eye's pass, in the overworld only (patch_shaderpack.py)" + nl
                + "program.world-1/composite4.enabled=false" + nl + "program.world1/composite4.enabled=false" + nl);
        edits.put(water, dropEyeFaces(shaders, water, edits, marker));
        edits.put(particles, dropFarParticles(shaders, particles, edits, marker, null));
        return edits;
    }

    /**
     * Sildur's Vibrant: faces dropped in gbuffers_water.vsh; the eye drawn at
     * the end of composite1 - after its fog, before TAA - in the linear colours
     * it works in there; final's tonemap is fixed (4.7). Its overworld programs
     * are its root's. Its bloom is taken earlier, in composite, so the eye
     * doesn't bloom. composite1 runs only with one of its effects on, so it is
     * switched on always.
     */
    static Map<Path, String> sildurs(Path shaders, Eye eye) throws IOException, Unsupported {
        Path comp = shaders.resolve("composite1.fsh"), fin = shaders.resolve("final.fsh");
        Path water = shaders.resolve("gbuffers_water.vsh"), particles = shaders.resolve("gbuffers_textured.vsh");
        Path props = shaders.resolve("shaders.properties");
        for (Path p : new Path[]{comp, fin, water, particles, props}) {
            if (!isFile(p)) {
                throw new Unsupported("not Sildur's");
            }
        }
        String text = read(comp);
        Matcher gate = re("(?m)^program\\.composite1\\.enabled=[^\\r\\n]*").matcher(read(props));
        boolean gated = gate.find();
        if (!read(fin).contains("Uncharted2Tonemap(albedo.rgb*4.7)") || !gated
                || !search("vec3 fragpos0 = utilScreenSpace\\(", text) || !search("float depth0 = ", text)) {
            throw new Unsupported("Sildur's composite1 or final have changed");
        }
        Map<Path, String> edits = new LinkedHashMap<>();
        String name = comp.getFileName().toString();
        String main = "void main\\(\\) \\{";
        text = markDeclarations(shaders, comp, text, findOne(text, main, name).start(), names(LOD_UNIFORMS), edits);
        text = insert(text, main, name, "// MCME: the fire eye, at its block (patch_shaderpack.py)\n"
                + resource("/mcme/patch/lod.glsl") + drawDefines(eye, true) + "#include \"/lib/mcme/fire_eye_draw.glsl\"\n"
                + "\n", true);
        text = declareUniforms(shaders, comp, text, text.indexOf("// MCME: the fire eye, at its block"), new String[][]{
                {"ivec3", "cameraPositionInt"}, {"vec3", "cameraPositionFract"}, {"float", "far"},
                {"mat4", "gbufferModelViewInverse"}, {"float", "frameTimeCounter"}}, edits);
        text = insert(text, "[ \\t]*albedo\\.rgb = pow\\(albedo\\.rgb, vec3\\(0\\.454\\)\\);", name, "\t// MCME: the fire eye, after fog, before TAA\n"
                + "\t{\n"
                + "\t\tfloat mcmeDistance = mcmeLodDistance(texcoord.xy, ivec2(gl_FragCoord.xy), depth0 < 1.0 ? length(fragpos0) : 1.0e9);\n"
                + "\t\talbedo.rgb = mcmeDrawFireEye(albedo.rgb, fragpos0, mcmeDistance, 1.0);\n"
                + "\t}\n"
                + "\n", true);
        edits.put(comp, text);
        String p = read(props);
        String nl = nl(p);
        edits.put(props, p.substring(0, gate.start()) + "# MCME: always on, for the fire eye (patch_shaderpack.py)" + nl
                + "program.composite1.enabled=true" + p.substring(gate.end()));
        edits.put(water, dropEyeFaces(shaders, water, edits));
        edits.put(particles, dropFarParticles(shaders, particles, edits));
        return edits;
    }

    /** The recipes, in the order they are tried, by name. */
    static final Map<String, Recipe> RECIPES = new LinkedHashMap<>();

    static {
        RECIPES.put("Bliss", ShaderPackPatcher::bliss);
        RECIPES.put("MakeUp", ShaderPackPatcher::makeup);
        RECIPES.put("Mellow", ShaderPackPatcher::mellow);
        RECIPES.put("BSL", ShaderPackPatcher::bsl);
        RECIPES.put("Complementary", ShaderPackPatcher::complementary);
        RECIPES.put("Solas", ShaderPackPatcher::solas);
        RECIPES.put("Sildur's", ShaderPackPatcher::sildurs);
    }

    // ---------------------------------------------------------------- main

    /**
     * Patches the shader pack whose shaders/ folder is in root, in place: adds
     * lib/mcme/ and applies the first recipe that takes it. The recipe's name;
     * NotSupported, with each recipe's refusal, if none does - root is then
     * left with lib/mcme/ in it.
     */
    public static String patch(Path root, Eye eye) throws IOException, NotSupported {
        Path shaders = root.resolve("shaders").toAbsolutePath().normalize();
        Path lib = shaders.resolve("lib/mcme");
        Files.createDirectories(lib);
        for (Map.Entry<String, String> include : eye.includes.entrySet()) {
            write(lib.resolve(include.getKey()), include.getValue());
        }
        write(lib.resolve("fire_eye_face.glsl"), resource("/mcme/patch/fire_eye_face.glsl"));
        write(lib.resolve("fire_eye_draw.glsl"), resource("/mcme/patch/fire_eye_draw.glsl"));

        Map<String, String> refusals = new LinkedHashMap<>();
        for (Map.Entry<String, Recipe> recipe : RECIPES.entrySet()) {
            Map<Path, String> edits;
            try {
                edits = recipe.getValue().apply(shaders, eye);
            } catch (Unsupported e) {
                refusals.put(recipe.getKey(), e.getMessage());
                continue;
            }
            for (Map.Entry<Path, String> edit : edits.entrySet()) {
                write(edit.getKey(), edit.getValue());
            }
            return recipe.getKey();
        }
        throw new NotSupported(refusals);
    }

    /**
     * For checking this against patch_shaderpack.py: patches a shader pack
     * folder (holding shaders/) in place, as the script would its copy.
     *
     *     java -cp MCME-Installer.jar net.hypercubemc.iris_installer.shaders.ShaderPackPatcher folder [X Y Z]
     */
    public static void main(String[] args) throws Exception {
        Eye eye = Eye.bundled();
        if (args.length == 4) {
            eye = new Eye(eye.includes, Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        }
        try {
            System.out.println("patched " + args[0] + " (" + patch(Paths.get(args[0]), eye) + ")");
        } catch (NotSupported e) {
            System.out.println("not a supported shader pack:");
            for (Map.Entry<String, String> refusal : e.refusals.entrySet()) {
                System.out.println("  " + refusal.getKey() + ": " + refusal.getValue());
            }
            System.exit(1);
        }
    }
}
