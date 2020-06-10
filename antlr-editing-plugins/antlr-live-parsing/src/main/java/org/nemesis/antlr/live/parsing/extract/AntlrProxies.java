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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;
import static org.nemesis.adhoc.mime.types.AdhocMimeTypes.loggableMimeType;

/**
 * IMPORTANT: This class is a go-between between isolated and non-isolated
 * classloaders. That means it must not have any dependencies on ANTLR classes,
 * or such objects will result in a ClassCastException when the IDE touches them
 * with its own definition of those classes from its own classloader - we may be
 * running code that constructs these against a different version of ANTLR than
 * the one we are running.
 * <p>
 * Tests ensure that when antlr is run, no objects allocated by the isolation
 * classloader find their way into an instance of ParseTreeProxy. But when
 * maintaining this class, the safe way is to add no methods that take Antlr
 * classes - just pass the primitive values of their fields.
 * </p>
 *
 * @author Tim Boudreau
 */
public class AntlrProxies {

    private final List<ProxyToken> tokens = new ArrayList<>();
    private final List<ProxyTokenType> tokenTypes = new ArrayList<>(50);
    private final List<Ambiguity> ambiguities = new ArrayList<>(10);
    private ParseTreeElement root = new ParseTreeElement(ParseTreeElementKind.ROOT);
    private final ProxyTokenType EOF_TYPE = new ProxyTokenType(-1, "EOF", "", "EOF");
    private final List<ParseTreeElement> treeElements = new ArrayList<>(50);
    private final Set<ProxySyntaxError> errors = new TreeSet<>();
    private String[] parserRuleNames = new String[0];
    private String[] channelNames = new String[0];
    private boolean hasParseErrors;
    private MessageDigest hash;
    private RuntimeException thrown;
    private final String grammarName;
    private final Path grammarPath;
    private final CharSequence text;
    private BitSet[] ruleReferences;

    public AntlrProxies(String grammarName, Path grammarPath, CharSequence text) {
        this.grammarName = grammarName;
        this.grammarPath = grammarPath;
        tokenTypes.add(EOF_TYPE);
        newHash();
        this.text = text;
    }

    public void onAmbiguity(Ambiguity ambiguity) {
        ambiguities.add(ambiguity);
    }

    public void onThrown(Throwable thrown) {
        // This will be a ProxyException that doesn't hold
        // a reference to any types from the foreign classloader
        if (!(thrown instanceof ProxyException)) {
            thrown = new ProxyException(thrown);
        }
        if (this.thrown != null) {
            this.thrown.addSuppressed(thrown);
        } else {
            this.thrown = (ProxyException) thrown;
        }
    }

    public Throwable thrown() {
        return thrown;
    }

    private void newHash() {
        try {
            hash = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }

    public ParseTreeProxy result() {
        String hashString = Base64.getUrlEncoder().encodeToString(hash.digest());
        newHash();
        return new ParseTreeProxy(tokens, tokenTypes, root, EOF_TYPE,
                treeElements, errors, parserRuleNames, channelNames, hasParseErrors, hashString,
                grammarName, grammarPath, text, thrown, ruleReferences, ambiguities);
    }

    /**
     * Generates an "unparsed" ParseTreeProxy which can allow lexing to start
     * immediately, and redone once the grammar is built, compiled and the file
     * parsed with it.
     *
     * @param pth The path to the originating grammar file
     * @param grammarName The name of the grammar (which should match the file
     * name)
     * @param text The text that should be parsed
     * @return A dummy ParseTreeProxy which does not require background work or
     * large amounts of I/O which can be used until a real one is ready
     */
    public static ParseTreeProxy forUnparsed(Path pth, String grammarName, CharSequence text) {
        if (text == null) {
            text = "(sample code here)\n";
        }
        ProxyTokenType textType = new ProxyTokenType(0, "text", "text", "text");
        ProxyToken all = new ProxyToken(0, 0, 0, 0, 0, 0, text.length() - 1);
        ProxyToken eof = new ProxyToken(-1, 0, 0, 0, 1, text.length(), text.length());
        ParseTreeElement root = new ParseTreeElement(ParseTreeElementKind.ROOT);
        ParseTreeElement child = new RuleNodeTreeElement("unparsed", 0, 0, 1, 1);
        ProxyTokenType EOF_TYPE = new ProxyTokenType(-1, "EOF", "", "EOF");
        root.add(child);
        List<ProxyTokenType> tokenTypes = Arrays.asList(EOF_TYPE, textType);
        List<ProxyToken> tokens = Arrays.asList(all, eof);
        ParseTreeProxy prox = new ParseTreeProxy(tokens, tokenTypes, root, EOF_TYPE, Arrays.asList(root, child),
                Collections.emptySet(), new String[]{"everything"}, new String[]{"default"},
                false, Long.toString(text.hashCode(), 36),
                grammarName, pth, text, null, new BitSet[1], Collections.emptyList());
        prox.isUnparsed = true;
        return prox;
    }

    /**
     * A wrapper for generated ANTLR parser and lexer vocabularies, optionally
     * containing a parse tree of some text. This class takes care not to expose
     * any ANTLR types which may have been loaded in a disposable classloader -
     * so tokens, etc. are deconstructed to primitive values and returned as
     * proxy objects.
     */
    public static final class ParseTreeProxy implements Serializable {

        private final List<ProxyToken> tokens;
        private final List<ProxyTokenType> tokenTypes;
        private final List<Ambiguity> ambiguities;
        private final ParseTreeElement root;
        private final ProxyTokenType eofType;
        private final List<ParseTreeElement> treeElements;
        private final Set<ProxySyntaxError> syntaxErrors;
        private final String[] parserRuleNames;
        private final String[] channelNames;
        private final boolean hasParseErrors;
        private final String hashString;
        private final String grammarName;
        private final String grammarPath;
        private boolean isUnparsed;
        private final CharSequence text;
        private final RuntimeException thrown;
        private static final AtomicLong IDS = new AtomicLong();
        private final long id = IDS.getAndIncrement();
        private final long when = System.currentTimeMillis();
        private BitSet[] ruleReferencesForToken;

        ParseTreeProxy(List<ProxyToken> tokens, List<ProxyTokenType> tokenTypes,
                ParseTreeElement root, ProxyTokenType eofType, List<ParseTreeElement> treeElements,
                Set<ProxySyntaxError> errors, String[] parserRuleNames,
                String[] channelNames, boolean hasParseErrors, String hashString, String grammarName,
                Path grammarPath, CharSequence text, RuntimeException thrown,
                BitSet[] ruleReferencesForToken, List<Ambiguity> ambiguities) {
            this.tokens = tokens;
            this.tokenTypes = tokenTypes;
            this.root = root;
            this.eofType = eofType;
            this.treeElements = treeElements;
            this.syntaxErrors = errors;
            this.parserRuleNames = parserRuleNames;
            this.channelNames = channelNames;
            this.hasParseErrors = hasParseErrors;
            this.hashString = hashString;
            this.grammarName = grammarName;
            this.grammarPath = grammarPath.toString();
            this.text = text;
            this.thrown = thrown;
            this.ruleReferencesForToken = ruleReferencesForToken;
            this.ambiguities = ambiguities;
        }

        public boolean hasAmbiguities() {
            return !ambiguities.isEmpty();
        }

        public List<? extends Ambiguity> ambiguities() {
            return ambiguities;
        }

        public String loggingInfo() {
            String errString = syntaxErrors.isEmpty() ? ""
                    : syntaxErrors.iterator().next().toString();
            return (isUnparsed() ? "UNPARSED-" : "PTP-") + id()
                    + " errs " + syntaxErrors.size()
                    + " tokens " + tokens.size()
                    + " textLength " + (text == null ? -1 : text.length())
                    + " for " + grammarName + ": " + errString;
        }

        public CharSequence textOf(ProxyToken tok) {
            int start = tok.getStartIndex();
            int end = tok.getEndIndex();
            if (end <= start) {
                return "";
            }
            int tp = tok.getType();
            if (tp == -1) {
                return "";
            }
            ProxyTokenType type = tokenTypes.get(tp + 1);
            if (type.literalName != null) {
                return type.literalName;
            }
            try {
                return text.subSequence(start, end);
            } catch (Exception ex) {
                // If the char sequence is a snapshot, it may have bit the dust
                Logger.getLogger(ParseTreeProxy.class.getName())
                        .log(Level.INFO, "Cannot get text - defunct snapshot?", ex);
                char[] c = new char[end - start];
                Arrays.fill(c, '-');
                return new String(c);
            }
        }

        public int referencesCount(ProxyToken tok) {
            int ix = tok.getTokenIndex();
            if (ix < 0 || ruleReferencesForToken == null || ruleReferencesForToken.length < ix || tok.getType() == -1) {
                return 0;
            }
            BitSet set = ruleReferencesForToken[ix];
            if (set == null) {
                return 0;
            }
            return set.cardinality();
        }

        public List<ParseTreeElement> referencedBy(ProxyToken tok) {
            int ix = tok.getTokenIndex();
            if (ix < 0 || ruleReferencesForToken == null || ruleReferencesForToken.length < ix || tok.getType() == -1) {
                return Collections.emptyList();
            }
            BitSet set = ruleReferencesForToken[ix];
            if (set == null || set.isEmpty()) {
                return Collections.emptyList();
            }
            List<ParseTreeElement> result = new ArrayList<>(set.cardinality());
            for (int bit = set.nextSetBit(0); bit >= 0; bit = set.nextSetBit(bit + 1)) {
                result.add(treeElements.get(bit));
            }
            Collections.sort(result, (a, b) -> {
                return Integer.compare(a.depth(), b.depth());
            });
            return result;
        }

        public Duration age() {
            return Duration.ofMillis(System.currentTimeMillis() - when);
        }

        public long createdAt() {
            return when;
        }

        public long id() {
            return id;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 23 * hash + Objects.hashCode(this.tokens);
            hash = 23 * hash + Objects.hashCode(this.tokenTypes);
            hash = 23 * hash + Objects.hashCode(this.root);
            hash = 23 * hash + Objects.hashCode(this.eofType);
            hash = 23 * hash + Objects.hashCode(this.treeElements);
            hash = 23 * hash + Objects.hashCode(this.syntaxErrors);
            hash = 23 * hash + Arrays.deepHashCode(this.parserRuleNames);
            hash = 23 * hash + Arrays.deepHashCode(this.channelNames);
            hash = 23 * hash + (this.hasParseErrors ? 1 : 0);
            hash = 23 * hash + Objects.hashCode(this.hashString);
            hash = 23 * hash + Objects.hashCode(this.grammarName);
            hash = 23 * hash + Objects.hashCode(this.grammarPath);
            hash = 23 * hash + (this.isUnparsed ? 1 : 0);
            // We cannot call hashCode() on a char sequence from the
            // lexer infrastructure; the tokens hash will take care of
            // containing the same information anyway
/*
java.lang.ArrayIndexOutOfBoundsException: 2441
	at org.netbeans.modules.editor.lib2.document.CharContent.charAt(CharContent.java:55)
	at org.netbeans.lib.lexer.TextLexerInputOperation.readExisting(TextLexerInputOperation.java:74)
	at org.netbeans.lib.lexer.LexerInputOperation.readExistingAtIndex(LexerInputOperation.java:166)
	at org.netbeans.spi.lexer.LexerInput$ReadText.charAt(LexerInput.java:342)
	at org.netbeans.lib.editor.util.CharSequenceUtilities.stringLikeHashCode(CharSequenceUtilities.java:46)
	at org.netbeans.lib.editor.util.AbstractCharSequence$StringLike.hashCode(AbstractCharSequence.java:96)
	at java.base/java.util.Objects.hashCode(Objects.java:116)
	at org.nemesis.antlr.live.parsing.extract.AntlrProxies$ParseTreeProxy.hashCode(AntlrProxies.java:215)

             */
//            hash = 23 * hash + Objects.hashCode(this.text);
            hash = 23 * hash + Objects.hashCode(this.thrown);
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
            final ParseTreeProxy other = (ParseTreeProxy) obj;
            if (this.hasParseErrors != other.hasParseErrors) {
                return false;
            }
            if (this.isUnparsed != other.isUnparsed) {
                return false;
            }
            if (!Objects.equals(this.hashString, other.hashString)) {
                return false;
            }
            if (!Objects.equals(this.grammarName, other.grammarName)) {
                return false;
            }
            if (!Objects.equals(this.grammarPath, other.grammarPath)) {
                return false;
            }
            if (!Objects.equals(this.tokens, other.tokens)) {
                return false;
            }
            if (!Objects.equals(this.tokenTypes, other.tokenTypes)) {
                return false;
            }
            if (!Objects.equals(this.root, other.root)) {
                return false;
            }
            if (!Objects.equals(this.eofType, other.eofType)) {
                return false;
            }
            if (!Objects.equals(this.treeElements, other.treeElements)) {
                return false;
            }
            if (!Objects.equals(this.syntaxErrors, other.syntaxErrors)) {
                return false;
            }
            if (!Arrays.deepEquals(this.parserRuleNames, other.parserRuleNames)) {
                return false;
            }
            if (!Arrays.deepEquals(this.channelNames, other.channelNames)) {
                return false;
            }
            if (!Objects.equals(this.thrown, other.thrown)) {
                return false;
            }
            return Objects.equals(this.ambiguities, other.ambiguities);
        }

        /**
         * Returns a ParseTreeProxy like this one, but with only some whitespace
         * characters stuck in an EOF token. Document inserts sometimes cause
         * the document to be briefly empty, and we don't want to run a full
         * parse to make a proxy for nothing.
         *
         * @param whitespace Some whatespace.
         * @return A new proxy
         */
        public ParseTreeProxy toEmptyParseTreeProxy(String whitespace) {
            whitespace = whitespace == null ? "" : whitespace;
            List<ProxyToken> newTokens = Arrays.asList(new ProxyToken(-1, 1, 0, 0, 0, 0, whitespace.length() - 1));
            ParseTreeElement root = new ParseTreeElement(ParseTreeElementKind.ROOT);
            return new ParseTreeProxy(newTokens, tokenTypes, root, eofType, Collections.<ParseTreeElement>emptyList(),
                    Collections.<ProxySyntaxError>emptySet(), parserRuleNames, channelNames, false, "x", grammarName,
                    Paths.get(grammarPath), whitespace, null, null, Collections.emptyList());
        }

        public RuntimeException thrown() {
            return thrown;
        }

        /**
         * IF an exception was thrown and wrapped, rethrow it.
         */
        public void rethrow() {
            if (thrown != null) {
                throw thrown;
            }
        }

        /**
         * Get the token at a given character offset within the document. Uses
         * binary search for performance.
         *
         * @param position The character offset
         * @return A token or null if out of range.
         */
        public ProxyToken tokenAtPosition(int position) {
            if (position < 0) {
                return null;
            }
            if (tokens.isEmpty()) {
                return null;
            }
            return tokenAtPosition(position, 0, tokens.size() - 1);
        }

        /**
         * Fetch the token at a given line and character offset within the text.
         * Uses binary search for performance.
         *
         * @param line
         * @param charPositionInLine
         * @return A token or null
         */
        public ProxyToken tokenAtLinePosition(int line, int charPositionInLine) {
            return tokenAtLinePosition(line, charPositionInLine, 0, tokens.size() - 1);
        }

        private ProxyToken tokenAtPosition(int position, int start, int end) {
            // Binary search
            int middle = start + ((end - start) / 2);
            ProxyToken first = tokens.get(start);
            ProxyToken last = tokens.get(end);
            ProxyToken mid = tokens.get(middle);
            if (first.contains(position)) {
                return first;
            } else if (last.contains(position)) {
                return last;
            } else if (mid.contains(position)) {
                return mid;
            } else if (first.startsAfter(position)) {
                return null;
            } else if (last.endsBefore(position)) {
                return null;
            } else if (start == middle || start == end || middle == end) {
                return null;
            } else if (mid.startsAfter(position)) {
                return tokenAtPosition(position, start, middle);
            } else if (mid.endsBefore(position)) {
                return tokenAtPosition(position, middle, end);
            }
            return null;
        }

        private ProxyToken tokenAtLinePosition(int line, int charOffset, int start, int end) {
            // Binary search
            int middle = start + ((end - start) / 2);
            ProxyToken first = tokens.get(start);
            ProxyToken last = tokens.get(end);
            ProxyToken mid = tokens.get(middle);
            if (first.contains(line, charOffset)) {
                return first;
            } else if (last.contains(line, charOffset)) {
                return last;
            } else if (mid.contains(line, charOffset)) {
                return mid;
            } else if (first.startsAfter(line, charOffset)) {
                return null;
            } else if (last.endsBefore(line, charOffset)) {
                return null;
            } else if (start == middle || start == end || middle == end) {
                return null;
            } else if (mid.startsAfter(line, charOffset)) {
                return ParseTreeProxy.this.tokenAtLinePosition(line, charOffset, start, middle);
            } else if (mid.endsBefore(line, charOffset)) {
                return ParseTreeProxy.this.tokenAtLinePosition(line, charOffset, middle, end);
            } else {
                if (mid.endsBefore(line, charOffset) && last.startsAfter(line, charOffset)) {
                    if (end - middle == 1) {
                        return tokens.get(middle + 1);
                    }
                    return ParseTreeProxy.this.tokenAtLinePosition(line, charOffset, middle, end);
                } else if (first.endsBefore(line, charOffset) && mid.startsAfter(line, charOffset)) {
                    if (middle - start == 1) {
                        return tokens.get(start + 1);
                    }
                    return ParseTreeProxy.this.tokenAtLinePosition(line, charOffset, start, middle);
                }
            }
            return null;
        }

        public CharSequence text() {
            return text;
        }

        public boolean isUnparsed() {
            return isUnparsed;
        }

        public String grammarName() {
            return grammarName;
        }

        public Path grammarPath() {
            return Paths.get(grammarPath);
        }

        public String mimeType() {
            return AdhocMimeTypes.mimeTypeForPath(grammarPath());
        }

        /**
         * Get the token type for a corresponding value (the list of types
         * starts at -1 for EOF, so this is value+1).
         *
         * @param value The int token type as returned by an antlr token
         * @return A token type
         */
        public ProxyTokenType tokenTypeForInt(int value) {
            return tokenTypes.get(value + 1);
        }

        /**
         * Allow tests to save serialized results.
         *
         * @param where A file path
         * @throws IOException If something goes wrong
         */
        public void save(Path where) throws IOException {
            try (OutputStream out = Files.newOutputStream(where, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                try (ObjectOutputStream oout = new ObjectOutputStream(out)) {
                    oout.writeObject(this);
                }
            }
        }

        /**
         * Allow tests to save serialized results.
         *
         * @param where A file path
         * @throws IOException If something goes wrong
         */
        public static ParseTreeProxy load(Path path) throws IOException, ClassNotFoundException {
            try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
                return load(in);
            }
        }

        public static ParseTreeProxy load(InputStream in) throws IOException, ClassNotFoundException {
            try (ObjectInputStream oin = new ObjectInputStream(in)) {
                return (ParseTreeProxy) oin.readObject();
            }
        }

        public List<ProxyTokenType> tokenTypes() {
            return Collections.unmodifiableList(tokenTypes);
        }

        public String tokenSequenceHash() {
            return hashString;
        }

        public boolean hasParseErrors() {
            return hasParseErrors;
        }

        public boolean hasSyntaxErrors() {
            return !syntaxErrors.isEmpty();
        }

        public boolean hasErrors() {
            return hasSyntaxErrors() || hasParseErrors();
        }

        public List<ParseTreeElement> allTreeElements() {
            return Collections.unmodifiableList(treeElements);
        }

        public List<ProxySyntaxError> syntaxErrors() {
            List<ProxySyntaxError> result = new LinkedList<>(syntaxErrors);
            Collections.sort(result);
            return result;
        }

        public ProxyTokenType eofTokenType() {
            return eofType;
        }

        public List<ProxyToken> tokens() {
            return Collections.unmodifiableList(tokens);
        }

        public List<String> parserRuleNames() {
            return Arrays.asList(parserRuleNames);
        }

        public List<String> channelNames() {
            return Arrays.asList(channelNames);
        }

        public String channelName(int channel) {
            return channel < channelNames.length ? channelNames[channel] : Integer.toString(channel);
        }

        public Iterable<ParseTreeElement> parseTreeRoots() {
            return root;
        }

        public int tokenTypeCount() {
            return tokenTypes.size(); // includes EOF
        }

        public int tokenCount() {
            return tokens.size();
        }

        public String summary() {
            return "{" + loggableMimeType(mimeType())
                    + ", parsed=" + !isUnparsed
                    + ", tokenTypes=" + tokenTypeCount()
                    + ", tokenCount=" + tokenCount() + ", ruleCount="
                    + (this.parserRuleNames == null ? 0 : this.parserRuleNames.length)
                    + " inputTextLength=" + (this.text == null ? "-1" : Integer.toString(this.text.length()))
                    + "}";
        }

        @Override
        public String toString() {
            return "ParseTreeProxy{" + "tokens=" + tokens + "\n tokenTypes="
                    + tokenTypes + "\n syntaxErrors="
                    + syntaxErrors + "\n parserRuleNames=" + Arrays.toString(parserRuleNames)
                    + "\n channelNames=" + Arrays.toString(channelNames) + "\n hasParseErrors="
                    + hasParseErrors + "\n hashString=" + hashString + "\n root=" + root + '}';
        }

        public List<ProxyToken> tokensForElement(ParseTreeElement element) {
            if (element instanceof TokenAssociated) {
                TokenAssociated ta = (TokenAssociated) element;
                List<ProxyToken> result = new ArrayList<>(ta.endTokenIndex() - ta.startTokenIndex());
                for (int i = ta.startTokenIndex(); i < ta.endTokenIndex(); i++) {
                    result.add(tokens.get(i));
                }
            }
            return Collections.emptyList();
        }
    }

    void addElement(ParseTreeElement el) {
        if (el.kind() == ParseTreeElementKind.ERROR) {
            // will be -1 token index
            return;
        }
        int index = treeElements.size();
        treeElements.add(el);
        if (el instanceof TokenAssociated) {
            TokenAssociated ta = (TokenAssociated) el;
            for (int tokenIndex = Math.max(0, ta.startTokenIndex()); tokenIndex < ta.endTokenIndex(); tokenIndex++) {
//                ProxyToken tok = tokens.get(tokenIndex);
//                tok.addRuleReference(el);
                if (ruleReferences == null) {
                    ruleReferences = new BitSet[tokens.size() + 1];
                }
                BitSet set = ruleReferences[tokenIndex];
                if (set == null) {
                    set = ruleReferences[tokenIndex] = new BitSet(treeElements.size());
                }
                set.set(index);
            }
        }
    }

    private final byte[] hashScratch = new byte[4];

    public AntlrProxies onToken(int type, int line, int charPositionInLine, int channel, int tokenIndex, int startIndex, int stopIndex, int trim) {
        ByteBuffer.wrap(hashScratch).putInt(type);
        hash.update(hashScratch);
//        if (text != null && !text.trim().isEmpty()) {
//            ByteBuffer.wrap(hashScratch).putInt(text.hashCode());
//            hash.update(hashScratch);
//        }
        ProxyToken token = new ProxyToken(type, line,
                charPositionInLine, channel, tokenIndex, startIndex, stopIndex, trim);
        if (startIndex > stopIndex && type != -1) {
            throw new IllegalArgumentException("Token ends before it starts: '"
                    + text + "' type=" + type + " startIndex=" + startIndex
                    + " stopIndex=" + stopIndex);
        }
        tokens.add(token);
        if (type != -1) {
            int typeIndex = type + 1; // eof is first
            if (typeIndex >= tokenTypes.size()) {
                throw new IllegalArgumentException("Token type index "
                        + typeIndex + " is > total token type count "
                        + tokenTypes.size() + " in " + tokenTypes
                        /* + " for '" + text */ + "' type " + type + " line "
                        + line + " charPos " + charPositionInLine
                        + " startIndex " + startIndex + " stopIndex " + stopIndex
                );
            }
//            ProxyTokenType ttype = this.tokenTypes.get(typeIndex);
//            ttype.addTokenInstance(token);
//        } else {
//            EOF_TYPE.addTokenInstance(token);
        }
        return this;
    }

    public void addTokenType(int type, String displayName, String symbolicName, String literalName) {
        tokenTypes.add(new ProxyTokenType(type, symbolicName, literalName, displayName));
    }

    public ParseTreeBuilder treeBuilder() {
        return new ParseTreeBuilder(root = new ParseTreeElement(ParseTreeElementKind.ROOT), this);
    }

    public void onSyntaxError(String message, int line, int charPositionInLine, int tokenIndex, int type, int startIndex, int stopIndex) {
        errors.add(new ProxyDetailedSyntaxError(message, line, charPositionInLine, tokenIndex, type, startIndex, stopIndex));
    }

    public void onSyntaxError(String message, int line, int charPositionInLine) {
        errors.add(new ProxySyntaxError(message, line, charPositionInLine));
    }

    public void setParserRuleNames(String[] ruleNames) {
        this.parserRuleNames = ruleNames;
    }

    public String[] parserRuleNames() {
        return parserRuleNames;
    }

    public void channelNames(String[] channelNames) {
        this.channelNames = channelNames;
    }

    public static class ProxySyntaxError implements Comparable<ProxySyntaxError>, Serializable {

        final String message;
        final int line;
        final int charPositionInLine;

        ProxySyntaxError(String message, int line, int charPositionInLine) {
            this.message = message;
            this.line = line;
            this.charPositionInLine = charPositionInLine;
        }

        @Override
        public String toString() {
            return line + ":" + charPositionInLine + " " + message;
        }

        public String message() {
            return message;
        }

        public int line() {
            return line;
        }

        public int charPositionInLine() {
            return charPositionInLine;
        }

        public int startIndex() {
            return -1;
        }

        public int stopIndex() {
            return -1;
        }

        public int endIndex() {
            return -1;
        }

        public boolean hasFileOffsetsAndTokenIndex() {
            return false;
        }

        public int tokenIndex() {
            return -1;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 53 * hash + Objects.hashCode(this.message);
            hash = 53 * hash + this.line;
            hash = 53 * hash + this.charPositionInLine;
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
            if (!(obj instanceof ProxySyntaxError)) {
                return false;
            }
            final ProxySyntaxError other = (ProxySyntaxError) obj;
            if (this.line != other.line) {
                return false;
            }
            if (this.charPositionInLine != other.charPositionInLine) {
                return false;
            }
            return Objects.equals(this.message, other.message);
        }

        @Override
        public int compareTo(ProxySyntaxError o) {

            int result = line > o.line
                    ? 1 : line == o.line
                            ? 0 : -1;
            if (result == 0) {
                result = charPositionInLine > o.charPositionInLine
                        ? 1 : charPositionInLine == o.charPositionInLine
                                ? 0 : -1;
            }
            return result;
        }
    }

    public static class ProxyDetailedSyntaxError extends ProxySyntaxError implements Serializable {

        private final int tokenIndex;
        private final int tokenType;
        private final int startIndex;
        private final int stopIndex;

        private ProxyDetailedSyntaxError(String message, int line,
                int charPositionInLine, int tokenIndex, int tokenType,
                int startIndex, int stopIndex) {
            super(message, line, charPositionInLine);
            this.tokenIndex = tokenIndex;
            this.tokenType = tokenType;
            this.startIndex = startIndex;
            this.stopIndex = stopIndex;
        }

        @Override
        public String toString() {
            return line + ":" + charPositionInLine
                    + "(" + startIndex + "," + stopIndex + ")="
                    + tokenIndex
                    + "<" + tokenType + ">"
                    + " " + message;
        }

        public int tokenType() {
            return tokenType;
        }

        @Override
        public int tokenIndex() {
            return tokenIndex;
        }

        @Override
        public int startIndex() {
            return startIndex;
        }

        @Override
        public int stopIndex() {
            return stopIndex;
        }

        @Override
        public int endIndex() {
            return stopIndex() + 1;
        }

        @Override
        public boolean hasFileOffsetsAndTokenIndex() {
            return true;
        }

        @Override
        public int compareTo(ProxySyntaxError o) {
            if (o instanceof ProxyDetailedSyntaxError) {
                ProxyDetailedSyntaxError d = (ProxyDetailedSyntaxError) o;
                int lsi = startIndex();
                int osi = d.startIndex();
                int result = lsi > osi ? 1 : lsi == osi ? 0 : -1;
                if (result == 0) {
                    int lst = stopIndex();
                    int ost = d.stopIndex();
                    result = lst > ost ? 1 : lst == ost ? 0 : -1;
                }
            }
            return super.compareTo(o);
        }
    }

    public static final class ProxyTokenType implements Comparable<ProxyTokenType>, Serializable {

        public final int type;
        public final String symbolicName;
        public final String literalName;
        public final String displayName;

        public ProxyTokenType(int type, String symbolicName, String literalName, String displayName) {
            this.type = type;
            this.symbolicName = symbolicName;
            if (literalName != null) {
                // Antlr puts literals in single quotes - not helpful for
                // determining if something is a single character element
                literalName = deQuote(literalName);
            }
            this.literalName = literalName;
            this.displayName = displayName;
        }

        @Override
        public int hashCode() {
            return 71 * (type + 3);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null) {
                return false;
            } else if (o instanceof ProxyTokenType) {
                ProxyTokenType ptt = (ProxyTokenType) o;
                return ptt.type == type;
            }
            return false;
        }

        public boolean isSingleCharacter() {
            return literalName != null && literalName.length() == 1;
        }

        public boolean isDelimiterLike() {
            if (isPunctuation()) {
                char c = literalName.charAt(0);
                switch (c) {
                    case '}':
                    case '{':
                    case '(':
                    case ')':
                    case '[':
                    case ']':
                        return true;
                }
            }
            return false;
        }

        public boolean isOperatorLike() {
            if (isPunctuation()) {
                char c = literalName.charAt(0);
                switch (c) {
                    case '+':
                    case '-':
                    case '/':
                    case '*':
                    case '%':
                    case '=':
                    case '>':
                    case '<':
                    case '^':
                    case '|':
                    case '&':
                        return true;
                }
            }
            return false;
        }

        public boolean isPunctuation() {
            return isSingleCharacter() && !Character.isAlphabetic(literalName.charAt(0))
                    && !Character.isDigit(literalName.charAt(0))
                    && !Character.isWhitespace(literalName.charAt(0));
        }

        private static String deQuote(String s) {
            if (s.length() > 1 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') {
                s = s.substring(1, s.length() - 2);
            }
            return s;
        }

        public boolean nameContains(String s) {
            for (String test : new String[]{displayName, literalName, symbolicName}) {
                if (test != null) {
                    String t = test.toLowerCase();
                    if (t.contains(s.toLowerCase())) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean isKeywordLike() {
            if (literalName != null) {
                boolean result = true;
                int len = literalName.length();
                if (len <= 1) {
                    return false;
                }
                for (int i = 0; i < literalName.length(); i++) {
                    result &= Character.isAlphabetic(literalName.charAt(i));
                    if (!result) {
                        break;
                    }
                }
                return result;
            }
            return false;
        }

        public String toString() {
            return name() + "(" + type + ")";
        }

        public String names() {
            StringBuilder sb = new StringBuilder();
            String[] names = new String[]{displayName, symbolicName, literalName};
            for (int i = 0; i < names.length; i++) {
                String s = names[i];
                if (s != null) {
                    if (sb.length() > 0) {
                        sb.append(" / ");
                    }
                    switch (i) {
                        case 0:
                            sb.append("displayName=");
                            break;
                        case 1:
                            sb.append("symbolicName=");
                            break;
                        case 2:
                            sb.append("literalName='");
                            break;
                        default:
                            throw new AssertionError(i);
                    }
                    sb.append(s);
                    if (i == 2) {
                        sb.append("'");
                    }
                }
            }
            if (sb.length() == 0) {
                sb.append("<no-name>");
            }
            sb.append(" (").append(type).append(")");
            return sb.toString();
        }

        public String programmaticName() {
            if (symbolicName != null) {
                return symbolicName;
            } else if (literalName != null) {
                return literalName;
            } else if (displayName != null) {
                return displayName;
            } else {
                return "<no-name>";
            }
        }

        public String name() {
            if (displayName != null) {
                return displayName;
            } else if (symbolicName != null) {
                return symbolicName;
            } else if (literalName != null) {
                return "'" + literalName + "'";
            } else {
                return "<no-name>";
            }
        }

        public int compareTo(ProxyTokenType other) {
            return type > other.type ? 1 : type == other.type ? 0 : -1;
        }
    }

    public static final class ProxyToken implements Comparable<ProxyToken>, Serializable {

        // We are making a few pretty safe assumptions here to minimize memory
        // footprint
        private final short type;
        private final int line;
        private final short charPositionInLine;
        private final byte channel;
        private final byte trim;
        private final int tokenIndex;
        private final int startIndex;
        private final short length;

        ProxyToken(int type, int line, int charPositionInLine, int channel, int tokenIndex, int startIndex, int stopIndex) {
            this(type, line, charPositionInLine, channel, tokenIndex, startIndex, stopIndex, 0);
        }

        ProxyToken(int type, int line, int charPositionInLine, int channel, int tokenIndex, int startIndex, int stopIndex, int trim) {
            this.type = (short) type;
            this.line = line;
            this.charPositionInLine = (short) charPositionInLine;
            this.channel = (byte) channel;
            this.tokenIndex = tokenIndex;
            this.startIndex = startIndex;
            this.length = (short) Math.max(0, (stopIndex - startIndex) + 1);
            assert trim <= length : "Trim " + trim + " < length " + length;
            this.trim = 0;
        }

        public boolean isWhitespace() {
            return trimmedLength() == 0;
        }

        public int trimmedLength() {
            return length - trim;
        }

        public int stopIndex() {
            return startIndex + length - 1;
        }

        public boolean startsAfter(int line, int position) {
            if (this.line > line) {
                return true;
            } else if (this.line < line) {
                return false;
            }
            return this.charPositionInLine > position;
        }

        public boolean endsBefore(int line, int position) {
            if (this.line < line) {
                return true;
            } else if (this.line > line) {
                return false;
            }
            return this.charPositionInLine + length <= position;
        }

        public boolean contains(int line, int charOffset) {
            if (line != this.line) {
                return false;
            }
            if (charOffset >= charPositionInLine && charOffset < charPositionInLine + length) {
                return true;
            }
            return false;
        }

        public boolean contains(int position) {
            int start = startIndex;
            int stop = stopIndex();
            if (type == -1) {
                // EOF will have an end before its start
                start = Math.min(startIndex, stop);
                stop = Math.max(startIndex, stop);
            }
            return position >= start && position <= stop;
        }

        public boolean isEOF() {
            return type == -1;
        }

        public boolean startsAfter(int position) {
            int ix = startIndex;
            if (type == -1) {
                // EOF will have an end before its start
                ix = Math.min(stopIndex(), startIndex);
            }
            return ix > position;
        }

        public boolean endsBefore(int position) {
            int ix = stopIndex();
            if (type == -1) {
                // EOF will have an end before its start
                ix = Math.max(stopIndex(), startIndex);
            }
            return ix < position;
        }

        public int length() {
            return length;
        }

        public int getType() {
            return type;
        }

        public int getLine() {
            return line;
        }

        public int getCharPositionInLine() {
            return charPositionInLine;
        }

        public int getChannel() {
            return channel;
        }

        public int getTokenIndex() {
            return tokenIndex;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public int getStopIndex() {
            return stopIndex();
        }

        /**
         * Convenience method to convert to netbeans bounds - returns
         * stopIndex+1.
         *
         * @return The stop index.
         */
        public int getEndIndex() {
            return startIndex + length;
        }

        public String toString() {
            return "ProxyToken@" + startIndex + ":"
                    + stopIndex() + "=" + tokenIndex + " line "
                    + line + " offset " + charPositionInLine
                    + " length " + length();
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + this.type;
            hash = 97 * hash + this.line;
            hash = 97 * hash + this.charPositionInLine;
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
            final ProxyToken other = (ProxyToken) obj;
            if (this.type != other.type) {
                return false;
            }
            if (this.line != other.line) {
                return false;
            }
            if (this.charPositionInLine != other.charPositionInLine) {
                return false;
            }
            return true;
        }

        @Override
        public int compareTo(ProxyToken o) {
            return tokenIndex > o.tokenIndex ? 1 : tokenIndex == o.tokenIndex ? 0 : -1;
        }
    }

    public static final class ParseTreeBuilder {

        private ParseTreeElement element;
        private final AntlrProxies proxies;

        ParseTreeBuilder(ParseTreeElement root, AntlrProxies proxies) {
            element = root;
            this.proxies = proxies;
        }

        public ParseTreeBuilder addRuleNode(String ruleName, int alternative,
                int sourceIntervalStart, int sourceIntervalEnd, int depth, Runnable run) {
            ParseTreeElement old = element;
            try {
                element = new RuleNodeTreeElement(ruleName, alternative, sourceIntervalStart, sourceIntervalEnd, depth);
                proxies.addElement(element);
                old.add(element);
                run.run();
            } finally {
                element = old;
            }
            return this;
        }

        public ParseTreeBuilder addTerminalNode(int tokenIndex, String tokenText, int currentDepth) {
            TerminalNodeTreeElement nue = new TerminalNodeTreeElement(tokenIndex, tokenText, currentDepth);
            proxies.addElement(nue);
            element.add(nue);
            return this;
        }

        public ParseTreeBuilder addErrorNode(int startToken, int endToken, int depth) {
            ErrorNodeTreeElement err = new ErrorNodeTreeElement(startToken, endToken, depth);
            proxies.addElement(err);
            element.add(err);
            proxies.hasParseErrors = true;
            return this;
        }

        public ParseTreeElement build() {
            return element;
        }
    }

    public static class ParseTreeElement implements Iterable<ParseTreeElement>, Serializable {

        private List<ParseTreeElement> children;
        private final ParseTreeElementKind kind;
        private ParseTreeElement parent;

        public ParseTreeElement(ParseTreeElementKind kind) {
            this.kind = kind;
        }

        public int depth() {
            return 0;
        }

        public String name() {
            return kind.toString();
        }

        public boolean isSameSpanAsParent() {
            return isSameSpanAs(parent);
        }

        public boolean isSameSpanAs(ParseTreeElement other) {
            if (this instanceof TokenAssociated && other instanceof TokenAssociated) {
                TokenAssociated a = (TokenAssociated) this;
                TokenAssociated b = (TokenAssociated) other;
                return a.startTokenIndex() == b.startTokenIndex()
                        && a.stopTokenIndex() == b.stopTokenIndex();
            }
            return false;
        }

        public ParseTreeElementKind kind() {
            return kind;
        }

        public ParseTreeElement parent() {
            return parent;
        }

        public boolean isRoot() {
            return kind == ParseTreeElementKind.ROOT;
        }

        public boolean isTopLevel() {
            return parent == null
                    ? false
                    : parent.kind == ParseTreeElementKind.ROOT;
        }

        void add(ParseTreeElement child) {
            child.parent = this;
            if (children == null) {
                children = new ArrayList<>(5);
            }
            children.add(child);
        }

        @Override
        public Iterator<ParseTreeElement> iterator() {
            return children == null ? Collections.emptyIterator()
                    : children.iterator();
        }

        @Override
        public String toString() {
            return toString("", new StringBuilder()).toString();
        }

        public String stringify() {
            return kind.name();
        }

        public StringBuilder toString(String indent, StringBuilder into) {
            into.append('\n').append(indent).append(stringify());
            if (children != null) {
                for (ParseTreeElement kid : children) {
                    kid.toString(indent + "  ", into);
                }
            }
            return into;
        }

        @Override
        public int hashCode() {
            int hash = kind.ordinal() * 67;
            hash = 67 * hash + Objects.hashCode(this.children);
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
            final ParseTreeElement other = (ParseTreeElement) obj;
            if (this.kind != other.kind) {
                return false;
            }
            return Objects.equals(this.children, other.children);
        }
    }

    public interface TokenAssociated extends Serializable {

        int startTokenIndex();

        int stopTokenIndex();

        default int endTokenIndex() {
            return stopTokenIndex() + 1;
        }
    }

    public static class RuleNodeTreeElement extends ParseTreeElement implements TokenAssociated {

        private final String ruleName;
        private final int alternative;
        private final int startTokenIndex;
        private final int endTokenIndex;
        private final short depth;

        public RuleNodeTreeElement(String ruleName, int alternative,
                int startTokenIndex, int endTokenIndex, int depth) {
            super(ParseTreeElementKind.RULE);
            this.ruleName = ruleName;
            this.alternative = alternative;
            this.startTokenIndex = startTokenIndex;
            this.endTokenIndex = endTokenIndex;
            this.depth = (short) depth;
        }

        @Override
        public int depth() {
            return depth;
        }

        @Override
        public String name() {
            return ruleName;
        }

        public int alternative() {
            return alternative;
        }

        @Override
        public int startTokenIndex() {
            return startTokenIndex;
        }

        @Override
        public int stopTokenIndex() {
            return endTokenIndex;
        }

        @Override
        public String stringify() {
            return ruleName + "(" + startTokenIndex + ":" + endTokenIndex + ")";
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 73 * hash + Objects.hashCode(this.ruleName);
            hash = 73 * hash + this.startTokenIndex;
            hash = 73 * hash + this.endTokenIndex;
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
            final RuleNodeTreeElement other = (RuleNodeTreeElement) obj;
            if (this.startTokenIndex != other.startTokenIndex) {
                return false;
            }
            if (this.endTokenIndex != other.endTokenIndex) {
                return false;
            }
            return Objects.equals(this.ruleName, other.ruleName);
        }
    }

    public static class ErrorNodeTreeElement extends ParseTreeElement implements TokenAssociated {

        private final int startTokenIndex;
        private final int stopTokenIndex;
        private final short depth;

        public ErrorNodeTreeElement(int startToken, int stopToken, int depth) {
            super(ParseTreeElementKind.ERROR);
            this.startTokenIndex = startToken;
            this.stopTokenIndex = stopToken;
            this.depth = (short) depth;
        }

        @Override
        public int depth() {
            return depth;
        }

        public int startTokenIndex() {
            return startTokenIndex;
        }

        public int stopTokenIndex() {
            return stopTokenIndex;
        }

        public String stringify() {
            return "<error>";
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 17 * hash + this.startTokenIndex;
            hash = 17 * hash + this.stopTokenIndex;
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
            final ErrorNodeTreeElement other = (ErrorNodeTreeElement) obj;
            if (this.startTokenIndex != other.startTokenIndex) {
                return false;
            }
            if (this.stopTokenIndex != other.stopTokenIndex) {
                return false;
            }
            return true;
        }
    }

    public static class TerminalNodeTreeElement extends ParseTreeElement implements TokenAssociated {

        private final int tokenIndex;
        private final String tokenText;
        private final short depth;

        public TerminalNodeTreeElement(int tokenIndex, String tokenText, int depth) {
            super(ParseTreeElementKind.TERMINAL);
            this.tokenIndex = tokenIndex;
            this.tokenText = tokenText;
            this.depth = (short) depth;
        }

        @Override
        public int depth() {
            return depth;
        }

        @Override
        public String stringify() {
            return "'" + tokenText + "'";
        }

        @Override
        public String name() {
            return tokenText;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 37 * hash + this.tokenIndex;
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
            final TerminalNodeTreeElement other = (TerminalNodeTreeElement) obj;
            if (this.tokenIndex != other.tokenIndex) {
                return false;
            }
            return true;
        }

        @Override
        public int startTokenIndex() {
            return tokenIndex;
        }

        @Override
        public int stopTokenIndex() {
            return tokenIndex;
        }
    }

    public enum ParseTreeElementKind implements Serializable {
        ROOT,
        RULE,
        TERMINAL,
        ERROR;
    }

    /**
     * Wraps an exception which may reference types from the isolated
     * classloader, ensuring no such objects are propagated out.
     */
    public static final class ProxyException extends RuntimeException {

        private final StackTraceElement[] stackCopy;
        private final String origType;

        ProxyException(Throwable thrown) {
            super(thrown.getMessage());
            if (thrown.getCause() != null) {
                initCause(new ProxyException(thrown.getCause()));
            }
            origType = thrown.getClass().getName();
            StackTraceElement[] orig = thrown.getStackTrace();
            StackTraceElement[] copy;
            if (orig != null) { // no debug info
                copy = new StackTraceElement[orig.length];
                for (int i = 0; i < orig.length; i++) {
                    StackTraceElement e = orig[i];
                    StackTraceElement ste = new StackTraceElement(e.getClassName(),
                            e.getMethodName(), e.getFileName(), e.getLineNumber());
                    copy[i] = ste;
                }
            } else {
                copy = new StackTraceElement[0];
            }
            stackCopy = copy;
            for (Throwable t : thrown.getSuppressed()) {
                addSuppressed(new ProxyException(t));
            }
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            // do nothing
            return this;
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return stackCopy;
        }

        @Override
        public String toString() {
            return origType + ": " + getMessage();
        }
    }

    public static final class Ambiguity {

        public final int decision;
        public final int ruleIndex;
        public final String ruleName;
        public final BitSet conflictingAlternatives;
        public final int startOffset;
        public final int stopOffset;

        public Ambiguity(int decision, int ruleIndex, String ruleName, BitSet conflictingAlternatives,
                int startOffset, int stopOffset) {
            this.decision = decision;
            this.ruleIndex = ruleIndex;
            this.ruleName = ruleName;
            this.conflictingAlternatives = conflictingAlternatives;
            this.startOffset = startOffset;
            this.stopOffset = stopOffset;
        }

        public String identifier() {
            return ruleName + ":" + decision + ":" + ruleIndex + ":" + startOffset + "-" + stopOffset;
        }

        public int start() {
            return startOffset;
        }

        public int end() {
            return stopOffset + 1;
        }

        public int stop() {
            return stopOffset;
        }

        public String toString() {
            return "Ambiguity(" + ruleName + " / " + ruleIndex + " / " + decision
                    + " @ " + startOffset + ":" + stopOffset + " alts: "
                    + conflictingAlternatives + ")";
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + this.decision;
            hash = 59 * hash + this.ruleIndex;
            hash = 59 * hash + Objects.hashCode(this.ruleName);
            hash = 59 * hash + Objects.hashCode(this.conflictingAlternatives);
            hash = 59 * hash + this.startOffset;
            hash = 59 * hash + this.stopOffset;
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
            final Ambiguity other = (Ambiguity) obj;
            if (this.decision != other.decision) {
                return false;
            }
            if (this.ruleIndex != other.ruleIndex) {
                return false;
            }
            if (this.startOffset != other.startOffset) {
                return false;
            }
            if (this.stopOffset != other.stopOffset) {
                return false;
            }
            if (!Objects.equals(this.ruleName, other.ruleName)) {
                return false;
            }
            if (!Objects.equals(this.conflictingAlternatives, other.conflictingAlternatives)) {
                return false;
            }
            return true;
        }
    }
}
