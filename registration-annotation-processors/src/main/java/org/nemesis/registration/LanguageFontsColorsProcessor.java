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
import com.mastfrog.annotation.processor.LayerGeneratingDelegate;
import com.mastfrog.annotation.AnnotationUtils;
import javax.annotation.processing.ProcessingEnvironment;
import org.openide.filesystems.annotations.LayerBuilder;
import org.xml.sax.SAXException;
import static org.nemesis.registration.LanguageRegistrationProcessor.REGISTRATION_ANNO;

/**
 *
 * @author Tim Boudreau
 */
public class LanguageFontsColorsProcessor extends LayerGeneratingDelegate {

    static final String SEMANTIC_HIGHLIGHTING_PKG = "org.nemesis.antlr.spi.language.highlighting.semantic";
    static final String SEMANTIC_HIGHLIGHTING_NAME = "HighlighterKeyRegistration";
    static final String GROUP_SEMANTIC_HIGHLIGHTING_NAME = "HighlighterKeyRegistrations";
    static final String SEMANTIC_HIGHLIGHTING_ANNO = SEMANTIC_HIGHLIGHTING_PKG + "." + SEMANTIC_HIGHLIGHTING_NAME;
    static final String GROUP_SEMANTIC_HIGHLIGHTING_ANNO = SEMANTIC_HIGHLIGHTING_PKG + "." + GROUP_SEMANTIC_HIGHLIGHTING_NAME;

    @Override
    protected void onInit(ProcessingEnvironment env, AnnotationUtils utils) {
        AnnotationUtils.forceLogging();
        super.onInit(env, utils); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected boolean processFieldAnnotation(VariableElement var, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        boolean result = true;
        if (GROUP_SEMANTIC_HIGHLIGHTING_ANNO.equals(mirror.getAnnotationType().toString())) {
            for (AnnotationMirror individual : utils().annotationValues(mirror, "value", AnnotationMirror.class)) {
                result &= processSingleFieldAnnotation(var, individual, roundEnv);
            }
        } else {
            result = processSingleFieldAnnotation(var, mirror, roundEnv);
        }
        return result;
    }

    protected boolean processSingleFieldAnnotation(VariableElement field, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
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
        return false;
    }

    @Override
    protected boolean processTypeAnnotation(TypeElement on, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        if (mirror.getAnnotationType().toString().equals(REGISTRATION_ANNO)) {
            log("LanguageFontsColorsProcessor processTypeAnnotation {0}", on.getSimpleName().toString());
            return processLanguageRegistration(on, mirror, roundEnv);
        } else {
            log("  - wrong anntation type - skipping - for not " + REGISTRATION_ANNO);
        }
        return false;
    }

    private boolean processLanguageRegistration(TypeElement on, AnnotationMirror mirror, RoundEnvironment roundEnv) throws Exception {
        // This will be an AntlrLanguageRegistration
        List<AnnotationMirror> tokenCategories = utils().annotationValues(mirror, "categories", AnnotationMirror.class);

        TypeMirror tokenCategorizerClass = utils().typeForSingleClassAnnotationMember(mirror, "tokenCategorizer");
        String prefix = utils().annotationValue(mirror, "name", String.class);
        String mimeType = utils().annotationValue(mirror, "mimeType", String.class);
        String tokenClassName = LanguageRegistrationDelegate.willHandleCategories(tokenCategories, tokenCategorizerClass, prefix, utils());
        if (tokenClassName == null) {
            // Nothing specified - will use the heuristic categorizer - nothing to do
            return true;
        }
        processLanguageRegistrationColoringInfoForFileGeneration(mimeType, prefix, tokenCategories, mirror, on);
        return false;
    }

    static List<Integer> ints(AnnotationMirror mir, String attribute, AnnotationUtils utils) {
        List<Integer> result = utils.annotationValues(mir, attribute, Integer.class);
        if (result == null) {
            result = Collections.emptyList();
        }
        return result;
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
        log("Calling generate theme files with save layer true for {0}");
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
        String destPath = destPathForMimeType.getOrDefault(mimeType, "");
        if (!destPath.isEmpty()) {
            return destPath;
        }
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
        mimeType = mimeType.toLowerCase();
        int ix = mimeType.indexOf('/');
        if (ix > 0 && ix < mimeType.length() - 1) {
            mimeType = mimeType.substring(ix + 1);
        }
        if (mimeType.length() > 2 && mimeType.startsWith("x-")) {
            mimeType = mimeType.substring(2);
        }
        return mimeType.replace('/', '-').replace('+', '-');
    }

    String destPath(String mimeType) {
        return destPathForMimeType.getOrDefault(mimeType, "com/foo/unspecified");
    }

    void updateExt(String mimeType, String ext) {
        if (ext != null && !ext.isEmpty()) {
            extForMimeType.put(mimeType, ext);
        }
    }

    String extension(String mimeType) {
        String result = extForMimeType.get(mimeType);
        if (result == null) {
            return prefix(mimeType).toLowerCase();
        }
        return result;
    }

    /**
     * Handle the colorings specified by @AntlrLanguageRegistration
     *
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
            log("   added to {0}: {1}", mimeType, fc);
        }
    }

    @Override
    protected boolean onRoundCompleted(Map<AnnotationMirror, Element> processed, RoundEnvironment env) throws IOException {
        log("onRoundCompleted with {0} proc over? {1} error raised? {2} entries {3}", processed.size(),
                env.processingOver(), env.errorRaised(), themeBuilderForMimeType.keySet());
        if (env.processingOver() && !env.errorRaised()) {
            for (Map.Entry<String, MultiThemeFontsColorsBuilder> e : themeBuilderForMimeType.entrySet()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generating theme files for " + e.getKey());
                generateThemeFiles(e.getKey(), e.getValue(), true, false);
            }
        } else if (env.processingOver()) {
            utils().warn("Not running fonts-colors generation because error was raised");
        } else {
            log("Not running layer generation because round completed is false");
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
        log("generateThemeFiles0 (saveTheme: {0}, saveLayer: {1} for {2})", saveTheme, saveLayer, themeBuilderForMimeType.keySet());
        boolean result = true;
        for (Map.Entry<String, MultiThemeFontsColorsBuilder> e : this.themeBuilderForMimeType.entrySet()) {
            log("  generate for {0}", e.getKey());
            result &= generateThemeFiles(e.getKey(), e.getValue(), saveTheme, saveLayer);
        }
        return result;
    }

    public static String capitalize(CharSequence s) {
        StringBuilder sb = new StringBuilder();
        int max = s.length();
        boolean capitalizeNext = true;
        for (int i = 0; i < max; i++) {
            char c = s.charAt(i);
            if (capitalizeNext && Character.isLetter(c)) {
                c = Character.toUpperCase(c);
                sb.append(c);
                capitalizeNext = false;
                continue;
            }
            if (Character.isUpperCase(c)) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(c);
                capitalizeNext = false;
                continue;
            }
            switch (c) {
                case '-':
                case '_':
                case '.':
                case ' ':
                    capitalizeNext = true;
                    sb.append(' ');
                    continue;
            }
            capitalizeNext = false;
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
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

    private final Set<String> writtenSamples = new HashSet<>();

    private boolean generateThemeFiles(String mimeType, MultiThemeFontsColorsBuilder bldr, boolean saveTheme, boolean saveLayer) throws IOException {
        log("generateThemeFiles1 ({2} saveTheme: {0}, saveLayer: {1})", saveTheme, saveLayer, mimeType);
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
        log("Got past load existing properties {0}", locBundle);
        for (String name : bldr.names()) {
            locBundle.setProperty(name, capitalize(name));
        }

        String sampleLayerPath = "OptionsDialog/PreviewExamples/" + mimeType;
        String sampleFilePath = destPath + "/" + pfx.toLowerCase() + "-sample." + ext;
        if (sample != null && !writtenSamples.contains(mimeType)) {
            try {
                FileObject res = filer.createResource(StandardLocation.CLASS_OUTPUT, "", sampleFilePath, declaringElements);
                try (OutputStream out = res.openOutputStream()) {
                    out.write(sample.getBytes(UTF_8));
                }
                writtenSamples.add(mimeType);
            } catch (IOException ioe) {
                logException(ioe, true);
            }
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
                LayerBuilder.File file = layer.file(sampleLayerPath)
                        .url("nbres:/" + sampleFilePath);
                if (!bundle.isEmpty()) {
                    file.stringvalue("SystemFileSystem.localizingBundle", bundle);
                }
                file.write();
                log("generating layer {0} with {1}", layer, themeFileResourcePathForTheme);

                themeFileResourcePathForTheme.forEach((theme, pathInJar) -> {
                    String layerPath = "Editors/" + mimeType + "/FontsColors/" + theme + "/Defaults/" + theme + ".xml";
                    LayerBuilder.File themeLayerFile = layer.file(layerPath);
                    themeLayerFile.url("nbres:/" + pathInJar);
                    if (!bundle.isEmpty()) {
                        themeLayerFile.stringvalue("SystemFileSystem.localizingBundle", genBundleDots);
                    } else {
                        themeLayerFile.stringvalue("SystemFileSystem.localizingBundle", genBundleDots);
                    }
                    themeLayerFile.write();
                });
//            }, declaringElements);
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
