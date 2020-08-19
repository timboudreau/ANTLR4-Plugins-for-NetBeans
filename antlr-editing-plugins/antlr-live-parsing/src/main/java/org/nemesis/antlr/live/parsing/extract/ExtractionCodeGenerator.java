/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.live.parsing.extract;

import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.LevenshteinDistance;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_PATH;
import org.nemesis.antlr.live.parsing.impl.GrammarKind;
import org.nemesis.antlr.memory.JFSPathHints;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.debug.api.Debug;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;

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

    private static JFSFileObject searchByName(JFS jfs, String pkg, JFSPathHints hints, String rawName, String... possibleSuffixen) {
        for (String suff : possibleSuffixen) {
            String nm = rawName + suff;
            UnixPath hintsResult = hints.firstPathForRawName(nm, "g4", "g");
            if (hintsResult != null) {
                JFSFileObject result = jfs.get(SOURCE_PATH, hintsResult);
                if (result == null) {
                    result = jfs.get(SOURCE_OUTPUT, hintsResult);
                }
                if (result != null) {
                    return result;
                }
            }
        }
        String pkgPath = pkg.replace('.', '/') + '/';
        for (String suff : possibleSuffixen) {
            for (StandardLocation loc : new StandardLocation[]{StandardLocation.SOURCE_PATH, StandardLocation.SOURCE_OUTPUT}) {
                UnixPath path = UnixPath.get(pkgPath + rawName + suff + ".java");
                JFSFileObject fo = jfs.get(loc, path);
                if (fo != null) {
                    return fo;
                }
            }
        }
        return null;
    }

    public static ExtractionCodeGenerationResult saveExtractorSourceCode(GrammarKind kind, Path realSourceFile, JFS fs, String pkg,
            String grammarName, String lexerGrammarName, PrintStream logTo, String tokensHash, JFSPathHints hints) throws IOException {
        ExtractionCodeGenerationResult result = new ExtractionCodeGenerationResult(grammarName, pkg);
        FileObject candidateLexer = null;
        FileObject foundParser = null;
        FileObject foundLexer = null;
        String lexerSuffix = "Lexer";
        String realPrefix = grammarName; // case may not match, so prefer values derived from actual file names
        // A bit of black magic here:
        // 1.  Though not documented, case difference between grammar name and grammar file name
        //     are tolerated by Antlr.  We have the grammar name from source generation, but we do not
        //     actually know the exact file name.  So we iterate all generated files and try to find the
        //     parser and lexer
        // 2.  If you run Antlr against a file with fragments only - a partial lexer grammer,
        //     you get a file named "grammarfile.java" which *is* a lexer but does not have Lexer
        //     as its suffix (or maybe this has to do with grammar file name case?).  So if we didn't
        //     find fooLexer.java or FooLexer.java, we also look for foo.java as a fallback
        // 3.  Depending on what all we're generating, we may have generated parsers and lexers for
        //     not just the target grammar, but things it imported, so we need to be sure we're getting
        //     the thing we're really after.
        // 4.  If we compiled a lexer grammar, there will not *be* a parser source file, and we need to
        //     know that to remove all the lines from ParserExtractor.java which are suffixed with //parser
        //     so it will be compilable.
        // 5.  If your grammar and .g4 file are named FooParser, you will not get a parser class named
        //     FooParserParser as you might expect, but just FooParser.
        //
        //     Are we having fun yet?
        if (grammarName != null && !grammarName.equals(lexerGrammarName)) {
            foundParser = searchByName(fs, pkg, hints, grammarName, "Parser", "");
            if (foundParser == null) {
                UnixPath pth = hints.firstPathForRawName(grammarName, "g4", "g");
                if (pth != null) {
                    foundParser = fs.get(StandardLocation.SOURCE_PATH, pth);
                    if (foundParser == null) {
                        foundParser = fs.get(StandardLocation.SOURCE_OUTPUT, pth);
                    }
                }
            }
            logTo.println("grammarName non-match lexerName " + grammarName + " / " + lexerGrammarName + ". Found " + foundParser);
        }
        if (lexerGrammarName != null) {
            foundLexer = searchByName(fs, pkg, hints, grammarName, "Lexer", "");
            if (foundLexer == null) {
                UnixPath pth = hints.firstPathForRawName(lexerGrammarName, "g4", "g");
                if (pth != null) {
                    foundLexer = fs.get(StandardLocation.SOURCE_PATH, pth);
                    if (foundLexer == null) {
                        foundLexer = fs.get(StandardLocation.SOURCE_OUTPUT, pth);
                    }
                }
            }
            logTo.println("For lexer name found " + foundLexer);
        }

        StringBuilder genInfo = new StringBuilder();
        genInfo.append("Passed grammar name: ").append(grammarName).append('\n');
        genInfo.append("Passed lexer name: ").append(lexerGrammarName).append('\n');
        genInfo.append("Looking for '").append(grammarName).append("'\n\n");
        genInfo.append("Passed lexer grammar name '").append(lexerGrammarName).append("'\n");
        String pfx = grammarName.endsWith("Parser") ? grammarName.substring(0, grammarName.length() - 6) : grammarName;
        genInfo.append("Expected prefix for lexer: '").append(pfx).append("'\n");
        // We presume antlr generation has been done here
//        Iterable<JavaFileObject> generated = fs.list(SOURCE_OUTPUT, pkg, EnumSet.of(JavaFileObject.Kind.SOURCE), false);
//        generated = CollectionUtils.concatenate(generated, fs.list(SOURCE_PATH, pkg, EnumSet.of(JavaFileObject.Kind.SOURCE), false));
        if (foundParser == null || foundLexer == null) {
            Iterable<JavaFileObject> generated = fs.list(SOURCE_OUTPUT, pkg, EnumSet.of(JavaFileObject.Kind.SOURCE), false);
            generated = CollectionUtils.concatenate(generated, fs.list(SOURCE_PATH, pkg, EnumSet.of(JavaFileObject.Kind.SOURCE), false));
            for (FileObject fo : generated) {
                if (foundParser != null && foundLexer != null) {
                    genInfo.append("  Have lexer ").append(foundLexer).append(" and parser ").append(foundParser).append(" - done\n");
                    break;
                }
                // XXX should use Levenshtein distance to update the candidate lexer if it is
                // a better match for the passed lexer name?
                String fn = Paths.get(fo.getName()).getFileName().toString();
                if (fn.endsWith(PARSER_EXTRACTOR) || fn.endsWith("Visitor") || fn.endsWith("Listener")) {
                    continue;
                }
                if (fn.endsWith(".java")) {
                    genInfo.append("examine ").append(fo.getName()).append(" as name ").append(fn).append('\n');
                    String nm = fn.substring(0, fn.length() - 5);

                    if (nm.toLowerCase().endsWith("lexer") || nm.toLowerCase().endsWith("parser") || grammarName.equalsIgnoreCase(nm)) {
                        genInfo.append("  ends with lexer or parser - marking examined\n");
                        result.examined(nm);
                    }
                    // Look for GrammarNameParser.java - if it is not present,
                    // we probably have a lexer grammar
                    if (foundParser == null && nm.endsWith("Parser") && nm.length() > 6) {
                        String prefix = nm.substring(0, nm.length() - 6);
                        genInfo.append("  is a parser, prefix ").append(prefix).append('\n');
                        if (prefix.equalsIgnoreCase(grammarName) || prefix.equalsIgnoreCase(pfx) || prefix.equals(nm)) {
                            realPrefix = prefix;
                            foundParser = fo;
                            genInfo.append("  bingo, got the parser: ").append(fo.getName()).append('\n');
                            continue;
                        }
                    }
                    if (foundLexer == null && lexerGrammarName != null && nm.equals(lexerGrammarName)) {
                        genInfo.append("  lexerGrammarName ").append(lexerGrammarName).append(" matches ").append(nm).append(" - it is the lexer\n");
                        foundLexer = fo;
                        if (foundLexer != null) {
                            break;
                        }
                    }
                    // Look for a lexer class; for *some* grammars, we wind
                    // up with just GrammarName.java, so we check for that too
                    if (foundLexer == null && (nm.endsWith("Lexer") || nm.endsWith("lexer"))) {
                        String prefix = nm.substring(0, nm.length() - 5);
                        genInfo.append("  looks like a lexer - ").append(prefix).append(" matches grammarName ")
                                .append(grammarName).append("? ").append(prefix.equalsIgnoreCase(grammarName))
                                .append(" matches computed prefix '").append(pfx).append("'?")
                                .append(pfx.equals(prefix))
                                .append("\n");
                        if (prefix.equalsIgnoreCase(grammarName) || pfx.equalsIgnoreCase(prefix)) {
                            realPrefix = prefix;
                            lexerSuffix = nm.substring(prefix.length());
                            foundLexer = fo;
                            genInfo.append("Determining ").append(fo.getName())
                                    .append(" to be the lexer; lexerSuffix '").append(lexerSuffix).append("'\n");
                            continue;
                        }
                    }
                    if (foundParser != null && foundLexer != null) {
                        genInfo.append("  Have lexer ").append(foundLexer).append(" and parser ").append(foundParser).append(" - done\n");
                        break;
                    }
                    if (grammarName.equalsIgnoreCase(nm) && !nm.endsWith("Parser")) {
                        genInfo.append("  Partial name match '").append(nm).append("' marking ").append(fo.getName()).append(" as fallback lexer candidate\n");
                        if (candidateLexer != null && lexerGrammarName != null) {
                            String oldCandidateName = rawName(candidateLexer);
                            String newCandidateName = rawName(fo);
                            if (LevenshteinDistance.levenshteinDistance(oldCandidateName, lexerGrammarName, false) > LevenshteinDistance.levenshteinDistance(newCandidateName, lexerGrammarName, true)) {
                                candidateLexer = fo;
                            }
                        } else {
                            candidateLexer = fo;
                        }
                    }
                }
            }
            // If we didn't find a lexer but did find a candidate java file using
            // just the grammar name, assume that's our lexer
            if (foundLexer == null && candidateLexer != null) {
                lexerSuffix = "";
            }
            if (foundLexer == null && candidateLexer != null) {
                genInfo.append("Using candidate partial-name-match lexer as the lexer ").append(candidateLexer.getName());
                foundLexer = candidateLexer;
            }
        } else {
            genInfo.append("Found lexer by name: ").append(foundLexer == null ? null : foundLexer.getName());
            genInfo.append("\nFound parser by name: ").append(foundParser == null ? null : foundParser.getName());
        }
        logTo.println(genInfo);
        // If we found nothing, we're not going to generate anything compilable,
        // so bail here so we don't leave behind either a class missing parts
        // needed when generation is fixed, or an uncompilable turd
        if (foundLexer == null && candidateLexer == null && foundParser == null) {
            LOG.log(Level.INFO, "Did not find a parser or a lexer: {0}", genInfo.toString().replace("\n", "; "));
            result.setGenerationInfo(genInfo);
            logTo.println("No parser or lexer found.");
            return result;
        }
        String actualParserName = foundParser == null ? null : pkg + '.' + UnixPath.get(foundParser.getName()).getFileName().rawName();
        if (actualParserName != null && actualParserName.endsWith("Lexer")) {
            foundParser = null;
            actualParserName = null;
        }

        String generatedClassName = stripExt(foundParser == null ? foundLexer.getName() : foundParser.getName())
                + PARSER_EXTRACTOR;

        result.setGeneratedClassName(generatedClassName);

        genInfo.insert(0, "\n/*\n");
        genInfo.append(" \n*/\n");

        logTo.println("Generate class named: " + generatedClassName);
        logTo.println("Actual parser name: " + actualParserName);

        String code = getLexerExtractorSourceCode(grammarName, lexerGrammarName,
                generatedClassName, realSourceFile, pkg, realPrefix,
                foundParser == null || kind == GrammarKind.LEXER,
                lexerSuffix,
                lexerGrammarName, actualParserName, logTo, kind, false,
                tokensHash);

        UnixPath extractorPath = UnixPath.get(pkg.replace('.', '/'), generatedClassName + ".java");
        Debug.message("Generated source code " + realSourceFile + " in " + pkg, () -> code + genInfo);

        logTo.println("Generated file: " + extractorPath);

        logTo.println("\nCode:\n" + code); // XXX for debugging

        JFSFileObject fo = fs.get(SOURCE_PATH, extractorPath);
        result.setGenerationInfo(genInfo);
        if (fo != null && Strings.charSequencesEqual(fo.getCharContent(true), code, false)) {
            logTo.append("Generated code exactly matches existing file, reuse that.");
            LOG.log(Level.FINEST, "Existing extractor matches generated code, reusing it.");
            // Avoid touching the file if we would regenerate the same content - it will
            // cause the test whether files have been modified and the parser should be
            // regenerated to always return true, making every keystroke lead to a furious
            // amount of work
            return result.setResult(fo);
        } else {
            LOG.log(Level.FINER, "Generating extractor. Passed lexer name: {0}, passed grammar name {1},"
                    + " found lexer: {2}, found grammar: {3}, package {4} kind {5}", new Object[]{
                        lexerGrammarName, grammarName, (foundLexer == null ? null : foundLexer.getName()),
                        (foundParser == null ? null : foundParser.getName()), pkg, kind});
        }
        return result.setResult(fs.create(extractorPath, SOURCE_OUTPUT, code));
    }

    private static String rawName(FileObject fo) {
        return stripExt(UnixPath.get(fo.getName()).getFileName().toString());
    }

    private static String stripExt(String name) {
        int ix = name.lastIndexOf('/');
        if (ix > 0 && ix < name.length() - 1) {
            name = name.substring(ix + 1);
        }
        ix = name.lastIndexOf('.');
        if (ix > 0) {
            name = name.substring(0, ix);
        }
        return name;
    }

    public static String getLexerExtractorSourceCode(String passedGrammarName, String passedLexerGrammarName,
            String generatedClassName, Path originalGrammarFile, String pkg,
            String classNamePrefix, boolean lexerOnly, String lexerSuffix, String passedLexerName,
            String actualParserName, PrintStream logTo, GrammarKind kind, boolean minify,
            String tokensHash) throws IOException {
        String result = Streams.readResourceAsUTF8(ParserExtractor.class, PARSER_EXTRACTOR_TEMPLATE);
        assert result != null : PARSER_EXTRACTOR_TEMPLATE + " not adjacent to " + ExtractionCodeGenerator.class;

        String lexerFinalName = passedLexerName == null ? classNamePrefix + lexerSuffix
                : passedLexerName;
        if (actualParserName == null) {
            actualParserName = pkg + "." + classNamePrefix + "Parser";
            logTo.println("Using parser name: " + actualParserName);
        }
        logTo.println("Using lexer name: " + lexerFinalName);

        String extractorPackage = AntlrProxies.class.getPackage().getName();

        if (AntlrLoggers.isActive(logTo)) {
            logTo.println("\n\n--------  " + kind + " ----------");
            logTo.println("classNamePrefix " + classNamePrefix);
            logTo.println("lexerFinalName " + lexerFinalName);
            logTo.println("passedLexerName " + passedLexerName);
            logTo.println("lexerSuffix " + lexerSuffix);
            logTo.println("actual parser name " + actualParserName);
            logTo.println("LEXER ONLY " + lexerOnly);
            logTo.println("GRAMMAR FILE " + originalGrammarFile.getFileName());
            logTo.println("passed grammar name " + passedGrammarName);
            logTo.println("passed lexer name " + passedLexerName);
            logTo.println("\n\n");
        }

        if (kind == GrammarKind.LEXER) {
//            System.out.println("\n\n--------  LEXER ----------");
//            System.out.println("lexerFinalName " + lexerFinalName);
//            System.out.println("passedLexerName " + passedLexerName);
//            System.out.println("lexerSuffix " + lexerSuffix);
//            System.out.println("actual parser name " + actualParserName);
//            System.out.println("LEXER ONLY " + lexerOnly);
//            System.out.println("GRAMMAR FILE " + originalGrammarFile.getFileName());
//            System.out.println("passed grammar name " + passedGrammarName);
//            System.out.println("passed lexer name " + passedLexerName);
            actualParserName = null;
            lexerOnly = true;
            lexerFinalName = passedGrammarName;
            logTo.println("Set lexerOnly to true for lexer grammar");
            logTo.println("Swap lexerFinalName to " + passedGrammarName);
        }
        if (kind != GrammarKind.COMBINED) {
            logTo.println("Non-combined, swap classNamePrefix to passedGrammarName " + passedGrammarName);
            classNamePrefix = passedGrammarName;
        } else {
            classNamePrefix = UnixPath.get(originalGrammarFile).rawName();
        }

        // Change the package statement
        result = result.replace("package org.nemesis.antlr.live.parsing.extract;",
                "package " + pkg + ";").replaceAll(PARSER_EXTRACTOR, generatedClassName);

        result = result.replace("--tokensHash--", tokensHash);
        // Replace the import with an import of the actual lexer class (even though we're in
        // the same package ordinarily - this could be genertated into wherever and an import
        // from the same package is harmless)
        result = result.replace("import ignoreme.placeholder.DummyLanguageLexer;",
                "\nimport " + pkg + "." + lexerFinalName + ";\n" + "import " + extractorPackage + ".AntlrProxies;\n");

        // Same for the parser, if lexerOnly is false
        result = result.replace("import ignoreme.placeholder.DummyLanguageParser;",
                lexerOnly ? "" : "\nimport " + actualParserName + ";//parser\n");
        // Replace class name occurrences in source
        result = result.replaceAll("DummyLanguageLexer", lexerFinalName);
        if (!lexerOnly) {
            result = result.replaceAll("DummyLanguageParser", actualParserName);
        }
        // Replace the string constant that provides the name for the ParseTreeProxy
        result = result.replaceAll("\"DummyLanguage\"", '"' + classNamePrefix + '"');
        // Replace the path constant, so we have that too
        result = result.replaceAll("/replace/with/path", originalGrammarFile.toString());
        // If a lexer grammar, remove parser related stuff as it would be uncompilable
        // with no parser
        if (lexerOnly) {
            StringBuilder sb = new StringBuilder(result.length());
            for (String line : result.split("\n")) {
                String trimmedLine = line.trim();
                if (minify) {
                    line = trimmedLine;
                }
                if (line.isEmpty() || line.endsWith("//parser") || line.startsWith("//")
                        || trimmedLine.startsWith("* ") || trimmedLine.startsWith("/*") || "*".equals(trimmedLine) || "*/".equals(trimmedLine)) {
                    continue;
                }
                sb.append(line).append('\n');
            }
            result = sb.toString();
        } else {
            StringBuilder sb = new StringBuilder(result.length());
            for (String line : result.split("\n")) {
                String trimmedLine = line.trim();
                if (minify) {
                    line = trimmedLine;
                }
                if (line.isEmpty() || line.endsWith("//lexerOnly") || line.startsWith("//")
                        || trimmedLine.startsWith("* ") || trimmedLine.startsWith("/*") || "*".equals(trimmedLine) || "*/".equals(trimmedLine)) {
                    continue;
                }
                sb.append(line).append('\n');
            }
            result = sb.toString();

        }
        result = result.replaceAll(" //parser", "");
        if (minify) {
            result.replaceAll("\n\n", "\n").replaceAll("\\n\\s+\\}\\n", "}");
        }
        return result;
    }
}
