package org.nemesis.registration;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import org.nemesis.registration.FontsColorsBuilder.Fc;
import org.nemesis.registration.FontsColorsBuilder.MultiThemeFontsColorsBuilder;
import static org.nemesis.registration.LanguageRegistrationProcessor.ANNO;
import org.nemesis.registration.api.LayerGeneratingDelegate;
import org.nemesis.registration.utils.AnnotationUtils;
import org.openide.filesystems.annotations.LayerBuilder;
import org.xml.sax.SAXException;

/**
 *
 * @author Tim Boudreau
 */
public class LanguageFontsColorsProcessor extends LayerGeneratingDelegate {

    static final String SEMANTIC_HIGHLIGHTING_PKG = "org.nemesis.antlr.spi.language.highlighting.semantic";
    static final String SEMANTIC_HIGHLIGHTING_NAME = "HighlighterKeyRegistration";
    static final String SEMANTIC_HIGHLIGHTING_ANNO = SEMANTIC_HIGHLIGHTING_PKG + "." + SEMANTIC_HIGHLIGHTING_NAME;

    @Override
    protected boolean processFieldAnnotation(VariableElement field, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        log("LanguageFontsColorsProcessor processField " + mirror.getAnnotationType());
        if (!mirror.getAnnotationType().toString().equals(SEMANTIC_HIGHLIGHTING_ANNO)) {
            log("  - wrong anntation type - skipping - for not " + SEMANTIC_HIGHLIGHTING_ANNO);
            return true;
        }
        // This will be a HighlighterKeyRegistration
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        if (mimeType == null) {
            log(" - no mime type");
            return false;
        }
        AnnotationMirror coloration = utils().annotationValue(mirror, "colors", AnnotationMirror.class);
        if (coloration != null) {
            return processSemanticRegistrationColoringInfoForFileGeneration(mimeType, coloration, field);
        } else {
            log("  no coloration, can't do anything");
        }
        return true;
    }

    @Override
    protected boolean processTypeAnnotation(TypeElement on, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (mirror.getAnnotationType().toString().equals(ANNO)) {
            return processLanguageRegistration(on, mirror, roundEnv);
        } else {
            log("  - wrong anntation type - skipping - for not " + ANNO);
        }
        return false;
    }

    private boolean processLanguageRegistration(TypeElement on, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        // This will be an AntlrLanguageRegistration
        List<AnnotationMirror> tokenCategories = utils().annotationValues(mirror, "categories", AnnotationMirror.class);

        TypeMirror tokenCategorizerClass = utils().typeForSingleClassAnnotationMember(mirror, "tokenCategorizer");
        String prefix = utils().annotationValue(mirror, "name", String.class);
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        String tokenClassName = LanguageRegistrationProcessor.willHandleCategories(tokenCategories, tokenCategorizerClass, prefix, utils());
        if (tokenClassName == null) {
            // Nothing specified - will use the heuristic categorizer - nothing to do
            return true;
        }
        processLanguageRegistrationColoringInfoForFileGeneration(mimeType, prefix, tokenCategories, mirror, on);
        return false;
    }

    private static List<Integer> ints(AnnotationMirror mir, String attribute, AnnotationUtils utils) {
        List<Integer> result = utils.annotationValues(mir, attribute, Integer.class);
        if (result == null) {
            result = Collections.emptyList();
        }
        return result;
    }

    private static class ColoringProxy implements Iterable<String> {

        List<Integer> fg;
        List<Integer> bg;
        List<Integer> und;
        List<Integer> wave;
        String def;
        boolean bold;
        boolean italic;
        Set<String> themes;
        final String mimeType;
        final String categoryName;

        ColoringProxy(String mimeType, String categoryName, AnnotationMirror coloration, AnnotationUtils utils) {
            this.mimeType = mimeType;
            this.categoryName = categoryName;
            fg = ints(coloration, "fg", utils);
            bg = ints(coloration, "bg", utils);
            und = ints(coloration, "underline", utils);
            wave = ints(coloration, "waveUnderline", utils);
            def = utils.annotationValue(coloration, "derivedFrom", String.class);
            bold = utils.annotationValue(coloration, "bold", Boolean.class, false);
            italic = utils.annotationValue(coloration, "italic", Boolean.class, false);
            themes = new HashSet<>(utils.annotationValues(coloration, "themes", String.class));
            if (themes.isEmpty()) {
                themes = Collections.singleton("NetBeans");
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(categoryName).append(" ").append(mimeType);
            ann("fg", fg(), sb);
            ann("bg", bg(), sb);
            ann("wv", waveUnderline(), sb);
            ann("un", underline(), sb);
            sb.append(" themes=").append(themes);
            if (bold) {
                sb.append(" bold");
            }
            if (italic) {
                sb.append(" italic");
            }
            return sb.toString();
        }

        private void ann(String name, Object o, StringBuilder sb) {
            if (o != null) {
                sb.append(' ').append(name).append('=').append(o);
            }
        }

        @Override
        public Iterator<String> iterator() {
            return themes.iterator();
        }

        private int valueOrDefault(int index, List<Integer> ints, int def) {
            if (ints.size() <= index) {
                return def;
            }
            return ints.get(index);
        }

        public boolean isBold() {
            return bold;
        }

        public boolean isItalic() {
            return italic;
        }

        public ARGBColor fg() {
            return toColor(fg);
        }

        public ARGBColor bg() {
            return toColor(bg);
        }

        public ARGBColor underline() {
            return toColor(und);
        }

        public ARGBColor waveUnderline() {
            return toColor(wave);
        }

        private ARGBColor toColor(List<Integer> ints) {
            if (ints.isEmpty()) {
                return null;
            }
            int r = valueOrDefault(0, ints, 0);
            int g = valueOrDefault(1, ints, 0);
            int b = valueOrDefault(2, ints, 0);
            int a = valueOrDefault(3, ints, 255);
            return new ARGBColor(r, g, b, a);
        }

        public List<String> validate() {
            List<String> errors = new ArrayList<>();
            validateColorArray("fg", fg, errors);
            validateColorArray("bg", bg, errors);
            validateColorArray("underline", und, errors);
            validateColorArray("waveUnderline", wave, errors);
            return errors;
        }

        private String themesString() {
            StringBuilder sb = new StringBuilder();
            for (Iterator<String> it = themes.iterator(); it.hasNext();) {
                sb.append(it.next());
                if (it.hasNext()) {
                    sb.append(", ");
                }
            }
            return sb.toString();
        }

        void updateFC(Fc fc) {
            fc.setBackColor(bg());
            fc.setForeColor(fg());
            fc.setWaveUnderlineColor(waveUnderline());
            fc.setUnderlineColor(underline());
            fc.setDefault(def);
            if (isBold()) {
                fc.setBold();
            }
            if (isItalic()) {
                fc.setItalic();
            }
        }

        private void validateColorArray(String attr, List<Integer> rgba, List<String> errors) {
            if (rgba.isEmpty()) {
                return;
            }
            if (rgba.size() != 3 && rgba.size() != 4) {
                String err = "Colors for attribute " + attr + " on " + categoryName
                        + " of " + mimeType
                        + " for themes " + themesString() + " mis-specified as "
                        + rgba + ".  Colors must have 0 values (unspecified), "
                        + "or 3 RGB values, or 4 RGBA values, but "
                        + rgba.size() + " are present.";
                errors.add(err);
            }
            loop:
            for (int i = 0; i < rgba.size(); i++) {
                if (i < 0 || i > 255) {
                    String name;
                    switch (i) {
                        case 0:
                            name = "red";
                            break;
                        case 1:
                            name = "green";
                            break;
                        case 2:
                            name = "blue";
                            break;
                        case 3:
                            name = "alpha";
                            break;
                        default:
                            break loop; // error already recorded
                    }
                    String err = "Color value for " + name + " of " + attr + " on "
                            + categoryName + " of " + mimeType
                            + " for " + themesString() + " is "
                            + "outside the allowed range of 0 to 255.  Colors "
                            + "must be specified as red,green,blue or "
                            + "red,green,blue,alpha values from 0-255.";
                    errors.add(err);
                }
            }
        }

        public boolean isDefaults() {
            return fg.isEmpty() && bg.isEmpty() && und.isEmpty() && wave.isEmpty() && ("default".equals(def) || "".equals(def))
                    && !bold && !italic && (themes.isEmpty() || Arrays.asList("NetBeans").equals(themes));
        }
    }

    private List<ColoringProxy> processTokenCategories(String mimeType, Element on, List<AnnotationMirror> tokenCategories) throws IOException {
        log("Process token categories {0} on {1}", mimeType, on);
        // NOTE: The list of AnnotationMirrors may either be a list of TokenCategory, or a list of ColoringCategory
        // the attribute names on those classes MUST be kept in sync
        if (tokenCategories == null || tokenCategories.isEmpty()) {
            log("  no categories, give up");
            return Collections.emptyList();
        }
        List<ColoringProxy> result = new ArrayList<>(tokenCategories.size());
        boolean failures = false;
        Set<String> dups = new HashSet<>();
        for (AnnotationMirror category : tokenCategories) {
            List<AnnotationMirror> colorations = utils().annotationValues(category, "colors", AnnotationMirror.class);
            String tokenCategoryName = utils().annotationValue(category, "name", String.class);
            if (colorations == null || colorations.isEmpty()) {
                continue;
            }
            if (tokenCategoryName == null || tokenCategoryName.isEmpty() || "_".equals(tokenCategoryName)) {
                continue;
            }
            if (dups.contains(tokenCategoryName)) {
                utils().fail("Duplicate category name '" + tokenCategoryName + "'", on, category);
                continue;
            }
            for (AnnotationMirror coloration : colorations) {
                ColoringProxy proxy = new ColoringProxy(mimeType, tokenCategoryName, coloration, utils());
                if (proxy.isDefaults()) {
                    continue;
                }
                List<String> errors = proxy.validate();
                if (!errors.isEmpty()) {
                    for (String err : errors) {
                        utils().fail(err, on, coloration);;
                        failures = true;
                    }
                    continue;
                }
                result.add(proxy);
            }
        }
        generateThemeFiles(false, true);
        return result;
    }

    private final Map<String, MultiThemeFontsColorsBuilder> themeBuilderForMimeType = new HashMap<>();

    MultiThemeFontsColorsBuilder themesBuilder(String mimeType) {
        MultiThemeFontsColorsBuilder themeBuilder = themeBuilderForMimeType.get(mimeType);
        if (themeBuilder == null) {
            themeBuilder = FontsColorsBuilder.multiBuilder();
            themeBuilderForMimeType.put(mimeType, themeBuilder);
        }
        return themeBuilder;
    }

    private final Map<String, String> destPathForMimeType = new HashMap<>();
    private final Map<String, String> prefixForMimeType = new HashMap<>();
    private final Map<String, String> sampleForMimeType = new HashMap<>();
    private final Map<String, String> bundleForMimeType = new HashMap<>();
    private final Map<String, String> extForMimeType = new HashMap<>();
    private final Map<String, List<Element>> elementsForMimeType = new HashMap<>();

    private void updateElements(String mimeType, Element el) {
        List<Element> all = elementsForMimeType.get(mimeType);
        if (all == null) {
            all = new ArrayList<>(3);
            elementsForMimeType.put(mimeType, all);
        }
        all.add(el);
    }

    private Element[] elementsForMimeType(String mimeType) {
        List<Element> all = elementsForMimeType.get(mimeType);
        if (all == null) {
            return new Element[0];
        }
        return all.toArray(new Element[all.size()]);
    }

    private void updateSample(String mimeType, String sample) {
        if (sample != null) {
            sampleForMimeType.put(mimeType, sample);
        }
    }

    private void updateBundle(String mimeType, String bundle) {
        if (bundle != null && !bundle.isEmpty()) {
            bundleForMimeType.put(mimeType, bundle);
        }
    }

    String bundle(String mimeType) {
        return bundleForMimeType.getOrDefault(mimeType, "");
    }

    private String updateDestPath(String mimeType, String prefix, Element on) {
        if (prefix != null && !prefix.isEmpty()) {
            prefixForMimeType.put(mimeType, prefix);
        }
        String destPath = destPathForMimeType.getOrDefault(mimeType, "");
        PackageElement pkg = processingEnv.getElementUtils().getPackageOf(on);
        if (pkg != null && !pkg.getQualifiedName().toString().isEmpty()) {
            String dp = pkg.getQualifiedName().toString().replace('.', '/');
            if (destPath.isEmpty() || dp.length() < destPath.length()) {
                destPath = dp;
            }
        }
        if (!destPath.isEmpty()) {
            destPathForMimeType.put(mimeType, destPath);
        }
        return destPath;
    }

    String prefix(String mimeType) {
        String result = prefixForMimeType.get(mimeType);
        if (result == null) {
            int ix = mimeType.lastIndexOf('/');
            if (ix >= 0 && ix < mimeType.length() - 1) {
                result = mimeType.substring(ix + 1);
            } else if (ix < 0) {
                result = mimeType;
            } else {
                result = mimeType.substring(0, mimeType.length() - 1);
            }
        }
        return result;
    }

    String destPath(String mimeType) {
        String destPath = destPathForMimeType.getOrDefault(mimeType, "");
        if (destPath.isEmpty()) {
            return "com/foo/unspecified";
        }
        return destPath;
    }

    void updateExt(String mimeType, String ext) {
        if (ext != null && !ext.isEmpty()) {
            extForMimeType.put(mimeType, ext);
        }
    }

    String extension(String mimeType) {
        String result = extForMimeType.get(mimeType);
        if (result == null) {
            int ix = mimeType.indexOf('/');
            if (ix >= 0) {
                String res = mimeType.substring(ix + 1);
                if (res.startsWith("x-") && res.length() > 2) {
                    res = res.substring(2);
                }
                result = res;
            } else {
                result = mimeType;
            }
        }
        return result;
    }

    /**
     * Handle the colorings specified by @AntlrLanguageRegistration
     * @param mimeType The mime type
     * @param prefix The file name prefix
     * @param tokenCategories The token categories
     * @param registration The annotation they come from
     * @param on The thing annotated
     * @return true if all elements proceessed
     * @throws IOException
     */
    private boolean processLanguageRegistrationColoringInfoForFileGeneration(String mimeType, String prefix, List<AnnotationMirror> tokenCategories, AnnotationMirror registration, Element on) throws IOException {
        List<ColoringProxy> colorings = processTokenCategories(mimeType, on, tokenCategories);
        if (colorings.isEmpty()) {
            log("Empty colorings - give up");
            return true;
        }
        AnnotationMirror dataObject = utils().annotationValue(registration, "file", AnnotationMirror.class);
        updateSample(mimeType, utils().annotationValue(registration, "sample", String.class));
        updateElements(mimeType, on);
        String ext = utils().annotationValue(dataObject, "extension", String.class);
        if (ext != null) {
            updateExt(mimeType, ext);
        }
        updateDestPath(mimeType, prefix, on);
        String bundle = utils().annotationValue(registration, "localizingBundle", String.class, "");
        if (!bundle.isEmpty()) {
            updateBundle(mimeType, bundle);
        }
        addColoringsToBuilder(mimeType, colorings);
        return true;
    }

    /**
     * Process the single category specified by a HighlighterKeyRegistration.
     *
     * @param mimeType The mime type
     * @param category The named coloring
     * @param on The source element it appeared on
     * @return true if all processed
     * @throws IOException
     */
    private boolean processSemanticRegistrationColoringInfoForFileGeneration(String mimeType, AnnotationMirror category, Element on) throws IOException {
        log("processSemanticRegistrationColoringInfoForFileGeneration {0} for {1} on {2}", mimeType, category, on);
        List<ColoringProxy> colorings = processTokenCategories(mimeType, on, Arrays.asList(category));
        if (colorings.isEmpty()) {
            log("Empty colorings - give up");
            return true;
        }
        updateDestPath(mimeType, null, on);
        updateElements(mimeType, on);
        addColoringsToBuilder(mimeType, colorings);
        return true;
    }

    private void addColoringsToBuilder(String mimeType, List<ColoringProxy> colorings) {
        MultiThemeFontsColorsBuilder bldr = themesBuilder(mimeType);
        for (ColoringProxy px : colorings) {
            Fc fc = bldr.add(px.categoryName, px);
            px.updateFC(fc);
            log("   added {0}", fc);
        }
    }

    @Override
    protected boolean onRoundCompleted(Map<AnnotationMirror, Element> processed, RoundEnvironment env) throws IOException {
        log("onRoundCompleted with {0} proc over? {1} error raised? {2}", processed.size(),
                env.processingOver(), env.errorRaised());
        if (env.processingOver() && !env.errorRaised()) {
            for (Map.Entry<String, MultiThemeFontsColorsBuilder> e : themeBuilderForMimeType.entrySet()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generating theme files for " + e.getKey());
                generateThemeFiles(e.getKey(), e.getValue(), true, false);
            }
        } else if (env.processingOver()) {
            utils().warn("Not running fonts-colors generation because error was raised");
        }
        return true;
    }

    private void loadExisting(String themeFilePath, FontsColorsBuilder bldr) throws IOException, SAXException {
        Filer filer = processingEnv.getFiler();
        try {
            FileObject res = filer.getResource(StandardLocation.CLASS_OUTPUT, "", themeFilePath);
            try (InputStream in = res.openInputStream()) {
                bldr.loadExisting(in);
            }
        } catch (FileNotFoundException | NoSuchFileException fnfe) {
            // Okay, did not exist
        }
    }

    private String themeFilePath(String mimeType, String theme) {
        String pfx = prefix(mimeType);
        String destPath = destPath(mimeType);
        String themeFileName = pfx.toLowerCase() + "-" + themeToFileSpec(theme) + "-fontscolors.xml";
        String themeFilePath = destPath + "/" + themeFileName;
        return themeFilePath;
    }

    private boolean generateThemeFiles(boolean saveTheme, boolean saveLayer) throws IOException {
        boolean result = true;
        for (Map.Entry<String, MultiThemeFontsColorsBuilder> e : this.themeBuilderForMimeType.entrySet()) {
            result &= generateThemeFiles(e.getKey(), e.getValue(), saveTheme, saveLayer);
        }
        return result;
    }

    private String capitalize(String s) {
        char[] c = s.toCharArray();
        if (c.length > 0) {
            c[0] = Character.toUpperCase(c[0]);
        }
        return new String(c);
    }

    private void loadExitingProperties(Filer filer, Properties into, String path) throws IOException {
        try {
            FileObject res = filer.getResource(StandardLocation.CLASS_OUTPUT, "", path);
            try (InputStream in = res.openInputStream()) {
                into.load(in);
            }
        } catch (FileNotFoundException | NoSuchFileException ex) {
            // ok
        }
    }

    private boolean generateThemeFiles(String mimeType, MultiThemeFontsColorsBuilder bldr, boolean saveTheme, boolean saveLayer) throws IOException {

        Element[] declaringElements = elementsForMimeType(mimeType);
        String sample = sampleForMimeType.get(mimeType);
        String pfx = prefix(mimeType);
        String destPath = destPath(mimeType);
        String bundle = bundle(mimeType);
        if (bundle.isEmpty() && declaringElements.length > 0) {
            for (Element el : declaringElements) {
                utils().warn("Localizing bundle not specified for colorings on " + mimeType, el);
            }
        }
        Filer filer = processingEnv.getFiler();
        Map<String, String> themeFileResourcePathForTheme = new HashMap<>();
        Map<String, String> themeContentForTheme = new HashMap<>();
        String ext = extension(mimeType);

        String genBundlePackage = destPath;
        String genBundlePath = genBundlePackage + "/_FontColorNames.properties";
        String genBundleDots = genBundlePackage.replace('/', '.') + "._FontColorNames";

        Properties locBundle = new Properties();
        loadExitingProperties(filer, locBundle, genBundlePath);
        for (String name : bldr.names()) {
            locBundle.setProperty(name, capitalize(name));
        }

        bldr.build((theme, fontsColors) -> {
            String themeFilePath = themeFilePath(mimeType, theme);
            themeFileResourcePathForTheme.put(theme, themeFilePath);
            themeContentForTheme.put(theme, fontsColors.toString());
            try {
                if (saveTheme) {
                    loadExisting(themeFilePath, fontsColors);
                    FileObject fo = filer.createResource(StandardLocation.CLASS_OUTPUT, "", themeFilePath, declaringElements);
                    try (OutputStream out = fo.openOutputStream()) {
                        out.write(fontsColors.toString().getBytes(UTF_8));
                    }
                }
            } catch (IOException | SAXException ioe) {
                logException(ioe, true);
            }
        });
        if (saveLayer) {
            LayerBuilder layer = layer(declaringElements);
            themeFileResourcePathForTheme.forEach((theme, pathInJar) -> {
                String layerPath = "Editors/" + mimeType + "/FontsColors/" + theme + "/Defaults/" + theme + ".xml";
                LayerBuilder.File file = layer.file(layerPath);
                file.url("nbres:/" + pathInJar);
                if (!bundle.isEmpty()) {
                    file.stringvalue("SystemFileSystem.localizingBundle", genBundleDots);
                } else {
                    file.stringvalue("SystemFileSystem.localizingBundle", genBundleDots);
                }
                file.write();
            });

            if (sample != null) {
                try {
                    String sampleFilePath = destPath + "/" + pfx.toLowerCase() + "-sample." + ext;
                    FileObject res = filer.createResource(StandardLocation.CLASS_OUTPUT, "", sampleFilePath, declaringElements);
                    try (OutputStream out = res.openOutputStream()) {
                        out.write(sample.getBytes(UTF_8));
                    }
                    String sampleLayerPath = "OptionsDialog/PreviewExamples/" + mimeType;

                    LayerBuilder.File file = layer.file(sampleLayerPath)
                            .url("nbres:/" + sampleFilePath);
//                            .contents(sample);
                    if (!bundle.isEmpty()) {
                        file.stringvalue("SystemFileSystem.localizingBundle", bundle);
                    }
                    file.write();
                } catch (IOException ioe) {
                    logException(ioe, true);
                }
            }
        }
        // Returning true could stop LanguageRegistrationProcessor from being run
        if (saveTheme) {
            FileObject bun = filer.createResource(StandardLocation.CLASS_OUTPUT, "", genBundlePath);
            try (OutputStream out = bun.openOutputStream()) {
                locBundle.store(out, "Generated by " + getClass().getName());
            }
        }

        return false;
    }

    private String themeToFileSpec(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (!Character.isAlphabetic(c) && !Character.isDigit(c)) {
                sb.append('-');
                continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        if (sb.length() == 0) { // weird name?
            sb.append(Long.toString(System.currentTimeMillis(), 36));
        }
        return sb.toString();
    }
}
