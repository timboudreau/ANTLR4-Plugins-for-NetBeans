package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.toolext;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.tools.JavaFileManager.Location;
import org.antlr.runtime.ANTLRInputStream;
import org.antlr.v4.Tool;
import static org.antlr.v4.Tool.ALL_GRAMMAR_EXTENSIONS;
import org.antlr.v4.parse.ANTLRParser;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.tool.ANTLRMessage;
import org.antlr.v4.tool.ErrorManager;
import org.antlr.v4.tool.ErrorSeverity;
import org.antlr.v4.tool.ErrorType;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.GrammarTransformPipeline;
import org.antlr.v4.tool.LexerGrammar;
import org.antlr.v4.tool.ast.GrammarAST;
import org.antlr.v4.tool.ast.GrammarRootAST;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.JFS;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.JFSFileObject;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.ParsedAntlrError;
import org.stringtemplate.v4.ST;

/**
 *
 * @author Tim Boudreau
 */
public final class MemoryTool extends Tool {

    private JFS jfs;
    private final Location location;
    private final Path dir;

    public MemoryTool(JFS jfs, Location location, Path dir, String... args) {
        super(args);
        this.jfs = jfs;
        this.location = location;
        this.dir = dir;
        this.msgFormat = "vs2005";
        this.errMgr = new ErrM(this);
        errMgr.setFormat("vs2005");
        grammarEncoding = jfs.encoding().name();
    }

    JFS jfs() {
        return jfs;
    }

    public List<ParsedAntlrError> errors() {
        List<ParsedAntlrError> result = new ArrayList<>(((ErrM) this.errMgr).errors);
        Collections.sort(result);
        return result;
    }

    public List<String> infoMessages() {
        return ((ErrM) this.errMgr).infos;
    }

    @Override
    public void log(String msg) {
        // do nothing
    }

    @Override
    public void log(String component, String msg) {
        // do nothing
//        System.out.println(component + ": " + msg);
    }

    @Override
    public File getOutputDirectory(String fileNameWithPath) {
//        throw new UnsupportedOperationException("File output not permitted:"
//                + fileNameWithPath);
        return fileNameWithPath == null ? new File("/tmp") : new File(fileNameWithPath);
    }

    Path resolveRelativePath(Path rel) {
        return dir.resolve(rel);
    }

    @Override
    public Writer getOutputFileWriter(Grammar g, String fileName) throws IOException {
        Path pth = dir.resolve(fileName);
        JFSFileObject fo = jfs.getSourceFileForOutput(pth.toString());
        return fo.openWriter();
    }

    @Override
    public Grammar loadGrammar(String fileName) {

        JFSFileObject fo = jfs.get(location, Paths.get(fileName));
        if (fo == null) {
            fo = jfs.get(location, dir.resolve(fileName));
        }
        if (fo != null) {
            try {
                org.antlr.runtime.CharStream charStream = new org.antlr.runtime.ANTLRReaderStream(fo.openReader(true), fo.length());
                GrammarRootAST grammarRootAST = parse(fileName, charStream);
                final Grammar g = createGrammar(grammarRootAST);
                g.fileName = fileName;
                process(g, false);
                return g;
            } catch (IOException oi) {
                errMgr.toolError(ErrorType.CANNOT_OPEN_FILE, oi, fileName);
            }
        }
        return super.loadGrammar(fileName);
    }

    private Map<String, Grammar> importedGrammars = null;

    @SuppressWarnings("unchecked")
    private Map<String, Grammar> importedGrammars() {
        if (importedGrammars != null) {
            return importedGrammars;
        }
        try {
            Field f = Tool.class.getDeclaredField("importedGrammars");
            f.setAccessible(true);
            return importedGrammars = (Map<String, Grammar>) f.get(this);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            throw new IllegalStateException("Could not look up imported grammars field", ex);
        }
    }

    public JFSFileObject getImportedGrammarFileObject(Grammar g, String fileName) {
        if (g.fileName == null) {
            System.out.println("Grammar with null filename: " + g + "; was passed " + fileName);
        }
        JFSFileObject fo = jfs.get(location, dir.resolve(fileName));
        if (fo == null) {
            Path nxt = Paths.get(fileName);
            fo = jfs.get(location, nxt);
            if (fo == null && nxt.getParent() != null) {
                fo = jfs.get(location, nxt.getParent().resolve(fileName));
            }
            if (fo == null) {
                fo = jfs.get(location, Paths.get("import").resolve(fileName));
            }
            if (fo == null) {
                fo = jfs.get(location, Paths.get("import").resolve(g.fileName));
            }
            if (fo == null && libDirectory != null) {
                fo = jfs.get(location, Paths.get(libDirectory).resolve(g.fileName));
            }
            if (fo == null) {
                System.out.println("FAILED TO RESOLVE " + fileName + " relative to " + g.fileName + ". Available: " + list());
            }
        }
//        if (fo != null) {
//            System.out.println("RESOLVED GRAMMAR " + fileName + " as " + fo);
//        }
        return fo;
    }

    private String list() {
        StringBuilder sb = new StringBuilder("Location in use is " + location + "\n");
        for (Map.Entry<JFSFileObject, Location> e : jfs.listAll().entrySet()) {
            sb.append(e.getKey().getName()).append('\t').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    public Grammar loadImportedGrammar(Grammar g, GrammarAST nameNode) throws IOException {
        String name = nameNode.getText();
        Grammar imported = importedGrammars().get(name);
        if (imported == null) {
            g.tool.log("grammar", "load " + name + " from " + g.fileName);
            JFSFileObject importedFile = null;
            for (String extension : ALL_GRAMMAR_EXTENSIONS) {
                importedFile = getImportedGrammarFileObject(g, name + extension);
                if (importedFile != null) {
                    break;
                }
            }
            if (importedFile != null) {
//                ANTLRInputStream in = new ANTLRInputStream(importedFile.openInputStream(), importedFile.length());
                ANTLRInputStream in = new ANTLRInputStream(importedFile.openInputStream());
                GrammarRootAST root = parse(g.fileName, in);
                if (root == null) {
                    return null;
                }
                imported = createGrammar(root);
                imported.fileName = Paths.get(importedFile.getName()).getFileName().toString();
                importedGrammars().put(root.getGrammarName(), imported);
            } else {
                errMgr.grammarError(ErrorType.CANNOT_FIND_IMPORTED_GRAMMAR, g.fileName, nameNode.getToken(), name);
            }
        }
        if (imported == null) {
            return super.loadImportedGrammar(g, nameNode);
        }
        return imported;
    }

    public Grammar loadDependentGrammar (String name, JFSFileObject fo) throws IOException {
        ANTLRInputStream in = new ANTLRInputStream(fo.openInputStream(), fo.length());
        GrammarRootAST root = parse(name, in);
        Grammar result = createGrammar(root);
        result.fileName = name;
        return result;
    }

    public Grammar createGrammar(GrammarRootAST ast) {
        final Grammar g;
        if (ast.grammarType == ANTLRParser.LEXER) {
            g = new LexerGrammar(this, ast);
        } else {
            g = new AlternateTokenLoadingGrammar(this, ast);
        }

//        BuildDependencyGenerator dep =
//					new BuildDependencyGenerator(this, g);
//
//        System.out.println("*************** DEPENDENCIES *****************");
//        System.out.println(dep.getDependencies().render());
//        System.out.println("***************              *****************");

        // ensure each node has pointer to surrounding grammar
        GrammarTransformPipeline.setGrammarPtr(g, ast);
        return g;
    }


    @Override
    public void panic() {
        throw AttemptedExit.panic();
    }

    @Override
    public void exit(int exitCode) {
        throw new AttemptedExit(exitCode);
    }

    public static final class AttemptedExit extends RuntimeException {

        private final int code;

        AttemptedExit(int code) {
            super(code == -1 ? "Antlr panic" : "Attempted exit with " + code);
            this.code = code;
        }

        public static AttemptedExit panic() {
            return new AttemptedExit(-1);
        }

        public boolean isPanic() {
            return code == -1;
        }

        public int code() {
            return code;
        }
    }

    static final class ErrM extends ErrorManager {

        private final List<ParsedAntlrError> errors = new ArrayList<>(3);
        private final List<String> infos = new ArrayList<>(3);

        public ErrM(Tool tool) {
            super(tool);
        }

        private String toString(ANTLRMessage msg) {
            ST msgST = getMessageTemplate(msg);
            String outputMsg = msgST.render();
            if (tool.errMgr.formatWantsSingleLineMessage()) {
                outputMsg = outputMsg.replace('\n', ' ');
            }
            return outputMsg;
        }

        @Override
        public void emit(ErrorType etype, ANTLRMessage msg) {
            if (etype == ErrorType.CANNOT_FIND_IMPORTED_GRAMMAR) {
//                Thread.dumpStack();
//                System.out.println(msg);
            }
            Path pth = msg.fileName == null ? Paths.get("_no-file_") : Paths.get(msg.fileName);
            int charPositionInLine = msg.charPosition;
            int line = msg.line;
            int startOffset = -1;
            int stopOffset = -1;
            if (msg.offendingToken instanceof CommonToken) {
                CommonToken ct = (CommonToken) msg.offendingToken;
                startOffset = ct.getStartIndex();
                stopOffset = ct.getStopIndex();
            }
            boolean isError = etype.severity == ErrorSeverity.ERROR || etype.severity == ErrorSeverity.ERROR_ONE_OFF
                    || etype.severity == ErrorSeverity.FATAL;

//            String message = msg.getMessageTemplate(true).
            ParsedAntlrError pae = new ParsedAntlrError(isError, etype.code, pth, line, charPositionInLine, toString(msg));
            if (startOffset != -1 && stopOffset != -1) {
                pae.setFileOffsetAndLength(startOffset, stopOffset - startOffset);
            }
            errors.add(pae);
            super.emit(etype, msg);
        }

        @Override
        public void info(String msg) {
            infos.add(msg);
            super.info(msg);
        }

        @Override
        public void resetErrorState() {
            errors.clear();
            infos.clear();
            super.resetErrorState();
        }
    }
}
