package org.nemesis.antlrformatting.api;

import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.misc.Interval;
import org.openide.util.Exceptions;

/**
 * A temporary hack to ensure that FormattingContextImpl really returns the
 * right current line position. You can do that by simply calling the rewriter's
 * getText() method for all processed tokens, but it is very slow and does a lot
 * of work, when the only thing we are interested in is how many characters
 * behind the current token is the last newline in the revised text. This
 * instead keeps an index of the last token that was modified to include a
 * newline, and handles it that way. Seriously, doing it the brute force way
 * means a one-second freeze when reformatting with very simple rules.
 *
 * @author Tim Boudreau
 */
final class LinePositionComputingRewriter extends TokenStreamRewriter implements StreamRewriterFacade {

    private int rev = 0;
    private int lastRequestedNlPos = -1;
    private int lastRequestedNlPosResult = -1;
    private int lastRequestRev;
    private final StringBuilder scratch = new StringBuilder(120);
    private int lastTokenModifiedWithNewline;
    private String lastFilteredRewritesKey = "-";
    private List<RewriteOperation> lastFilteredRewrites;
    private int rewriteCountAtLastCache = -1;
    private TreeMap<Integer, RewriteOperation> cachedRewrites;

    LinePositionComputingRewriter(TokenStream tokens) {
        super(tokens);
    }

    public void close() {
        // discard caches
        rewriteCountAtLastCache = -1;
        lastFilteredRewritesKey = "-";
        cachedRewrites = null;
        lastFilteredRewrites = null;
    }

    private void maybeUpdateNewlineTokenIndex(int token, Object text) {
        if (containsNewline(text)) {
            lastTokenModifiedWithNewline = token;
        }
    }

    static Set<Class<?>> INSERT_BEFORE = new HashSet<>();
    static Set<Class<?>> INSERT_OR_REPLACE = new HashSet<>();
    static Set<Class<?>> NOT_INSERT_OR_REPLACE = new HashSet<>();

    private boolean isInsertBeforeOrReplace(RewriteOperation o) {
        // Believe it or not, this pays for itself - in profiling,
        // looking up the binary name of the class shows up quite a bit
        if (INSERT_OR_REPLACE.contains(o.getClass())) {
            return true;
        } else if (NOT_INSERT_OR_REPLACE.contains(o.getClass())) {
            return false;
        }
        String name = o.getClass().getSimpleName();
        boolean insertBefore = "InsertBeforeOp".equals(name);
        boolean isMatch = insertBefore || "ReplaceOp".equals(name);
        if (insertBefore) {
            INSERT_BEFORE.add(o.getClass());
        }
        if (isMatch) {
            INSERT_OR_REPLACE.add(o.getClass());
            return true;
        } else {
            NOT_INSERT_OR_REPLACE.add(o.getClass());
            return false;
        }
    }

    private boolean isInsertBefore(RewriteOperation o) {
        if (INSERT_BEFORE.contains(o.getClass())) {
            return true;
        }
        String name = o.getClass().getSimpleName();
        boolean insertBefore = "InsertBeforeOp".equals(name);
        if (insertBefore) {
            INSERT_BEFORE.add(o.getClass());
        }
        return insertBefore;
    }

    boolean isInsertAfter(RewriteOperation op) {
        return !isInsertBeforeOrReplace(op);
    }

    int index(RewriteOperation rew) {
        // An awkward way to get the value of the internal index
        // field of a rewrite operation, but the subclasses are
        // package private
        int result = rew.execute(scratch);
        scratch.setLength(0);
//        if ("InsertBeforeOp".equals(name) || "ReplaceOp".equals(name)) {
        if (isInsertBeforeOrReplace(rew)) {
            result--;
        }
        return result;
    }

    private boolean containsNewline(Object text) {
        return text != null && text.toString().indexOf('\n') >= 0;
    }

    @Override
    public void replace(String programName, int from, int to, Object text) {
        maybeUpdateNewlineTokenIndex(to, text);
        super.replace(programName, from, to, text);
        rev++;
    }

    @Override
    public void insertBefore(String programName, int index, Object text) {
        maybeUpdateNewlineTokenIndex(index, text);
        super.insertBefore(programName, index, text);
        rev++;
    }

    @Override
    public void insertAfter(String programName, int index, Object text) {
        maybeUpdateNewlineTokenIndex(index + 1, text);
        super.insertAfter(programName, index, text);
        rev++;
    }

    private List<RewriteOperation> filteredRewrites(int start, int end) {
        List<RewriteOperation> rewrites = programs.get(DEFAULT_PROGRAM_NAME);
        if (lastFilteredRewrites != null) {
            String k = start + ":" + end + ":" + rewrites.size();
            if (lastFilteredRewritesKey.equals(k)) {
                return lastFilteredRewrites;
            }
        }
        if (rewrites == null) {
            return null;
        }
        List<RewriteOperation> result = new ArrayList<>(rewrites.size() + 1);
        for (RewriteOperation rew : rewrites) {
            // This is messy.  reduceToSingleOperationPerIndex assumes
            // that it each operation's private index field will match
            // its position in the list, so if we want to skip irrelevant
            // formatting operations that occurred before the last newline,
            // we MUST provide nulls for those or all hell breaks loose
            if (rew == null) {
                result.add(null);
                continue;
            }
            int ix = index(rew);
            if (ix >= start && ix <= end) {
                result.add(rew);
            } else {
                result.add(null);
            }
        }
        lastFilteredRewrites = result;
        lastFilteredRewritesKey = start + ":" + end + ":" + rewrites.size();
        return result;
    }

    private int trailingCharactersAfterNewline(String txt) {
        int ix = txt.lastIndexOf('\n');
        if (ix < 0) {
            return txt.length();
        }
        return (txt.length() - 1) - ix;
    }

    int cachedResult(int tokenIndex) {
        if (rev == lastRequestRev && lastRequestedNlPos == tokenIndex) {
            return lastRequestedNlPosResult;
        }
        return -1;
    }

    public int lastNewlineDistance(int tokenIndex) {
        if (tokenIndex == 0) {
            return 0;
        }
//        int res = cachedResult(tokenIndex);
//        if (res >= 0) {
//            log("  returning cached " + res + " for " + tokenIndex);
//            return res;
//        }
        lastRequestRev = rev;
        lastRequestedNlPos = tokenIndex;
        return lastRequestedNlPosResult = _lastNewlineDistance(tokenIndex);
    }

    @Override
    protected Map<Integer, RewriteOperation> reduceToSingleOperationPerIndex(List<RewriteOperation> rewrites) {
        // This operation is THE most expensive operation in all of formatting.
        // It is called whenever something checks the current character position
        // in the reformatted text.  SO... cache the result, and only process the
        // tail of the list which has not been seen before or overlaps with the
        // previous result
        if (cachedRewrites != null && rewrites.size() == rewriteCountAtLastCache) {
            return cachedRewrites;
        }
        if (cachedRewrites != null && cachedRewrites.size() > 1) {
            int maxKey = cachedRewrites.lastKey();
            List<RewriteOperation> toReprocess = new LinkedList<>();
            int mx = rewrites.size() - 1;
            for (int i = mx; i >= 0; i--) {
                RewriteOperation op = rewrites.get(i);
                if (op != null) {
                    int ix = index(op);
                    if (ix >= maxKey + 1) {
                        toReprocess.add(0, op);
                    } else {
                        break;
                    }
                }
            }
            cachedRewrites.putAll(super.reduceToSingleOperationPerIndex(toReprocess));
            /*
            boolean asserts = false;
            assert asserts = true;
            if (asserts) {
                Map<Integer, RewriteOperation> orig = super.reduceToSingleOperationPerIndex(rewrites);
                Map<Integer, String> a = new HashMap<>();
                Map<Integer, String> b = new HashMap<>();
                for (Map.Entry<Integer, RewriteOperation> e : cachedRewrites.entrySet()) {
                    if (e.getKey() < maxKey) {
                        a.put(e.getKey(), Strings.escape(e.getValue().toString(), Escaper.NEWLINES_AND_OTHER_WHITESPACE));
                    }
                }
                for (Map.Entry<Integer, RewriteOperation> e : orig.entrySet()) {
                    if (e.getKey() < maxKey) {
                        b.put(e.getKey(), Strings.escape(e.getValue().toString(), Escaper.NEWLINES_AND_OTHER_WHITESPACE));
                    }
                }
                assert a.equals(b) :
                        "Rewrites diverge: " + cachedRewrites + " vs " + orig;
            }
             */
        } else {
            cachedRewrites = new TreeMap<>(super.reduceToSingleOperationPerIndex(rewrites));
        }
        rewriteCountAtLastCache = rewrites.size();
        return cachedRewrites;
    }

    static boolean log = false;

    private static void log(String s) {
        if (log) {
            System.out.println(s);
        }
    }

    @Override
    public String rewrittenText(int index) {
        List<RewriteOperation> rewrites = filteredRewrites(Math.max(0, index - 1), index + 1);
        Map<Integer, RewriteOperation> indexToOp = reduceToSingleOperationPerIndex(rewrites);
        if (indexToOp.containsKey(index)) {
            RewriteOperation op = indexToOp.get(index);
            return text(op);
        } else {
            Token tok = super.tokens.get(index);
            return tok.getText();
        }
    }

    private int _lastNewlineDistance(int tokenIndex) {
        int start = lastTokenModifiedWithNewline;
        int stop = tokenIndex;
        log("compute last newline dist " + tokenIndex + " start " + start + " stop " + stop);

        if (stop < start) {
            start = 0;
            /*
            log("  stop < start, ret 0 for " + lastTokenModifiedWithNewline + " - " + stop);
            int res;
            if (start >= 2) {
                start = 0;
//                res = trailingCharactersAfterNewline(tokens.getText(new Interval(start - 2, start - 1)));
//                System.out.println("  z1 - " + res + " tokStart " + tokens.get(tokenIndex).getStartIndex());
            } else {
                System.out.println("  z2 - 0");
                res = 0;
                return res;
            }
            */
        }

        // ensure start/end are in range
        if (stop > tokens.size() - 1) {
            stop = Math.min(tokenIndex, tokens.size() - 1);
        }

        List<RewriteOperation> rewrites = filteredRewrites(start, stop + 1);
//        List<RewriteOperation> rewrites = programs.get(DEFAULT_PROGRAM_NAME);
        if (rewrites == null || rewrites.isEmpty()) {
            stop--;
            if (stop <= 0) {
                log("  first token, must be tokenlength");
//                return tok.get;
            }
//            log("  empty rewrites, use trailing of '" + tokens.getText(new Interval(start, stop)) + "'");
            return trailingCharactersAfterNewline(tokens.getText(new Interval(start, stop)));
        }

        // First, optimize instruction stream
        Map<Integer, RewriteOperation> indexToOp = super.reduceToSingleOperationPerIndex(rewrites);

        int result = 0;
        // Walk buffer, executing instructions and emitting tokens
        tokenloop:
        for (int i = stop; i >= 0; i--) {

//            XXX THIS IS BROKEN - WE ARE HANDLING THE FULL LENGTH OF THE TARGET
//                    TOKEN WHEN FOR STOP WE SHOULD ONLY LOOK AT WHETHER IT STARTS
//                            WITH OR HAS A PREPENDED NEWLINE AND HOW FAR AWAY THAT IS
            RewriteOperation op = indexToOp.get(i);
            indexToOp.remove(i); // remove so any left have index size-1
            Token t = tokens.get(i);
            log("proc token " + i + " " + Strings.escape(t.getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE) + " with start " + t.getStartIndex());
            if (op == null) {
                if (i == tokenIndex) {
                    String s = t.getText();
                    for (int j = 0; j < s.length(); j++) {
                        if (s.charAt(j) == '\n') {
                            log("Found leading newline in text of token " + i + " - returning 0");
                            return 0;
                        } else if (!Character.isWhitespace(s.charAt(j))) {
                            String c = Strings.escape(new String(new char[]{s.charAt(j)}), Escaper.NEWLINES_AND_OTHER_WHITESPACE);
                            log("  bail 1 out of testing current token " + i + " on '" + c + "' at " + j);
                            break;
                        }
                    }
                    log("  continuing with no change");
                    continue;
                }
                // no operation at that index, just dump token
                if (t.getType() != Token.EOF) {
                    String txt = t.getText();
                    int ix = txt.lastIndexOf('\n');
                    log("  null op, last ix " + ix + " for token " + i + " - " + t);
                    if (ix >= 0) {
                        log("  add in " + ((txt.length() - 1) - ix));
                        result += (txt.length() - 1) - ix;
                        break;
                    } else {
                        if (i != tokenIndex) {
                            log("   add in full token length " + txt.length());
                            result += i == 0 ? txt.length() + 1 : txt.length();
                        } else {
                            log("   add nothing, this is the target token");
                        }
                    }
                }
            } else {
                if (i == tokenIndex && isInsertBefore(op)) {
                    log("  is first");
                    String txt = textOf(op);
                    log("     insert before text " + Strings.escape(txt, Escaper.NEWLINES_AND_OTHER_WHITESPACE));
                    int ix = txt.lastIndexOf('\n');
                    if (ix >= 0) {
                        log("  return diff of last index of '\\n'" + ix + " of " + txt.length());
                        return (txt.length() - ix) - 1;
                    }
                    txt = t.getText();
                    if (txt.startsWith("\n")) {
                        log("   token text starts with newline, return 0");
                        return 0;
                    }
                    continue;
                }
                scratch.setLength(0);
                op.execute(scratch);
                log("  executed op " + op + " to get " + Strings.escape(scratch, Escaper.NEWLINES_AND_OTHER_WHITESPACE));
                if (scratch.length() > 0) {
                    if (i == tokenIndex) {
                        // Special handling if we are dealing with the token we
                        // are being queried from - in that case, all we want
                        // to know is if there is a leading \n (either in the
                        // text or prepended) and if so
                        // return zero; otherwise, immediately look at the
                        // preceding character
                        for (int j = 0; j < scratch.length(); j++) {
                            if (scratch.charAt(j) == '\n') {
                                return 0;
                            } else if (!Character.isWhitespace(i)) {
                                continue tokenloop;
                            }
                        }
                    }
                    int ix = lastIndexOfNewline(scratch);
                    if (ix >= 0) {
                        log("    add in " + ((scratch.length() - 1) - ix));
                        result += (scratch.length() - 1) - ix;
                        break;
                    } else {
                        log("    add in full token length " + scratch.length());
                        result += scratch.length();
                    }
                }
            }
        }
//        log("returning " + result);
        return result - 1;
    }
    
    String textOf(RewriteOperation op) {
        try {
            Field f = RewriteOperation.class.getDeclaredField("text");
            f.setAccessible(true);
            return "" + f.get(op);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            Exceptions.printStackTrace(ex);
            return "";
        }
    }

    String text(RewriteOperation op) {
        StringBuilder sb = new StringBuilder();
        op.execute(sb);
        return sb.toString();
    }

    private int lastIndexOfNewline(StringBuilder sb) {
        int max = sb.length() - 1;
        for (int i = max; i >= 0; i--) {
            if (sb.charAt(i) == '\n') {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void insertAfter(Token tok, String text) {
        this.insertAfter(DEFAULT_PROGRAM_NAME, tok, text);
    }

    @Override
    public void insertAfter(int index, String text) {
        this.insertAfter(DEFAULT_PROGRAM_NAME, index, text);
    }

    @Override
    public void insertBefore(Token tok, String text) {
        this.insertBefore(DEFAULT_PROGRAM_NAME, tok, text);
    }

    @Override
    public void insertBefore(int index, String text) {
        this.insertBefore(DEFAULT_PROGRAM_NAME, index, text);
    }

    @Override
    public void replace(Token tok, String text) {
        this.replace(tok, tok, text);
    }

    @Override
    public void replace(int index, String text) {
        this.replace(DEFAULT_PROGRAM_NAME, index, index, text);
    }
}
