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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
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

    public static final String ERRONEOUS_TOKEN_NAME = "$ERRONEOUS";
    private final List<ProxyToken> tokens = new ArrayList<>();
    private final List<ProxyTokenType> tokenTypes = new ArrayList<>(50);
    private final Set<Ambiguity> ambiguities = new HashSet<>(10);
    private ParseTreeElement root = new ParseTreeElement(ParseTreeElementKind.ROOT);
    private final ProxyTokenType EOF_TYPE = new ProxyTokenType(-1, "EOF", "", "EOF");
    private final List<ParseTreeElement> treeElements = new ArrayList<>(50);
    private final Set<ProxySyntaxError> errors = new TreeSet<>();
    private String[] parserRuleNames = new String[0];
    private String[] lexerRuleNames = new String[0];
    private String[] channelNames = new String[0];
    private String[] modeNames = new String[]{"DEFAULT_MODE"};
    private int defaultModeIndex = 0;
    private boolean hasParseErrors;
    private MessageDigest hash;
    private RuntimeException thrown;
    private final String grammarName;
    private final Path grammarPath;
    private final CharSequence text;
    private BitSet[] ruleReferences;
    private final BitSet presentRules = new BitSet(64);
    private final BitSet presentTokens = new BitSet(64);
    private String grammarTokensHash = "--tokensHash--";
    private long tokenNamesChecksum;
    private boolean lexerGrammar;

    public AntlrProxies(String grammarName, Path grammarPath, CharSequence text) {
        this.grammarName = grammarName;
        this.grammarPath = grammarPath;
        tokenTypes.add(EOF_TYPE);
        newHash();
        this.text = text;
    }

    public void setLexerGrammar(boolean val) {
        lexerGrammar = val;
    }

    public void setTokenNamesChecksum(long val) {
        this.tokenNamesChecksum = val;
    }

    public void setGrammarTokensHash(String val) {
        if (val != null) {
            grammarTokensHash = val;
        }
    }

    public void onAmbiguity(Ambiguity ambiguity) {
        ambiguities.add(ambiguity);
    }

    public void setModeInfo(int defaultMode, String[] modeNames) {
        defaultModeIndex = 0;
        if (modeNames != null) {
            this.modeNames = modeNames;
        }
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
                grammarName, grammarPath, text, thrown, ruleReferences, ambiguities,
                lexerRuleNames, presentRules, defaultModeIndex, modeNames,
                grammarTokensHash, tokenNamesChecksum, presentTokens,
                lexerGrammar);
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
        ProxyTokenType errorType = new ProxyTokenType(1, ERRONEOUS_TOKEN_NAME, ERRONEOUS_TOKEN_NAME, ERRONEOUS_TOKEN_NAME);

        ProxyToken eof = new ProxyToken(-1, 0, 0, 0, 1, text.length(), text.length(), 0);
        ParseTreeElement root = new ParseTreeElement(ParseTreeElementKind.ROOT);
        ParseTreeElement child = new RuleNodeTreeElement(0, 0, 0, 1, 1);
        ProxyTokenType EOF_TYPE = new ProxyTokenType(-1, "EOF", "", "EOF");
        root.add(child);
        List<ProxyTokenType> tokenTypes = Arrays.asList(EOF_TYPE, textType, errorType);

        List<ProxyToken> tokens;
        if (text.length() < ProxyToken.MAX_TOKEN_LENGTH) {
            ProxyToken all = new ProxyToken(0, 0, 0, 0, 0, 0, text.length() - 1, 0);
            tokens = Arrays.asList(all, eof);
        } else {
            ProxyToken[] split = splitToken(0, 0, 0, 0, 0, 0, text.length() - 1, 0);
            tokens = new ArrayList<>(split.length + 1);
            tokens.addAll(Arrays.asList(split));
            tokens.add(eof);
        }
        BitSet empty = new BitSet(1);
        ParseTreeProxy prox = new ParseTreeProxy(tokens, tokenTypes, root, EOF_TYPE, Arrays.asList(root, child),
                Collections.emptySet(), new String[]{"text"}, new String[]{"default"},
                false, Long.toString(text.hashCode(), 36),
                grammarName, pth, text, null, new BitSet[1], Collections.emptySet(), new String[0],
                empty, 0, null, "-", 0, empty, false);
        prox.isUnparsed = true;
        return prox;
    }

    public void setLexerRuleNames(String[] ruleNames) {
        this.lexerRuleNames = ruleNames;
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
        private final Ambiguity[] ambiguities;
        private final ParseTreeElement root;
        private final ProxyTokenType eofType;
        private final List<ParseTreeElement> treeElements;
        private final Set<ProxySyntaxError> syntaxErrors;
        private final String[] parserRuleNames;
        private final String[] channelNames;
        private final String[] lexerRuleNames;
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
        private SortedSet<String> allRuleNames;
        private final BitSet presentRules;
        private final String[] modeNames;
        private final short defaultMode;
        private final String grammarTokensHash;
        private final long tokenNamesChecksum;
        private final BitSet presentTokens;
        private final boolean lexerGrammar;

        ParseTreeProxy(List<ProxyToken> tokens, List<ProxyTokenType> tokenTypes,
                ParseTreeElement root, ProxyTokenType eofType, List<ParseTreeElement> treeElements,
                Set<ProxySyntaxError> errors, String[] parserRuleNames,
                String[] channelNames, boolean hasParseErrors, String hashString, String grammarName,
                Path grammarPath, CharSequence text, RuntimeException thrown,
                BitSet[] ruleReferencesForToken, Set<Ambiguity> ambiguities, String[] lexerRuleNames,
                BitSet presentRules, int defaultMode, String[] modeNames,
                String grammarTokensHash, long tokenNamesChecksum, BitSet presentTokens,
                boolean lexerGrammar) {
            this.tokens = Collections.unmodifiableList(tokens);
            this.grammarTokensHash = grammarTokensHash;
            this.presentTokens = presentTokens;
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
            this.ambiguities = toArray(ambiguities);
            this.lexerRuleNames = lexerRuleNames;
            this.modeNames = modeNames == null ? new String[]{"DEFAULT_MODE"} : modeNames;
            this.defaultMode = (short) defaultMode;
            this.presentRules = presentRules;
            this.tokenNamesChecksum = tokenNamesChecksum;
            this.lexerGrammar = lexerGrammar;
        }

        public boolean isLexerGrammar() {
            return lexerGrammar;
        }

        private static final Ambiguity[] EMPTY_AMBIGUITIES = new Ambiguity[0];

        static Ambiguity[] toArray(Set<Ambiguity> all) {
            if (all.isEmpty()) {
                return EMPTY_AMBIGUITIES;
            }
            Ambiguity[] ambs = all.toArray(new Ambiguity[all.size()]);
            Arrays.sort(ambs);
            return ambs;
        }

        private List<ErrorNodeTreeElement> allErrorElements = null;

        public List<ErrorNodeTreeElement> allErrorElements() {
            if (allErrorElements == null) {
                allErrorElements = collectErrorElements();
            }
            return allErrorElements;
        }

        private List<ErrorNodeTreeElement> collectErrorElements() {
            List<ErrorNodeTreeElement> result = new ArrayList<>(treeElements.size());
            for (ParseTreeElement el : treeElements) {
                if (el instanceof ErrorNodeTreeElement) {
                    result.add((ErrorNodeTreeElement) el);
                }
            }
            return result;
        }

        public String ruleNameFor(Ambiguity amb) {
            int rule = amb.ruleIndex();
            if (rule < 0 || rule >= parserRuleNames.length) {
                return "unknown";
            }
            return parserRuleNames[rule];
        }

        public String ruleNameFor(RuleNodeTreeElement ruleElement) {
            return parserRuleNames[ruleElement.ruleIndex()];
        }

        public CharSequence textOf(Ambiguity ambig) {
            StringBuilder sb = new StringBuilder();
            int stop = ambig.stop();
            for (int i = ambig.start(); i < stop; i++) {
                sb.append(textOf(tokens.get(i)));
            }
            return sb.toString();
        }

        public List<ProxyToken> tokensIn(Ambiguity ambig) {
            int start = ambig.start();
            int stop = ambig.stop();
            List<ProxyToken> result = new ArrayList<>((stop + 1) - start);
            for (int i = start; i <= stop; i++) {
                result.add(tokens.get(i));
            }
            return result;
        }

        public long tokenNamesChecksum() {
            return tokenNamesChecksum;
        }

        public String grammarTokensHash() {
            return grammarTokensHash;
        }

        public String[] modeNames() {
            return modeNames;
        }

        public int defaultMode() {
            return defaultMode;
        }

        public boolean isDefaultMode(ProxyToken tok) {
            int mode = tok.mode();
            return mode == defaultMode || mode >= modeNames.length || mode < 0;
        }

        public boolean isErroneousToken(ProxyToken tok) {
            return tok.getType() == tokenTypes.size() - 1;
        }

        public String modeName(ProxyToken tok) {
            int md = tok.mode();
            if (md >= 0 && md < modeNames.length) {
                return modeNames[md];
            }
            return "DEFAULT_MODE";
        }

        public List<String> lexerRuleNames() {
            return Arrays.asList(lexerRuleNames);
        }

        private Set<String> presentRuleNames;

        public Set<String> presentRuleNames() {
            if (presentRuleNames == null) {
                presentRuleNames = new HashSet<>(presentRules.cardinality());
                for (int bit = presentRules.nextSetBit(0); bit >= 0; bit = presentRules.nextSetBit(bit + 1)) {
                    presentRuleNames.add(parserRuleNames[bit]);
                }
                for (int bit = presentTokens.nextSetBit(0); bit >= 0; bit = presentTokens.nextSetBit(bit + 1)) {
                    ProxyTokenType type = tokenTypes.get(bit);
                    presentRuleNames.add(type.symbolicName);
                }
            }
            return presentRuleNames;
        }

        public BitSet presentTokenIds() {
            return presentTokens;
        }

        public BitSet presentRuleIds() {
            return presentRules;
        }

        public SortedSet<String> allRuleNames() {
            if (allRuleNames == null) {
                SortedSet<String> result = new TreeSet<>(Arrays.asList(lexerRuleNames));
                result.addAll(Arrays.asList(parserRuleNames));
                return allRuleNames = Collections.unmodifiableSortedSet(result);
            }
            return allRuleNames;
        }

        public boolean hasAmbiguities() {
            return ambiguities.length > 0;
        }

        public List<? extends Ambiguity> ambiguities() {
            return Arrays.asList(ambiguities);
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
            if (ix < 0 || ruleReferencesForToken == null || ruleReferencesForToken.length <= ix || tok.getType() == -1) {
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
            if (ix < 0 || ruleReferencesForToken == null || ruleReferencesForToken.length <= ix || tok.getType() == -1) {
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
            if (!Arrays.equals(modeNames, other.modeNames)) {
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
            List<ProxyToken> newTokens = Arrays.asList(new ProxyToken(-1, 1, 0, 0, 0, 0, whitespace.length() - 1, 0, 0));
            ParseTreeElement root = new ParseTreeElement(ParseTreeElementKind.ROOT);
            BitSet emptyBits = new BitSet(1);
            return new ParseTreeProxy(newTokens, tokenTypes, root, eofType, Collections.<ParseTreeElement>emptyList(),
                    Collections.<ProxySyntaxError>emptySet(), parserRuleNames, channelNames, false, "x", grammarName,
                    Paths.get(grammarPath), whitespace, null, null, Collections.emptySet(), lexerRuleNames,
                    emptyBits, defaultMode, modeNames, grammarTokensHash, tokenNamesChecksum, emptyBits,
                    lexerGrammar);
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
            return tokens;
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

        public List<ProxyToken> tokensForElement(ParseTreeElement el) {
            if (el instanceof TokenAssociated) {
                TokenAssociated ta = (TokenAssociated) el;
                int start = ta.startTokenIndex();
                int end = ta.endTokenIndex();
                if (start == -1 || end == -1) {
                    return Collections.emptyList();
                }
                return tokens().subList(start, end);
            }
            return Collections.emptyList();
        }
    }

    void addElement(ParseTreeElement el) {
        switch (el.kind()) {
            case RULE:
                presentRules.set(((RuleNodeTreeElement) el).ruleIndex());
        }
        int index = treeElements.size();
        treeElements.add(el);
        if (el instanceof TokenAssociated) {
            TokenAssociated ta = (TokenAssociated) el;
            if (!el.isSynthetic()) {
                for (int tokenIndex = Math.max(0, ta.startTokenIndex()); tokenIndex < ta.endTokenIndex(); tokenIndex++) {
                    if (ruleReferences == null) {
                        ruleReferences = new BitSet[tokens.size() + 1];
                    }
                    if (tokenIndex >= ruleReferences.length) {
                        // Happens when we are adding elements for a lexer-only grammar
                        ruleReferences = Arrays.copyOf(ruleReferences, tokenIndex + 16);
                    }
                    BitSet set = ruleReferences[tokenIndex];
                    if (set == null) {
                        set = ruleReferences[tokenIndex] = new BitSet(treeElements.size());
                    }
                    set.set(index);
                }
            }
        }
    }

    private final byte[] hashScratch = new byte[4];

    public AntlrProxies onToken(int type, int line, int charPositionInLine, int channel, int tokenIndex, int startIndex, int stopIndex, int trim, int mode) {
        ByteBuffer.wrap(hashScratch).putInt(type);
        hash.update(hashScratch);
        if (type > -1) {
            ProxyTokenType typeType = tokenTypes.get(type + 1);
            if (typeType.symbolicName != null) {
                presentTokens.set(type + 1);
            }
        }
        ProxyToken token = new ProxyToken(type, line,
                charPositionInLine, channel, tokenIndex, startIndex, stopIndex, trim, mode);
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

    public void onSyntaxError(String message, int line, int charPositionInLine, int streamPosition) {
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
        final long lineAndPosition;

        ProxySyntaxError(String message, int line, int charPositionInLine) {
            this.message = message;
            lineAndPosition = pack(line, charPositionInLine);
        }

        @Override
        public String toString() {
            return line() + ":" + charPositionInLine() + " " + message;
        }

        public String message() {
            return message;
        }

        public int line() {
            return unpackLeft(lineAndPosition);
        }

        public int charPositionInLine() {
            return unpackRight(lineAndPosition);
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
            hash = 53 * hash + this.line();
            hash = 53 * hash + this.charPositionInLine();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null || obj.getClass() != ProxySyntaxError.class) {
                return false;
            }
            final ProxySyntaxError other = (ProxySyntaxError) obj;
            return other.lineAndPosition == lineAndPosition;
        }

        @Override
        public int compareTo(ProxySyntaxError o) {

            int result = line() > o.line()
                    ? 1 : line() == o.line()
                    ? 0 : -1;
            if (result == 0) {
                result = charPositionInLine() > o.charPositionInLine()
                        ? 1 : charPositionInLine() == o.charPositionInLine()
                        ? 0 : -1;
            }
            return result;
        }
    }

    public static class ProxyDetailedSyntaxError extends ProxySyntaxError implements Serializable {

        private final long tokenIndexType;
        private final long startStop;

        private ProxyDetailedSyntaxError(String message, int line,
                int charPositionInLine, int tokenIndex, int tokenType,
                int startIndex, int stopIndex) {
            super(message, line, charPositionInLine);
            tokenIndexType = pack(tokenIndex, tokenType);
            startStop = pack(startIndex, stopIndex);
        }

        @Override
        public String toString() {
            return line() + ":" + charPositionInLine()
                    + "(" + startIndex() + "," + stopIndex() + ")="
                    + tokenIndex()
                    + "<" + tokenType() + ">"
                    + " " + message;
        }

        public int tokenType() {
            return unpackRight(tokenIndexType);
        }

        @Override
        public int tokenIndex() {
            return unpackLeft(tokenIndexType);
        }

        @Override
        public int startIndex() {
            return unpackLeft(startStop);
        }

        @Override
        public int stopIndex() {
            return unpackRight(startStop);
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
                return result;
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
                return ptt.type == type && Objects.equals(displayName, ptt.displayName);
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

        @Override
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

        private String category;

        public String category() {
            if (category == null) {
                category = _categorize(this);
            }
            return category;
        }
    }

    @SuppressWarnings("StringEquality")
    private static String _categorize(ProxyTokenType type) {
        if (AntlrProxies.ERRONEOUS_TOKEN_NAME == type.name()) {
            return "errors";
        }
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
        if (type.nameContains("ident")) {
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
        if (type.nameContains("number")
                || type.nameContains("integer")
                || type.nameContains("float")
                || type.nameContains("int")
                || type.nameContains("num")) {
            return "numbers";
        }
        if (type.nameContains("field")) {
            return "field";
        }
        if (type.nameContains("comment") || type.nameContains("cmt")) {
            return "comment";
        }
        if (type.nameContains("white") || type.nameContains("ws")) {
            return "whitespace";
        }
        return "default";
    }

    private static ProxyToken[] splitToken(int type, int line, int charPositionInLine, int channel, int tokenIndex,
            int startIndex, int stopIndex, int mode) {
        int length = tokenLength(startIndex, stopIndex);
        if (length < ProxyToken.MAX_TOKEN_LENGTH) {
            return new ProxyToken[]{
                new ProxyToken(type, line, charPositionInLine, channel, tokenIndex, startIndex, stopIndex, type, mode)
            };
        } else {
            int count = length / ProxyToken.MAX_TOKEN_LENGTH;
            int rem = length % ProxyToken.MAX_TOKEN_LENGTH;
            if (rem > 0) {
                count++;
            }
            ProxyToken[] result = new ProxyToken[count];
            for (int i = 0; i < count; i++) {
                int startOffset = (i * ProxyToken.MAX_TOKEN_LENGTH);
                int start = startIndex + startOffset;
                // XXX we are fudging line / char position
                if (i == count - 1 && rem != 0) {
                    result[i] = new ProxyToken(type, line + i, charPositionInLine, channel, tokenIndex + i,
                            start, start + rem - 1, type, mode);
                } else {
                    result[i] = new ProxyToken(type, line + i, charPositionInLine, channel, tokenIndex + i,
                            start, start + ProxyToken.MAX_TOKEN_LENGTH, type, mode);
                }
            }
            return result;
        }
    }

    public static final class ProxyToken implements Comparable<ProxyToken>, Serializable {

        static final long serialVersionUID = 2;
        // We are making a few pretty safe assumptions here to minimize memory
        // footprint.  Note we are only using 6 bytes of lineChannelTrim -
        // the top two bytes are available.  This is a little nuts, but there
        // can be hundreds of thousands of instances in a live VM, and paying
        // for fields gets expensive.
        private final long typeCharPositionLengthMode;
        private final long startTokenIndex;
        private final long lineChannelTrim;
        public static int MAX_TOKEN_LENGTH = 65535;

        ProxyToken(int type, int line, int charPositionInLine, int channel, int tokenIndex,
                int startIndex, int stopIndex, int mode) {
            this(type, line, charPositionInLine, channel, tokenIndex, startIndex, stopIndex, 0, mode);
        }

        ProxyToken(int type, int line, int charPositionInLine, int channel, int tokenIndex,
                int startIndex, int stopIndex, int trim, int mode) {

            int length = type == -1 ? 0 : tokenLength(startIndex, stopIndex);
            if (length > MAX_TOKEN_LENGTH) {
                throw new IllegalArgumentException("Max token " + MAX_TOKEN_LENGTH
                        + " length exceeded.  Split the tokens.");
            }
            typeCharPositionLengthMode = pack(type + 1, charPositionInLine,
                    length, mode);
            startTokenIndex = pack(startIndex, tokenIndex);
            lineChannelTrim = pack3(line, channel, trim);
        }

        public int mode() {
            return unpackD(typeCharPositionLengthMode);
        }

        public boolean isWhitespace() {
            return trimmedLength() == 0;
        }

        public int trimmedLength() {
            return length() - trim();
        }

        public int trim() {
            return unpack3RightByte(lineChannelTrim);
        }

        public boolean startsAfter(int line, int position) {
            int myLine = getLine();
            if (myLine > line) {
                return true;
            } else if (myLine < line) {
                return false;
            }
            return this.getCharPositionInLine() > position;
        }

        public boolean endsBefore(int line, int position) {
            int myLine = getLine();
            if (myLine < line) {
                return true;
            } else if (myLine > line) {
                return false;
            }
            return this.getCharPositionInLine() + length() <= position;
        }

        public boolean contains(int line, int charOffset) {
            if (line != this.getLine()) {
                return false;
            }
            int cp = getCharPositionInLine();
            if (charOffset >= cp && charOffset < cp + length()) {
                return true;
            }
            return false;
        }

        public boolean contains(int position) {
            int start = getStartIndex();
            int stop = getStopIndex();
            if (isEOF()) {
                // EOF will have an end before its start
                start = Math.min(start, stop);
                stop = Math.max(start, stop);
            }
            return position >= start && position <= stop;
        }

        public boolean isEOF() {
            return getType() == -1;
        }

        public boolean startsAfter(int position) {
            int ix = getStartIndex();
            if (isEOF()) {
                // EOF will have an end before its start
                ix = Math.min(getStopIndex(), ix);
            }
            return ix > position;
        }

        public boolean endsBefore(int position) {
            int ix = getStopIndex();
            if (isEOF()) {
                // EOF will have an end before its start
                ix = Math.max(ix, getStartIndex());
            }
            return ix < position;
        }

        public int length() {
            return unpackC(typeCharPositionLengthMode);
        }

        public int getType() {
            return unpackA(typeCharPositionLengthMode) - 1;
        }

        public int getLine() {
            return unpack3Int(lineChannelTrim);
        }

        public int getCharPositionInLine() {
            return unpackB(typeCharPositionLengthMode);
        }

        public int getChannel() {
            return unpack3LeftByte(lineChannelTrim);
        }

        public int getTokenIndex() {
            return unpackRight(startTokenIndex);
        }

        public int getStartIndex() {
            return unpackLeft(startTokenIndex);
        }

        public int getStopIndex() {
            return getEndIndex() - 1;
        }

        /**
         * Convenience method to convert to netbeans bounds - returns
         * stopIndex+1.
         *
         * @return The stop index.
         */
        public int getEndIndex() {
            return getStartIndex() + length();
        }

        @Override
        public String toString() {
            return "ProxyToken@" + getStartIndex() + ":"
                    + getStopIndex() + " ix " + getTokenIndex() + " line "
                    + getLine() + ":" + getCharPositionInLine()
                    + " length " + length() + " type "
                    + getType() + " mode " + mode()
                    + " trim " + trim();
        }

        @Override
        public int hashCode() {
            long hash = typeCharPositionLengthMode
                    ^ (startTokenIndex >>> 24) ^ (lineChannelTrim << 16);
            return (int) (hash ^ (hash >> 32));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final ProxyToken other = (ProxyToken) obj;
            return other.typeCharPositionLengthMode == typeCharPositionLengthMode
                    && other.startTokenIndex == startTokenIndex
                    && other.lineChannelTrim == lineChannelTrim;
        }

        @Override
        public int compareTo(ProxyToken o) {
            int tokenIndex = getTokenIndex();
            int otherTokenIndex = o.getTokenIndex();
            return tokenIndex > otherTokenIndex
                    ? 1
                    : tokenIndex == otherTokenIndex
                            ? 0
                            : -1;
        }
    }

    public static final class ParseTreeBuilder {

        private ParseTreeElement element;
        private final AntlrProxies proxies;

        ParseTreeBuilder(ParseTreeElement root, AntlrProxies proxies) {
            element = root;
            this.proxies = proxies;
        }

        public ParseTreeBuilder addRuleNode(int ruleIndex, int alternative,
                int firstToken, int lastToken, int depth, Runnable run) {
            ParseTreeElement old = element;
            try {
                element = new RuleNodeTreeElement(ruleIndex, alternative, firstToken, lastToken, depth);
                proxies.addElement(element);
                old.add(element);
                run.run();
            } finally {
                element = old;
            }
            return this;
        }

        public ParseTreeBuilder addTerminalNode(int tokenIndex, int currentDepth) {
            TerminalNodeTreeElement nue = new TerminalNodeTreeElement(tokenIndex, currentDepth);
            proxies.addElement(nue);
            element.add(nue);
            return this;
        }

        public ParseTreeBuilder addErrorNode(int startToken, int endToken, int depth, int tokenStart, int tokenStop, String tokenText, int tokenType) {
            ErrorNodeTreeElement err = new ErrorNodeTreeElement(startToken, endToken, depth,
                    tokenStart, tokenStop, tokenText, tokenType);
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

        /**
         * True for error nodes that represent missing tokens, in which case the
         * start and end token indices will be -1.
         *
         * @return True if they are synthetic
         */
        public boolean isSynthetic() {
            return false;
        }

        public int depth() {
            return 0;
        }

        public String name(ParseTreeProxy proxy) {
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

        protected void add(ParseTreeElement child) {
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

        public String stringify(ParseTreeProxy proxy) {
            return kind.name();
        }

        public StringBuilder toString(String indent, StringBuilder into) {
            into.append('\n').append(indent).append(kind.name());
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

    public final static class RuleNodeTreeElement extends ParseTreeElement implements TokenAssociated {

        private final long startStop;
        private final int altDepth;
        private final int ruleIndex;

        public RuleNodeTreeElement(int ruleIndex, int alternative,
                int startTokenIndex, int stopTokenIndex, int depth) {
            super(ParseTreeElementKind.RULE);
            altDepth = packShort(alternative, depth);
            this.startStop = pack(startTokenIndex, stopTokenIndex);
            this.ruleIndex = ruleIndex;
        }

        public String name(ParseTreeProxy prx) {
            return prx.ruleNameFor(this);
        }

        @Override
        public int depth() {
            return unpackShortRight(altDepth);
        }

        public int ruleIndex() {
            return ruleIndex;
        }

        public int alternative() {
            return unpackShortLeft(altDepth);
        }

        @Override
        public int startTokenIndex() {
            return unpackLeft(startStop);
        }

        @Override
        public int stopTokenIndex() {
            return unpackRight(startStop);
        }

        @Override
        public String stringify(ParseTreeProxy proxy) {
            return proxy.ruleNameFor(this) + "(" + startTokenIndex() + ":" + stopTokenIndex() + ")";
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 11107 * hash + ruleIndex;
            hash += 73 * (startStop ^ (startStop << 32));
            hash += 111 * altDepth;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final RuleNodeTreeElement other = (RuleNodeTreeElement) obj;
            return startStop == other.startStop && altDepth == other.altDepth
                    && ruleIndex == other.ruleIndex;
        }
    }

    public static class ErrorNodeTreeElement extends ParseTreeElement implements TokenAssociated {

        private final long startStopTokenIndex;
        private final long tokenStartStop;
        private final int typeAndDepth;
        private final String text;

        public ErrorNodeTreeElement(int startToken, int stopToken, int depth, int tokenStart,
                int tokenStop, String tokenText, int tokenType) {
            super(ParseTreeElementKind.ERROR);
            // We need to hold the text - it will sometimes be "missing ;" or
            // similar from the parser
            tokenStartStop = pack(tokenStart, tokenStop);
            text = tokenText;
            startStopTokenIndex = pack(startToken + 1, stopToken + 1);
            typeAndDepth = packShort(tokenType, depth);
        }

        public int tokenType() {
            return unpackShortLeft(typeAndDepth);
        }

        public int tokenStart() {
            return unpackLeft(tokenStartStop);
        }

        public int tokenStop() {
            return unpackRight(tokenStartStop);
        }

        public int tokenEnd() {
            return tokenStop() + 1;
        }

        public boolean isSynthetic() {
            return startTokenIndex() == -1 || stopTokenIndex() == -1;
        }

        @Override
        public String toString() {
            return "Error(startToken=" + startTokenIndex() + ", " + stopTokenIndex()
                    + " depth=" + depth() + " text='" + text + " start "
                    + " startTokenIndex=" + startTokenIndex()
                    + " stopTokenIndex=" + stopTokenIndex() + ")";
        }

        @Override
        public int depth() {
            return unpackShortRight(typeAndDepth);
        }

        @Override
        public int startTokenIndex() {
            return unpackLeft(startStopTokenIndex) - 1;
        }

        @Override
        public int stopTokenIndex() {
            return unpackRight(startStopTokenIndex) - 1;
        }

        public String stringify(ParseTreeProxy proxy) {
            return "error: '" + text + "'";
        }

        @Override
        public int hashCode() {
            long hash = ((startStopTokenIndex * 14747)
                    + (tokenStartStop * 100043))
                    | (((long) typeAndDepth) << 24);
            return (int) (hash ^ (hash >> 32));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null || !(obj instanceof ErrorNodeTreeElement)) {
                return false;
            }
            final ErrorNodeTreeElement other = (ErrorNodeTreeElement) obj;
            return other.tokenStartStop == tokenStartStop
                    && other.typeAndDepth == typeAndDepth
                    && other.startStopTokenIndex == startStopTokenIndex;
        }

        public String tokenText() {
            return text;
        }
    }

    public static class TerminalNodeTreeElement extends ParseTreeElement implements TokenAssociated {

        private final int indexAndDepth;

        public TerminalNodeTreeElement(int tokenIndex, int depth) {
            super(ParseTreeElementKind.TERMINAL);
            indexAndDepth = packShort(tokenIndex, depth);
        }

        @Override
        public int depth() {
            return unpackShortRight(indexAndDepth);
        }

        public int tokenIndex() {
            return unpackShortLeft(indexAndDepth);
        }

        @Override
        public String stringify(ParseTreeProxy proxy) {
            ProxyToken tok = proxy.tokens().get(tokenIndex());
            return "'" + proxy.textOf(tok) + "'";
        }

        @Override
        public String name(ParseTreeProxy proxy) {
            return proxy.textOf(proxy.tokens().get(tokenIndex())).toString();
        }

        @Override
        public int hashCode() {
            return 17923 * indexAndDepth;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            TerminalNodeTreeElement other = (TerminalNodeTreeElement) obj;
            return indexAndDepth == other.indexAndDepth;
        }

        @Override
        public int startTokenIndex() {
            return tokenIndex();
        }

        @Override
        public int stopTokenIndex() {
            return tokenIndex();
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

        public String originalType() {
            return origType;
        }

        @Override
        public Throwable fillInStackTrace() {
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

    /**
     * An ambiguity reported by the parser; IMPORTANT: The bit set Antlr reports
     * as "ambiguous alternatives" does NOT correspond 1:1 with "alternative"
     * elements in the grammar; each bit is the index of a Transition in the
     * ATNState for the rule being processed, with 1 added to it; these MIGHT
     * correspond 1:1 with alternatives, but FREQUENTLY (as in "blah? foo
     * (bar)*?" is one alternative in a grammar and two in its ATN.
     */
    public static final class Ambiguity implements Comparable<Ambiguity> {

        private final BitSet conflictingAlternatives;
        private final long decisionAndDFAIndex;
        private final long stateNumberRuleIndexAndOuterAlt;
        private final long startStop;

        public Ambiguity(int decision, int ruleIndex, BitSet conflictingAlternatives,
                int startIndex, int stopIndex, int outerAlt, int dfaIndex,
                int stateNumber) {
            this.decisionAndDFAIndex = pack(decision, dfaIndex);
            this.conflictingAlternatives = conflictingAlternatives;
            int ruleIndexAndOuterAlt = packShort(ruleIndex, outerAlt);
            stateNumberRuleIndexAndOuterAlt = pack(stateNumber, ruleIndexAndOuterAlt);
            startStop = pack(startIndex, stopIndex);
        }

        /**
         * The bit set of conflicating "alternatives" - which will correspond
         * 1:1 to |-separated alternatives in a grammar *unless* either
         * left-recursion or non-greedy optional values are present, in which
         * case you have a puzzle to solve.
         *
         * @return A bit set of alternatives
         */
        public BitSet conflictingAlternatives() {
            return conflictingAlternatives;
        }

        private int ruleIndexAndOuterAlt() {
            return unpackRight(stateNumberRuleIndexAndOuterAlt);
        }

        public int state() {
            return unpackLeft(stateNumberRuleIndexAndOuterAlt);
        }

        public int ruleIndex() {
            return unpackShortLeft(ruleIndexAndOuterAlt());
        }

        public int outerAlternative() {
            return unpackShortRight(ruleIndexAndOuterAlt());
        }

        public int decision() {
            return unpackLeft(decisionAndDFAIndex);
        }

        /**
         * The index into the interpreter's array of DFAs of the DFA at this
         * ambiguity.
         *
         * @return The index into that array
         */
        public int dfaIndex() {
            return unpackRight(decisionAndDFAIndex);
        }

        /**
         * A unique identifier, useful in APIs that require error markers to
         * have one.
         *
         * @return An id
         */
        public String identifier() {
            return Long.toString(startStop, 36) + ":"
                    + Long.toString(stateNumberRuleIndexAndOuterAlt, 36)
                    + ":" + Integer.toString(decision(), 36)
                    + Arrays.toString(conflictingAlternatives.toLongArray())
                    + unpackRight(decisionAndDFAIndex);
        }

        public int start() {
            return unpackLeft(startStop);
        }

        public int end() {
            return stop() + 1;
        }

        public int stop() {
            return unpackRight(startStop);
        }

        @Override
        public int compareTo(Ambiguity o) {
            return Long.compare(startStop, o.startStop);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + this.decision();
            hash = 13171 * hash + (int) (stateNumberRuleIndexAndOuterAlt
                    ^ (11 * (stateNumberRuleIndexAndOuterAlt >> 32)));
            hash = 59 * hash + (int) (this.startStop
                    ^ (31891 * (startStop >> 32)));
            hash = 59 * hash + Objects.hashCode(this.conflictingAlternatives);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final Ambiguity other = (Ambiguity) obj;
            return decisionAndDFAIndex == other.decisionAndDFAIndex
                    && startStop == other.startStop
                    && stateNumberRuleIndexAndOuterAlt == other.stateNumberRuleIndexAndOuterAlt
                    && conflictingAlternatives.equals(other.conflictingAlternatives);
        }

        @Override
        public String toString() {
            return "Ambiguity(" + " / " + ruleIndex() + " / " + decision()
                    + " @ " + start() + ":" + stop() + " alts: "
                    + conflictingAlternatives
                    + " dfaIndex " + dfaIndex() + ")";
        }
    }

    // Packing methods for merging numeric values to save on field count
    // for types there are enormous numbers of instances of
    static long pack(int left, int right) {
        return (((long) left) << 32) | (right & 0xFFFF_FFFFL);
    }

    static int unpackLeft(long value) {
        return (int) ((value >>> 32) & 0xFFFF_FFFFL);
    }

    static int unpackRight(long value) {
        return (int) (value & 0xFFFF_FFFFL);
    }

    static long unsigned(int x) {
        return x & 0xFFFFFFFFL;
    }

    static long pack3(int a, int b, int c) {
        return ((long) a << 16)
                | (long) ((b << 8) & 0xFF00) | (c & 0xFF);
    }

    static int packShort(int left, int right) {
        return (left << 16)
                | (right & 0xFFFF);
    }

    static int unpackShortLeft(int val) {
        return (val >>> 16) & 0xFFFF;
    }

    static int unpackShortRight(int val) {
        return val & 0xFFFF;
    }

    static int unpack3Int(long value) {
        return (int) ((value >>> 16) & 0xFFFF_FFFFL);
    }

    static short unpack3LeftByte(long value) {
        value = value & 0x0000_0000_0000_FF00L;
        return (short) ((value >>> 8) & 0xFFFFL);
    }

    static short unpack3RightByte(long value) {
        return (short) (value & 0xFFL);
    }

    static long pack(int a, int b, int c, int d) {
        return ((long) a << 48)
                | (long) ((b & 0xFFFF_FFFF_FFFFL) << 32)
                | (long) ((c & 0xFFFF_FFFF_FFFFL) << 16)
                | (long) (d & 0xFFFF_FFFF_FFFFL);
    }

    static int unpackA(long value) {
        return (int) ((value >>> 48) & 0xFFFFL);
    }

    static int unpackB(long value) {
        return (int) ((value >>> 32) & 0xFFFFL);
    }

    static int unpackC(long value) {
        return (int) ((value >>> 16) & 0xFFFFL);
    }

    static int unpackD(long value) {
        return (int) (value & 0xFFFFL);
    }

    static int tokenLength(int startIndex, int stopIndex) {
        if (stopIndex < 0) {
            return 0;
        }
        return Math.max(0, (stopIndex - startIndex) + 1);
    }
}
