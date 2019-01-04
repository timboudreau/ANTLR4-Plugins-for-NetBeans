package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.toolext;

import org.antlr.runtime.Token;
import org.antlr.v4.Tool;
import org.antlr.v4.codegen.CodeGenerator;
import org.antlr.v4.tool.ErrorType;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.ast.GrammarAST;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.StandardLocation;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.JFSFileObject;

public class AlternateTokenVocabParser {

    protected final Grammar g;
    private final MemoryTool tool;

    public AlternateTokenVocabParser(Grammar g, MemoryTool tool) {
        this.g = g;
        this.tool = tool;
    }

    /**
     * Load a vocab file {@code <vocabName>.tokens} and return mapping.
     */
    public Map<String, Integer> load() {
        Map<String, Integer> tokens = new LinkedHashMap<String, Integer>();
        Path filePath = importedFilePath();
        int maxTokenType = -1;
        BufferedReader br = null;
        Tool tool = g.tool;
        String vocabName = g.getOptionString("tokenVocab");
        try {
            JFSFileObject fullFile = getImportedVocabFile();
            Pattern tokenDefPattern = Pattern.compile("([^\n]+?)[ \\t]*?=[ \\t]*?([0-9]+)");
            br = new BufferedReader(fullFile.openReader(true), fullFile.length());
            String tokenDef = br.readLine();
            int lineNum = 1;
            while (tokenDef != null) {
                Matcher matcher = tokenDefPattern.matcher(tokenDef);
                if (matcher.find()) {
                    String tokenID = matcher.group(1);
                    String tokenTypeS = matcher.group(2);
                    int tokenType;
                    try {
                        tokenType = Integer.valueOf(tokenTypeS);
                    } catch (NumberFormatException nfe) {
                        tool.errMgr.toolError(ErrorType.TOKENS_FILE_SYNTAX_ERROR,
                                vocabName + CodeGenerator.VOCAB_FILE_EXTENSION,
                                " bad token type: " + tokenTypeS,
                                lineNum);
                        tokenType = Token.INVALID_TOKEN_TYPE;
                    }
                    tool.log("grammar", "import " + tokenID + "=" + tokenType);
                    tokens.put(tokenID, tokenType);
                    maxTokenType = Math.max(maxTokenType, tokenType);
                    lineNum++;
                } else {
                    if (tokenDef.length() > 0) { // ignore blank lines
                        tool.errMgr.toolError(ErrorType.TOKENS_FILE_SYNTAX_ERROR,
                                vocabName + CodeGenerator.VOCAB_FILE_EXTENSION,
                                " bad token def: " + tokenDef,
                                lineNum);
                    }
                }
                tokenDef = br.readLine();
            }
        } catch (FileNotFoundException fnfe) {
            GrammarAST inTree = g.ast.getOptionAST("tokenVocab");
            String inTreeValue = inTree.getToken().getText();
            if (vocabName.equals(inTreeValue)) {
                tool.errMgr.grammarError(ErrorType.CANNOT_FIND_TOKENS_FILE_REFD_IN_GRAMMAR,
                        g.fileName,
                        inTree.getToken(),
                        filePath);
            } else { // must be from -D option on cmd-line not token in tree
                tool.errMgr.toolError(ErrorType.CANNOT_FIND_TOKENS_FILE_GIVEN_ON_CMDLINE,
                        filePath,
                        g.name);
            }
        } catch (Exception e) {
            tool.errMgr.toolError(ErrorType.ERROR_READING_TOKENS_FILE,
                    e,
                    filePath,
                    e.getMessage());
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ioe) {
                tool.errMgr.toolError(ErrorType.ERROR_READING_TOKENS_FILE,
                        ioe,
                        filePath,
                        ioe.getMessage());
            }
        }
        return tokens;
    }

    private Path importedFilePath() {
        Path result = Paths.get(g.getOptionString("tokenVocab") + CodeGenerator.VOCAB_FILE_EXTENSION);
        if (g.tool.libDirectory != null) {
            result = Paths.get(g.tool.libDirectory).resolve(result);
        }
        if (result.startsWith(".")) {
            result = tool.resolveRelativePath(result.getFileName());
        }
        return result;
    }

    /**
     * Return a File descriptor for vocab file. Look in library or in -o output
     * path. antlr -o foo T.g4 U.g4 where U needs T.tokens won't work unless we
     * look in foo too. If we do not find the file in the lib directory then
     * must assume that the .tokens file is going to be generated as part of
     * this build and we have defined .tokens files so that they ALWAYS are
     * generated in the base output directory, which means the current directory
     * for the command line tool if there was no output directory specified.
     */
    public JFSFileObject getImportedVocabFile() throws FileNotFoundException {
        Path path = importedFilePath();
        JFSFileObject fo = tool.jfs().get(StandardLocation.SOURCE_PATH, path);
        if (fo == null) {
            fo = tool.jfs().get(SOURCE_OUTPUT, path);
        }
        if (fo == null) {
            fo = tool.jfs().get(CLASS_OUTPUT, path);
        }
        if (fo == null) {
            throw new FileNotFoundException(path.toString());
        }
        return fo;
    }
}
