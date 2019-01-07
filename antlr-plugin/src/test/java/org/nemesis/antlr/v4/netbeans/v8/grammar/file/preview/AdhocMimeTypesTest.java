package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes.ComplexMimeTypeMapper;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocMimeTypesTest {

    @Before
    public void before() throws Throwable {
        AdhocMimeTypes._reinitAndDeleteCache();
    }

    @Test
    public void testMimeTypeRegistrations() throws IOException, InvalidMimeTypeRegistrationException {
        Path grammar = Paths.get("/home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/1.2.1/ANTLR4PLGNB82/test/unit/src/org/nemesis/antlr/v4/netbeans/v8/grammar/file/tool/NestedMapGrammar.g4");
        String mime = AdhocMimeTypes.mimeTypeForPath(grammar);
        String ext = AdhocMimeTypes.fileExtensionFor(mime);
        Path extPath = Paths.get("com/foo/Bar." + ext);
        assertEquals(msg.toString(), mime, AdhocMimeTypes.mimeTypeForPath(extPath));

        AdhocMimeTypes.registerFileNameExtension("map", mime);
        assertTrue(msg.toString(), AdhocMimeTypes.isRegisteredExtension("map"));
        assertTrue(msg.toString(), AdhocMimeTypes.allExtensionsForMimeType(mime).contains("map"));
        assertTrue(msg.toString(), AdhocMimeTypes.allExtensionsForMimeType(mime).contains(ext));
        assertTrue("EXTS: " + AdhocMimeTypes.getRegisteredFileNameExtensions() + "; " + msg.toString(),
                AdhocMimeTypes.getRegisteredFileNameExtensions().contains("map"));

        Path mapPath = Paths.get("/com/foo/Baz.map");
        assertEquals(msg.toString(), mime, AdhocMimeTypes.mimeTypeForPath(mapPath));
        assertEquals(msg.toString(), ext, AdhocMimeTypes.fileExtensionFor(mime));

        AdhocMimeTypes.registerFileNameExtension("foo", mime);
        assertTrue(msg.toString(), AdhocMimeTypes.isRegisteredExtension("map"));
        assertTrue(msg.toString(), AdhocMimeTypes.allExtensionsForMimeType(mime).contains("map"));
        assertTrue(msg.toString(), AdhocMimeTypes.allExtensionsForMimeType(mime).contains(ext));
        assertTrue("EXTS: " + AdhocMimeTypes.getRegisteredFileNameExtensions() + "; " + msg.toString(),
                AdhocMimeTypes.getRegisteredFileNameExtensions().contains("map"));

        assertTrue(msg.toString(), AdhocMimeTypes.isRegisteredExtension("foo"));
        assertTrue(msg.toString(), AdhocMimeTypes.allExtensionsForMimeType(mime).contains("foo"));
        assertTrue(msg.toString(), AdhocMimeTypes.allExtensionsForMimeType(mime).contains(ext));
        assertTrue("EXTS: " + AdhocMimeTypes.getRegisteredFileNameExtensions() + "; " + msg.toString(),
                AdhocMimeTypes.getRegisteredFileNameExtensions().contains("foo"));

        Path path = Paths.get("/tmp/x.foo");
        assertEquals(msg.toString(), mime, AdhocMimeTypes.mimeTypeForPath(path));
        assertEquals(msg.toString(), mime, AdhocMimeTypes.mimeTypeForPath(mapPath));
        String old=msg.toString();

        Set<String> all = AdhocMimeTypes.allExtensionsForMimeType(mime);
        System.out.println("\nNOW REINIT");
        AdhocMimeTypes._reinit();
        assertEquals(msg.toString(), all, AdhocMimeTypes.allExtensionsForMimeType(mime));
        assertEquals(msg.toString(), ext, AdhocMimeTypes.fileExtensionFor(mime));
        
        assertEquals(mime, AdhocMimeTypes.EXTENSIONS_REGISTRY.mimeTypeForExt("map"));
        assertEquals(mime, AdhocMimeTypes.EXTENSIONS_REGISTRY.mimeTypeForExt("foo"));
        assertTrue(AdhocMimeTypes.EXTENSIONS_REGISTRY.isRegisteredExtension("map"));
        assertTrue(AdhocMimeTypes.EXTENSIONS_REGISTRY.isRegisteredExtension("foo"));
        assertEquals("Wrong result for " + mapPath + ": " + msg.toString(), mime, AdhocMimeTypes.mimeTypeForPath(mapPath));
        assertEquals(msg.toString(), mime, AdhocMimeTypes.mimeTypeForFileExtension(ext));
        assertEquals(msg.toString(), mime, AdhocMimeTypes.mimeTypeForFileExtension("map"));
        assertEquals(msg.toString(), mime, AdhocMimeTypes.mimeTypeForFileExtension("foo"));
        assertTrue("EXTS: " + AdhocMimeTypes.getRegisteredFileNameExtensions() + "; " + msg.toString(),
                AdhocMimeTypes.getRegisteredFileNameExtensions().contains("foo"));
        assertEquals(old, msg.toString());

        AdhocMimeTypes._reinitAndDeleteCache();
        assertFalse(msg.toString(), AdhocMimeTypes.isRegisteredExtension("foo"));
        assertFalse(msg.toString(), AdhocMimeTypes.allExtensionsForMimeType(mime).contains("foo"));
        assertFalse(msg.toString(), AdhocMimeTypes.allExtensionsForMimeType(mime).contains("map"));
        assertFalse("EXTS: " + AdhocMimeTypes.getRegisteredFileNameExtensions() + "; " + msg.toString(),
                AdhocMimeTypes.getRegisteredFileNameExtensions().contains("foo"));

    }

    private final Msg msg = new Msg();

    static class Msg {

        public String toString() {
            return AdhocMimeTypes.EXTENSIONS_REGISTRY.toString();
        }
    }

    @Test
    public void testProblematicPathCache() throws Throwable {
        ComplexMimeTypeMapper lm = new ComplexMimeTypeMapper();
        Path path = Paths.get("/home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/1.2.1/ANTLR4PLGNB82/test/unit/src/org/nemesis/antlr/v4/netbeans/v8/grammar/file/tool/NestedMapGrammar.g4");
        String mt = lm.mimeTypeForPath(path);
        Path path2 = Paths.get("/path/to/SomeGrammar.g4");
        String mt2 = lm.mimeTypeForPath(path2);
        assertNotNull(FileUtil.getConfigFile(ComplexMimeTypeMapper.SFS_PATH));
        // Now wipe the cache and make sure we get the same results
        lm.reinit(true);
        assertNull(FileUtil.getConfigFile(ComplexMimeTypeMapper.SFS_PATH));
        assertEquals(mt, lm.mimeTypeForPath(path));
        assertEquals(mt2, lm.mimeTypeForPath(path2));
        assertEquals(path, lm.pathForMimeType(mt));
        assertEquals(path2, lm.pathForMimeType(mt2));
        // Now DON'T delete the cache from disk and make sure the loaded
        // results match the ones from before
        assertNotNull(FileUtil.getConfigFile(ComplexMimeTypeMapper.SFS_PATH));
        lm.reinit(false);
        assertNotNull(FileUtil.getConfigFile(ComplexMimeTypeMapper.SFS_PATH));
        assertEquals(path, lm.pathForMimeType(mt));
        assertEquals(path2, lm.pathForMimeType(mt2));
    }

    @Test
    public void testOddCharactersInFileNames() throws IOException {
        Path path = Paths.get("/home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/1.2.1/ANTLR4PLGNB82/test/unit/src/org/nemesis/antlr/v4/netbeans/v8/grammar/file/tool/NestedMapGrammar.g4");
        String mt = AdhocMimeTypes.mimeTypeForPath(path);
        testOne(mt, path);
    }

    @Test
    public void ensureMimePathContract() {
        MimePath.parse("text/x-foo--two-dashes");
        MimePath.parse("text/x-foo--two__underscores");
    }

    private static final MimeToPathTestSet[] SETS = new MimeToPathTestSet[]{
        new MimeToPathTestSet("text/l-nestedmapgrammar-t19h30jbkv", "/home/tim/work/foreign/ANTLR4-Plugins-for-NetBeans/1.2.1/ANTLR4PLGNB82/test/unit/src/org/nemesis/antlr/v4/netbeans/v8/grammar/file/tool/NestedMapGrammar.g4", "alr_l-nestedmapgrammar-t19h30jbkv", "text/x-nestedmapgrammar-t19h30jbkv"),
        new MimeToPathTestSet("text/a-somegrammar$$path$$to$$SomeGrammar", "/path/to/SomeGrammar.g4", "alr_a-somegrammar$$path$$to$$SomeGrammar", "text/a-somegrammar"),
        new MimeToPathTestSet("text/l-time-lgycygh9dv", "/this/is/too/long/a/path/for/mime/path/which/limits/mime/types/to/one/twenty/eight/characters/but/this/path/goes/on/and/on/and/for/quite/a/while/and/eventually/there/is/a/file/but/not/for/a/long/time.g4", "alr_l-time-lgycygh9dv", "text/x-time-lgycygh9dv"),
        new MimeToPathTestSet("text/l-vlench-nm026olk", "/non-ascii/válečných.g4", "alr_l-vlench-nm026olk", "text/x-válečných-nm026olk"),
        new MimeToPathTestSet("text/x-antlr-n4mvuzwg", "/non-ascii/朝日新聞.g4", "alr_x-antlr-n4mvuzwg", "text/x-朝日新聞-n4mvuzwg"),
        new MimeToPathTestSet("text/x-antlr-nnz4l3n", "/non-ascii/слова.g4", "alr_x-antlr-nnz4l3n", "text/x-слова-nnz4l3n"),
        new MimeToPathTestSet("text/x-antlr-nf5u3i6f", "/non-ascii/šůčářížý.g4", "alr_x-antlr-nf5u3i6f", "text/x-šůčářížý-nf5u3i6f"),
        new MimeToPathTestSet("text/l-grammar-J7qqtune", "/Users/Joe Blow/grammar.g4", "alr_l-grammar-J7qqtune", "text/x-grammar-J7qqtune"),
        new MimeToPathTestSet("text/a-grammar$$Users$$Joe-Blow$$grammar", "/Users/Joe-Blow/grammar.g4", "alr_a-grammar$$Users$$Joe-Blow$$grammar", "text/a-grammar"),
        new MimeToPathTestSet("text/a-grammar$$Users$$Joe^Blow$$grammar", "/Users/Joe^Blow/grammar.g4", "alr_a-grammar$$Users$$Joe^Blow$$grammar", "text/a-grammar"),
        new MimeToPathTestSet("text/l-grammar-Jfl0s2zx", "/Users/Joe%20Blow/grammar.g4", "alr_l-grammar-Jfl0s2zx", "text/x-grammar-Jfl0s2zx"),
        new MimeToPathTestSet("text/a-1234$$numeric$$name$$1234", "/numeric/name/1234.g4", "alr_a-1234$$numeric$$name$$1234", "text/a-1234"),
        new MimeToPathTestSet("text/x-antlr-nfycgg9", "/numeric/name/^#&@.g4", "alr_x-antlr-nfycgg9", "text/x-^#&@-nfycgg9"),
        new MimeToPathTestSet("text/l-xx-ng83xa6", "/numeric/name/x\\\\\\\\x.g4", "alr_l-xx-ng83xa6", "text/x-x\\\\\\\\x-ng83xa6"),
        new MimeToPathTestSet("text/x-antlr-l1cattfyyp", "/weird/long/GrammarFileWhichHasASimplyUngodlyHugeRidiculousNameForNoEarthlyReasonICanImagineWhatCouldYouPossiblyHaveBeenThinkingByDoingThatAreYouInsane.g4", "alr_x-antlr-l1cattfyyp", "text/x-grammarfilewhichhasasimp...-l1cattfyyp"),
        new MimeToPathTestSet("text/l-line_break-nu7mkbdh", "/numeric/name/line\nbreak.g4", "alr_l-line_break-nu7mkbdh", "text/x-line\nbreak-nu7mkbdh"),
        new MimeToPathTestSet("text/l-tab_break-nmer8m2a", "/numeric/name/tab\tbreak.g4", "alr_l-tab_break-nmer8m2a", "text/x-tab\tbreak-nmer8m2a"),
        new MimeToPathTestSet("text/l-carriage_return-n1xokya9s", "/numeric/name/carriage\rreturn.g4", "alr_l-carriage_return-n1xokya9s", "text/x-carriage\rreturn-n1xokya9s"),
        new MimeToPathTestSet("text/l-parens-nfdq4mkm", "/numeric/name/(parens).g4", "alr_l-parens-nfdq4mkm", "text/x-(parens)-nfdq4mkm"),
        new MimeToPathTestSet("text/l-brackets-u511qga", "/street/čeletna/[brackets].g4", "alr_l-brackets-u511qga", "text/x-[brackets]-u511qga"),
        new MimeToPathTestSet("text/x-antlr-mesyor1", "/street/main/$$$$.g4", "alr_x-antlr-mesyor1", "text/x-$$$$-mesyor1"),
        new MimeToPathTestSet("text/l-bell-fd4ggcu", "/com/foo/bell\b.g4", "alr_l-bell-fd4ggcu", "text/x-bell\b-fd4ggcu"),
        new MimeToPathTestSet("text/l-dotfile-ff9ebe65", "/com/foo/.dotfile.g4", "alr_l-dotfile-ff9ebe65", "text/x-.dotfile-ff9ebe65"),
        new MimeToPathTestSet("text/x-antlr-2awbof", "/č.g4", "alr_x-antlr-2awbof", "text/x-č-2awbof"),
        new MimeToPathTestSet("text/l-stuff-dpfk89t6", "/a/b/č/d/\"stuff\".g4", "alr_l-stuff-dpfk89t6", "text/x-\"stuff\"-dpfk89t6"),
        new MimeToPathTestSet("text/l-stuff-dpfmmj9k", "/a/b/č/d/'stuff'.g4", "alr_l-stuff-dpfmmj9k", "text/x-'stuff'-dpfmmj9k"),
        new MimeToPathTestSet("text/x-antlr-dcu8h6n", "/a/b/č/d/.g4", "alr_x-antlr-dcu8h6n", "text/x--dcu8h6n"),
        new MimeToPathTestSet("text/x-antlr-dcmax1d", "/a/b/c/d/.g4", "alr_x-antlr-dcmax1d", "text/x--dcmax1d"),
        new MimeToPathTestSet("text/x-antlr-1kized", "/.g4", "alr_x-antlr-1kized", "text/x--1kized")
    };

    public void testSet(MimeToPathTestSet set) {
        Path expected = set.filePath;
        String mt = set.mimeType;
        assertTrue(AdhocMimeTypes.isAdhocMimeType(set.mimeType));
        assertNotNull(expected + "", mt);
        assertNotNull(mt + "", expected);
        String foundMimeType = AdhocMimeTypes.mimeTypeForPath(expected);
        assertEquals(expected.toString() + " got " + foundMimeType, mt, foundMimeType);
        assertEquals(expected.toString() + " got " + mt, mt, AdhocMimeTypes.mimeTypeForPath(expected));
        assertEquals(mt, expected, AdhocMimeTypes.grammarFilePathForMimeType(mt));
        assertEquals(mt, expected, AdhocMimeTypes.grammarFilePathForMimeType(mt));
        assertTrue(mt, AdhocMimeTypes.isAdhocMimeType(mt));
        String ext = AdhocMimeTypes.fileExtensionFor(mt);
        assertNotNull(ext);
        Path test = Paths.get("/lang." + ext);
        assertTrue("Ext generated by AdhocMimeTypes for " + mt
                + " but not recognized by it: " + ext,
                AdhocMimeTypes.isAdhocMimeTypeFileExtension(test));
        String mt2 = AdhocMimeTypes.mimeTypeForPath(test);
        assertEquals("Mime type to file extension conversion was not reversible: "
                + ext + " " + mt, mt, mt2);
        try {
            MimePath pth = MimePath.parse(mt);
            assertNotNull(pth);
        } catch (Exception e) {
            throw new AssertionError("MimePath does not like " + mt, e);
        }

        MimeToPathTestSet nue = new MimeToPathTestSet(null, mt2, AdhocMimeTypes
                .grammarFilePathForMimeType(mt).toString(), AdhocMimeTypes.fileExtensionFor(mt2),
                AdhocMimeTypes.loggableMimeType(foundMimeType));
        nue.assertEqual(set);
    }

    @Test
    public void testSets() throws IOException {
        Map<String, MimeToPathTestSet> mimeTypes = new HashMap<>();
        Map<String, MimeToPathTestSet> exts = new HashMap<>();
        for (MimeToPathTestSet set : SETS) {
            AdhocMimeTypes._reinitAndDeleteCache();
            String mt = AdhocMimeTypes.mimeTypeForPath(set.filePath);
            if (mimeTypes.containsKey(mt)) {
                MimeToPathTestSet other = mimeTypes.get(mt);
                fail("Different file paths get the same mime type: "
                        + mt + " for '" + set.filePath + " and " + other.filePath);
            }
            String ext = AdhocMimeTypes.fileExtensionFor(mt);
            if (exts.containsKey(ext)) {
                MimeToPathTestSet other = exts.get(ext);
                fail("Different mime types get the same extension: " + ext + " - "
                        + mt + " and " + other.mimeType);
            }
            mimeTypes.put(mt, set);
            testSet(set);
            AdhocMimeTypes._reinitAndDeleteCache();
        }
    }

    private void testOne(String mt, Path expected) throws IOException {
        assertNotNull(expected + "", mt);
        assertNotNull(mt + "", expected);
        assertEquals(mt, expected, AdhocMimeTypes.grammarFilePathForMimeType(mt));
        assertEquals(expected.toString(), mt, AdhocMimeTypes.mimeTypeForPath(expected));
        assertTrue(mt, AdhocMimeTypes.isAdhocMimeType(mt));
        String ext = AdhocMimeTypes.fileExtensionFor(mt);
        assertNotNull(ext);
        Path test = Paths.get("/lang." + ext);
        assertTrue("Ext generated by AdhocMimeTypes for " + mt
                + " but not recognized by it: " + ext,
                AdhocMimeTypes.isAdhocMimeTypeFileExtension(test));
        String mt2 = AdhocMimeTypes.mimeTypeForPath(test);
        assertEquals("Mime type to file extension conversion was not reversible: "
                + ext + " " + mt, mt, mt2);
        try {
            MimePath pth = MimePath.parse(mt);
            assertNotNull(pth);
        } catch (Exception e) {
            throw new AssertionError("MimePath does not like " + mt, e);
        }

        AdhocMimeTypes._reinit();
        assertEquals(expected, AdhocMimeTypes.grammarFilePathForMimeType(mt));
        assertEquals(mt, AdhocMimeTypes.mimeTypeForPath(test));
//        MimeToPathTestSet set = new MimeToPathTestSet(null, mt, expected.toString(), ext, AdhocMimeTypes.loggableMimeType(mt2));
//        System.out.println(set + ",");
    }

    @Test
    public void testNegative() {
        assertFalse(AdhocMimeTypes.isAdhocMimeType("text/x-java"));
        assertFalse(AdhocMimeTypes.isAdhocMimeType("application/javascript"));
        assertFalse(AdhocMimeTypes.isAdhocMimeType("text/a-b"));
        assertFalse(AdhocMimeTypes.isAdhocMimeType("text/a-b-c"));
        assertFalse(AdhocMimeTypes.isAdhocMimeType("text/a-b-c"));
        assertFalse(AdhocMimeTypes.isAdhocMimeType("text/a-"));
        assertFalse(AdhocMimeTypes.isAdhocMimeType("text/l-"));
        assertFalse(AdhocMimeTypes.isAdhocMimeTypeFileExtension(Paths.get("foo.txt")));
        assertFalse(AdhocMimeTypes.isAdhocMimeTypeFileExtension(Paths.get("foo.java")));
        assertFalse(AdhocMimeTypes.isAdhocMimeTypeFileExtension(Paths.get("foo.a-")));
        assertFalse(AdhocMimeTypes.isAdhocMimeTypeFileExtension(Paths.get("foo.l-")));
        assertFalse(AdhocMimeTypes.isAdhocMimeTypeFileExtension(Paths.get("foo.alr_a-")));
        assertFalse(AdhocMimeTypes.isAdhocMimeTypeFileExtension(Paths.get("foo.alr_l-")));
        assertFalse(AdhocMimeTypes.isAdhocMimeTypeFileExtension(Paths.get("foo.alr_r")));

    }

    @Test
    public void testSimpleMimeTypes() throws Throwable {
        Path p = Paths.get("/path/to/SomeGrammar.g4");
        String mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/this/is/too/long/a/path/for/mime/path/which/limits/mime/types/to/one/twenty/eight/characters/but/this/path/goes/on/and/on/and/for/quite/a/while/and/eventually/there/is/a/file/but/not/for/a/long/time.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/non-ascii/válečných.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/non-ascii/朝日新聞.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/non-ascii/слова.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/non-ascii/šůčářížý.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/Users/Joe Blow/grammar.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/Users/Joe-Blow/grammar.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/Users/Joe^Blow/grammar.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/Users/Joe%20Blow/grammar.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/numeric/name/1234.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/numeric/name/^#&@.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/numeric/name/x\\\\x.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/weird/long/GrammarFileWhichHasASimplyUngodlyHugeRidiculousNameForNoEarthlyReasonICanImagineWhatCouldYouPossiblyHaveBeenThinkingByDoingThatAreYouInsane.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/numeric/name/line\nbreak.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/numeric/name/tab\tbreak.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/numeric/name/carriage\rreturn.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/numeric/name/(parens).g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/street/čeletna/[brackets].g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/street/main/$$$$.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/com/foo/bell\b.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/com/foo/.dotfile.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/č.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/a/b/č/d/\"stuff\".g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/a/b/č/d/\'stuff\'.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/a/b/č/d/.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/a/b/c/d/.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);

        p = Paths.get("/.g4");
        mt = AdhocMimeTypes.mimeTypeForPath(p);
        assertNotNull(mt);
        testOne(mt, p);
    }

    private void assertInvalid(Path p) {
        // @Test(expected=IllegalArgumentException.class) swallows
        // too much information about what did happen instead
        try {
            String mt = AdhocMimeTypes.mimeTypeForPath(p);
            fail("IllegalArgumentException should have been thrown for Path '" + p + "', but got " + mt);
        } catch (IllegalArgumentException ex) {
            // ok
        }
    }

    @Test
    public void testEmptyPath1() {
        assertInvalid(Paths.get("/"));
    }

    @Test
    public void testEmptyPath2() {
        assertInvalid(Paths.get(""));
    }

    @Test
    public void testRelativePathsIllegal1() {
        assertInvalid(Paths.get("foo/bar/Nothing.g4"));
    }

    @Test
    public void testRelativePathsIllegal2() {
        assertInvalid(Paths.get("/foo/bar/../Nothing.g4"));
    }

    @Test
    public void testRelativePathsIllegal3() {
        assertInvalid(Paths.get("/foo/bar/./Nothing.g4"));
    }

    static final class MimeToPathTestSet {

        final String mimeType;
        final Path filePath;
        final String extension;
        final String loggable;

        public MimeToPathTestSet(String mimeType, String filePath, String extension, String loggable) {
            assertNotNull(mimeType);
            assertNotNull(filePath);
            assertNotNull(extension);
            assertNotNull(loggable);
            assertFalse(mimeType.isEmpty());
            assertFalse(filePath.isEmpty());
            assertFalse(extension.isEmpty());
            this.loggable = unescape(loggable);
            this.mimeType = unescape(mimeType);
            this.filePath = Paths.get(unescape(filePath));
            this.extension = unescape(extension);
        }

        public MimeToPathTestSet(Void ignored, String mimeType, String filePath, String extension, String loggable) {
            assertNotNull(mimeType);
            assertNotNull(filePath);
            assertNotNull(extension);
            assertNotNull(loggable);
            assertFalse(mimeType.isEmpty());
//            assertFalse(filePath.isEmpty());
//            assertFalse(extension.isEmpty());
            this.loggable = loggable;
            this.mimeType = mimeType;
            this.filePath = Paths.get(filePath);
            this.extension = extension;
        }

        String unescape(String s) {
            StringBuilder sb = new StringBuilder();
            boolean lastWasBackslash = false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (lastWasBackslash) {
                    lastWasBackslash = false;
                    switch (c) {
                        case '\\':
                            sb.append("\\");
                            break;
                        case 'r':
                            sb.append("\r");
                            break;
                        case 'n':
                            sb.append("\n");
                            break;
                        case 'b':
                            sb.append("\b");
                            break;
                        case 't':
                            sb.append("\t");
                            break;
                        case '"':
                            sb.append("\"");
                            break;
                        default:
                            throw new AssertionError("Unknown escape \\" + c + " in '" + s + "'");
                    }
                } else {
                    switch (c) {
                        case '\\':
                            lastWasBackslash = true;
                            break;
                        default:
                            lastWasBackslash = false;
                            sb.append(c);
                    }
                }
            }
            return sb.toString();
        }

        String expString(String msg) {
            return "mime='" + escape(mimeType) + "' ext='" + escape(extension)
                    + "' path='" + escape(filePath.toString()) + "': " + msg;
        }

        private String escape(String s) {
            /*
            Why not String.replaceAll()?
            java.util.regex.PatternSyntaxException: Unexpected internal error near index 1
                at java.util.regex.Pattern.error(Pattern.java:1957)
             */
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '\\':
                        sb.append("\\\\\\\\");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\b':
                        sb.append("\\b");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '"':
                        sb.append("\\\"");
                        break;
                    default:
                        sb.append(c);
                }
            }
            return sb.toString();
        }

        public void assertEqual(MimeToPathTestSet other) {
            if (other == this) {
                return;
            }
            assertEquals("mime types do not match", other.mimeType, mimeType);
            assertEquals("extensions do not match", other.extension, extension);
            assertEquals("file paths do not match", other.filePath, filePath);
            assertEquals("loggable names do not match", other.loggable, loggable);
        }

        public String toString() {
            return "new MimeToPathTestSet(\"" + escape(mimeType)
                    + "\", \"" + escape(filePath.toString())
                    + "\", \"" + escape(extension)
                    + "\", \"" + escape(loggable) + "\")";
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + Objects.hashCode(this.mimeType);
            hash = 97 * hash + Objects.hashCode(this.filePath);
            hash = 97 * hash + Objects.hashCode(this.extension);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final MimeToPathTestSet other = (MimeToPathTestSet) obj;
            if (!Objects.equals(this.mimeType, other.mimeType)) {
                return false;
            }
            if (!Objects.equals(this.extension, other.extension)) {
                return false;
            }
            if (!Objects.equals(this.filePath, other.filePath)) {
                return false;
            }
            return true;
        }
    }
}
