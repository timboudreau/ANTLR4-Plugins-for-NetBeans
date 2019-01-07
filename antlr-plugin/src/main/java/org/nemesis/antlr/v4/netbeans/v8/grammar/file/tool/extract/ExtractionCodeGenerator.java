package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.JavaFileObject;
import static javax.tools.StandardLocation.SOURCE_PATH;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.openide.filesystems.FileUtil;

/**
 * Takes ParserExtractor.template (symlink to ParserExtractor.java in this
 * package), and munges it to work against a particular package, grammar name
 * and style of grammar.
 *
 * @author Tim Boudreau
 */
public class ExtractionCodeGenerator {

    public static final String PARSER_EXTRACTOR = "ParserExtractor";
    public static final String PARSER_EXTRACTOR_SOURCE_FILE = PARSER_EXTRACTOR + ".java";
    public static final String PARSER_EXTRACTOR_TEMPLATE = PARSER_EXTRACTOR + ".template";

    private static final Logger LOG = Logger.getLogger(ExtractionCodeGenerator.class.getName());

    /**
     * Save to disk.
     *
     * @param originalGrammarFile The original grammar file it should be relative to
     * @param pkg The package name in the destination location
     * @param prefix The class name prefix, typically the name of the grammar but
     * may differ in character case
     * @param dir The destination directory
     * @return A path to the created file
     * @throws IOException If reading the template fails or writing fails
     */
    public static Path saveExtractorSourceTo(Path originalGrammarFile, String pkg, String prefix, Path dir) throws IOException {
        return saveExtractorSourceTo(originalGrammarFile, pkg, prefix, dir, false);
    }

    /**
     * Save to disk.
     *
     * @param originalGrammarFile The original grammar file it should be relative to
     * @param pkg The package name in the destination location
     * @param prefix The class name prefix, typically the name of the grammar but
     * may differ in character case
     * @param dir The destination directory
     * @param lexerOnly If true, omit any code relating to dealing with a parser
     * java class which will not exist for lexer grammars
     * @return A path to the created file
     * @throws IOException If reading the template fails or writing fails
     */
    public static Path saveExtractorSourceTo(Path originalGrammarFile, String pkg, String prefix, Path dir, boolean lexerOnly) throws IOException {
        if (Files.exists(dir.getParent().resolve("CompileResult.java"))) {
            throw new AssertionError("Writing extractor over itself: " + dir);
        }
        Path dest = dir.resolve(PARSER_EXTRACTOR_SOURCE_FILE);
        if (!Files.exists(dest)) {
            Path lexerFile = dir.resolve(prefix + "Lexer.java");
            String lexerSuffix = "Lexer";
            if (!Files.exists(lexerFile)) {
                // Fragment only files get compiled to grammarname.java, for
                // whatever reason
                lexerFile = dir.resolve(prefix + ".java");
                lexerSuffix = "lexer";
            }
            LOG.log(Level.FINER, "Save extractor to {0} wih lexer fn {1}, lexer only? {2}", new Object[]{dest, lexerFile, lexerOnly});
            String javaCode = getLexerExtractorSourceCode(originalGrammarFile, pkg, prefix, lexerOnly, lexerSuffix);
            Files.write(dest, javaCode.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        }
        return dest;
    }

    public static JFSFileObject saveExtractorSourceCode(Path realSourceFile, JFS fs, String pkg, String grammarName) throws IOException {
        // We presume antlr generation has been done here
        Iterable<JavaFileObject> generated = fs.list(SOURCE_PATH, pkg, EnumSet.of(JavaFileObject.Kind.SOURCE), false);
        JavaFileObject candidateLexer = null;
        JavaFileObject foundParser = null;
        JavaFileObject foundLexer = null;
        String lexerSuffix = "Lexer";
        String realPrefix = grammarName; // case may not match, so prefer values derived from actual file names
        // A bit of black magic here:
        // 1.  Though not documented, case difference between grammar name and grammar file name
        //     are tolerated by Antlr.  We have the grammar name from source generation, but we do not
        //     actually know the exact file name.  So we parse all generated files and try to find the
        //     parser and lexer
        // 2.  If you run Antlr against a file with fragments only - a partial lexer grammer,
        //     you get a file named "grammarfile.java" which *is* the lexer but does not have Lexer
        //     as its suffix (or maybe this has to do with grammar file name case?).  So if we didn't
        //     find fooLexer.java or FooLexer.java, we also look for foo.java as a fallback
        // 3.  Depending on what all we're generating, we may have generated parsers and lexers for
        //     not just the target grammar, but things it imported, so we need to be sure we're getting
        //     the thing we're really after.
        // 4.  If we compiled a lexer grammar, there will not *be* a parser source file, and we need to
        //     know that to remove all the lines from ParserExtractor.java which are suffixed with //parser
        //     so it will be compilable.

        for (JavaFileObject fo : generated) {
            String fn = Paths.get(fo.getName()).getFileName().toString();
            if (fn.endsWith(".java")) {
                String nm = fn.substring(0, fn.length() - 5);
                // Look for GrammarNameParser.java - if it is not present,
                // we probably have a lexer grammar
                if (foundParser == null && nm.endsWith("Parser") && nm.length() > 6) {
                    String prefix = nm.substring(0, nm.length() - 6);
                    if (prefix.equalsIgnoreCase(grammarName)) {
                        realPrefix = prefix;
                        foundParser = fo;
                        continue;
                    }
                }
                // Look for a lexer class; for *some* grammars, we wind
                // up with just GrammarName.java, so we check for that too
                if (foundLexer == null && (nm.endsWith("Lexer") || nm.endsWith("lexer"))) {
                    String prefix = nm.substring(0, nm.length() - 5);
                    if (prefix.equalsIgnoreCase(grammarName)) {
                        realPrefix = prefix;
                        lexerSuffix = nm.substring(prefix.length());
                        foundLexer = fo;
                        continue;
                    }
                }
                if (foundParser != null && foundLexer != null) {
                    break;
                }
                if (grammarName.equalsIgnoreCase(nm)) {
                    candidateLexer = fo;
                }
            }
        }
        // If we didn't find a lexer but did find a candidate java file using
        // just the grammar name, assume that's our lexer
        if (foundLexer == null && candidateLexer != null) {
            lexerSuffix = "";
        }
        // If we found nothing, we're not going to generate anything compilable,
        // so bail here so we don't leave behind either a class missing parts
        // needed when generation is fixed, or an uncompilable turd
        if (foundLexer == null && candidateLexer == null && foundParser == null) {
            throw new IOException("Could not find any lexer or parser java "
                    + "sources.  Either the grammar is broken or code generation "
                    + "failed.");
        }
        String code = getLexerExtractorSourceCode(realSourceFile, pkg, realPrefix, foundParser == null, lexerSuffix);
//        System.out.println("\n\n********************* CODE ***********************\n");
//        System.out.println(code);
//        System.out.println("\n\n");
        Path extractorPath = Paths.get(pkg.replace('.', '/'), PARSER_EXTRACTOR_SOURCE_FILE);
        return fs.create(extractorPath, SOURCE_PATH, code);
    }

    public static String getLexerExtractorSourceCode(Path originalGrammarFile, String pkg,
            String classNamePrefix, boolean lexerOnly, String lexerSuffix) throws IOException {
        // We use the symlink of ParserExtractor.java, next to this class, as a template, replacing
        // the package statement and references to parser and lexer it was written agains.

        // If a lexer only grammar, we remove all lines suffixed with //parser
        // so we don't reference things that don't exist
        try (final InputStream in = ExtractionCodeGenerator.class.getResourceAsStream(PARSER_EXTRACTOR_TEMPLATE)) {
            if (in == null) {
                throw new IOException("ParserExtractor.template not on " + "classpath next to " + ExtractionCodeGenerator.class.getName());
            }
            try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                FileUtil.copy(in, out);
                String extractorPackage = AntlrProxies.class.getPackage().getName();
                String result = new String(out.toByteArray(), StandardCharsets.UTF_8);
                // Change the package statement
                result = result.replace("package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract;",
                        "package " + pkg + ";");
                // Replace the import with an import of the actual lexer class (even though we're in
                // the same package ordinarily - this could be generated into wherever and an import
                // from the same package is harmless)
                result = result.replace("import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;",
                        "\nimport " + pkg + "." + classNamePrefix + lexerSuffix + ";" + "import " + extractorPackage + ".AntlrProxies;\n");

                // Same for the parser, if lexerOnly is false
                result = result.replace("import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;",
                        lexerOnly ? "" : "\nimport " + pkg + "." + classNamePrefix + "Parser;//parser\n");
                String lexerName = classNamePrefix + lexerSuffix;
                // Replace class name occurrences in source
                result = result.replaceAll("ANTLRv4Lexer", lexerName);
                result = result.replaceAll("ANTLRv4Parser", classNamePrefix + "Parser");
                // Replace the string constant that provides the name for the ParseTreeProxy
                result = result.replaceAll("\"ANTLRv4\"", '"' + classNamePrefix + '"');
                // Replace the path constant, so we have that too
                result = result.replaceAll("/replace/with/path", originalGrammarFile.toString());
                // If a lexer grammar, remove parser related stuff as it would be uncompilable
                // with no parser
                if (lexerOnly) {
                    StringBuilder sb = new StringBuilder();
                    for (String line : result.split("\n")) {
                        line = line.trim();
                        if (line.isEmpty() || line.endsWith("//parser") || line.startsWith("//")
                                || line.startsWith("* ") || line.startsWith("/*") || "*".equals(line) || "*/".equals(line)) {
                            continue;
                        }
                        sb.append(line).append('\n');
                    }
                    result = sb.toString();
                }
                return result;
            }
        }
    }
}
