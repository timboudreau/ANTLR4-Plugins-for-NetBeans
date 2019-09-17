package org.nemesis.antlr.live.language;

import java.nio.file.Path;
import java.util.Set;
import static org.nemesis.antlr.live.language.AdhocLanguageHierarchy.DUMMY_TOKEN_ID;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyTokenType;
import org.netbeans.api.lexer.TokenId;

/**
 * NetBeans TokenId implementation that wraps tokens extracted by
 * ParserExtractor from running antlr-generated code in isolation. Note that in
 * the generated set of tokens we really use, the 0th in the list is always EOF
 * (it sorts first, with id -1 coming from Antlr), and we always add a __dummy
 * token type which is used in the case that we are trying to lex text that is
 * not the text that was last parsed - meaning we have an out of date syntax
 * tree, and if there is more text than expected, we need *some* token id to use
 * for it.
 *
 * @author Tim Boudreau
 */
public class AdhocTokenId implements TokenId, Comparable<TokenId> {

    private final AntlrProxies.ProxyTokenType type;
    private final Path grammarPath;
    private final String name;

    public AdhocTokenId(AntlrProxies.ParseTreeProxy proxy, AntlrProxies.ProxyTokenType type, Set<String> usedNames) {
        this.type = type;
        String nm = type.programmaticName();
        String test = nm;
        // A grammar that uses literal tokens, aka '0' instead of a named
        // token rule, can have tokens that have duplicate symbolic names
        int ix = 1;
        while (usedNames.contains(test)) {
            test = nm + "_" + (ix++);
        }
        usedNames.add(test);
        this.name = test;
        this.grammarPath = proxy.grammarPath();
    }

    public AdhocTokenId(String name, Path grammarPath, int type) {
        this.type = new ProxyTokenType(type, DUMMY_TOKEN_ID, null, DUMMY_TOKEN_ID);
        this.grammarPath = grammarPath;
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int ordinal() {
        return type.type + 1;
    }

    /**
     * Attempts some heuristics based on token type name and contents to try to
     * come up with reasonable default categorizations for syntax highlighting.
     *
     * @param type The proxy token
     * @return A category
     */
    static String categorize(ProxyTokenType type) {
        if (type.isDelimiterLike()) {
            return "delimiters";
        }
        if (type.isOperatorLike()) {
            return "operators";
        }
        if (type.isPunctuation()) {
            return "symbols";
        }
        if (type.isKeywordLike()) {
            return "keywords";
        }
        if (type.isSingleCharacter()) {
            return "symbols";
        }
        if (type.nameContains("identifier")) {
            return "identifier";
        }
        if (type.name() != null
                && type.name().toLowerCase().startsWith("id")) {
            return "identifier";
        }
        if (type.nameContains("literal")) {
            return "literal";
        }
        if (type.nameContains("string")) {
            return "string";
        }
        if (type.nameContains("number") || type.nameContains("integer") || type.nameContains("float")) {
            return "numbers";
        }
        if (type.nameContains("field")) {
            return "field";
        }
        if (type.nameContains("comment")) {
            return "comment";
        }
        return "default";
    }

    @Override
    public String primaryCategory() {
        return categorize(type);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AdhocTokenId && ((AdhocTokenId) o).ordinal() == ordinal()
                && ((AdhocTokenId) o).grammarPath.equals(grammarPath);
    }

    @Override
    public int hashCode() {
        return (type.type + 1) * 43;
    }

    @Override
    public String toString() {
        return name() + "(" + ordinal() + " in " + grammarPath.getFileName() + ")";
    }

    @Override
    public int compareTo(TokenId o) {
        return Integer.compare(ordinal(), o.ordinal());
    }
}
