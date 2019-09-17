package org.nemesis.antlr.memory.tool;

import org.antlr.runtime.Token;
import org.antlr.v4.codegen.CodeGenerator;
import org.antlr.v4.tool.ErrorType;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.ast.GrammarAST;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nemesis.jfs.JFSFileObject;

final class AlternateTokenVocabParser {

    /**
     * Load a vocab file {@code <vocabName>.tokens} and return mapping.
     */
    public Map<String, Integer> load(MemoryTool tool, Grammar g) {
        ToolContext ctx = ToolContext.get(tool);
        Map<String, Integer> tokens = new LinkedHashMap<String, Integer>();
        Path filePath = ctx.importedFilePath(g, tool);
        String vocabName = g.getOptionString("tokenVocab");
        try {
            JFSFileObject fullFile = ctx.getImportedVocabFile(g, tool);
            JFSFileObject ff = fullFile;
            tool.withCurrentPathThrowing(Paths.get(fullFile.getName()), () -> {
                int maxTokenType = -1;
                Pattern tokenDefPattern = Pattern.compile("([^\n]+?)[ \\t]*?=[ \\t]*?([0-9]+)");
                try (BufferedReader b = new BufferedReader(ff.openReader(true), ff.length())) {
                    String tokenDef = b.readLine();
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
                        tokenDef = b.readLine();
                    }
                }
            });
        } catch (FileNotFoundException fnfe) {
            GrammarAST inTree = g.ast.getOptionAST("tokenVocab");
            String inTreeValue = inTree.getToken().getText();
            if (vocabName.equals(inTreeValue)) {
                tool.errMgr.grammarError(ErrorType.CANNOT_FIND_TOKENS_FILE_REFD_IN_GRAMMAR,
                        g.fileName,
                        inTree.getToken(),
                        filePath.toString());
            } else { // must be from -D option on cmd-line not token in tree
                tool.errMgr.toolError(ErrorType.CANNOT_FIND_TOKENS_FILE_GIVEN_ON_CMDLINE,
                        filePath.toString(),
                        g.name);
            }
        } catch (Exception e) {
            tool.errMgr.toolError(ErrorType.ERROR_READING_TOKENS_FILE,
                    e,
                    filePath.toString(),
                    e.getMessage());
        }
        return tokens;
    }
}
