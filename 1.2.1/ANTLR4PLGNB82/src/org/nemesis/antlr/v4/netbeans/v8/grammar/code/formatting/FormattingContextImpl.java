package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.misc.Interval;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;

/**
 *
 * @author Tim Boudreau
 */
class FormattingContextImpl implements FormattingContext {

    final TokenStreamRewriter rew;
    final int start;
    final int end;
    final int indentSize;
    final FormattingRules rules;
    private boolean lastContainedNewline;
    private int posInOriginalLine;
    private int lineLength = 0;
    private int blockDepth = 0;
    private int openBrace = 0;
    private int colonPositionInLine = 0;
    private int actionBraces = 0;
    private LinkedList<Integer> leftParenPos = new LinkedList<>();
    private String lastAppended;
    private String prepend;
    private String append;
    String replacement;
    private EverythingTokenStream stream;

    public FormattingContextImpl(TokenStreamRewriter rew, int start, int end, int indentSize,
            FormattingRules rules) {
        this.rew = rew;
        this.start = start;
        this.end = end;
        this.rules = rules;
        this.indentSize = indentSize;
    }

    String go(ANTLRv4Lexer lexer, EverythingTokenStream tokens) {
        stream = tokens;
        tokens.seek(0);
        int prevType = -1;
        int size = tokens.size();
        for (int i = 0; i < size; i++) {
            ModalToken tok = tokens.get(i);
            if (tok.getStartIndex() > end) {
                // We have formatted all we were asked to; get out
                break;
            }
            tokens.seek(i + 1);
            try {
                ModalToken nxt = tokens.findSubsequent(i, AntlrCriteria::notWhitespace);
                int nextType = nxt == null ? -1 : nxt.getType();
                tokens.seek(i);
                if (onOneToken(tok, prevType, nextType, tokens)) {
                    prevType = tok.getType();
                }
            } finally {
                try {
                    tokens.consume();
                } catch (IllegalStateException ex) {
                    break;
                }
            }
        }
        return getModifiedText();
    }

    public int occurrencesOf(int tokenType, int before) {
        int count = 0;
        for (int ix = stream.cursor + 1; ix < stream.size(); ix++) {
            Token t = stream.get(ix);
            if (t.getType() == before) {
                break;
            }
            if (tokenType == t.getType()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int tokenCountToNext(boolean ignoreWhitespace, int targetType) {
        int count = 0;
        for (int ix = stream.cursor + 1; ix < stream.size(); ix++) {
            Token t = stream.get(ix);
            if (t.getType() == targetType) {
                break;
            }
            boolean isWhitespace = AntlrCriteria.isWhitespace(t.getType());
            if (isWhitespace && ignoreWhitespace) {
                continue;
            }
            count++;
        }
        return count;
    }

    @Override
    public int tokenCountToPreceding(boolean ignoreWhitespace, int targetType) {
        int count = 0;
        for (int ix = stream.cursor - 1; ix >= 0; ix--) {
            Token t = stream.get(ix);
            if (t.getType() == targetType) {
                break;
            }
            boolean isWhitespace = AntlrCriteria.isWhitespace(t.getType());
            if (isWhitespace && ignoreWhitespace) {
                continue;
            }
            count++;
        }
        return count;
    }

    @Override
    public int countForwardOccurrencesUntilNext(IntPredicate toCount, IntPredicate stopType) {
        int count = 0;
        for (int ix = stream.cursor + 1; ix < stream.size(); ix++) {
            Token t = stream.get(ix);
            if (toCount.test(t.getType())) {
                count++;
            } else if (stopType.test(t.getType())) {
                break;
            }
        }
        return count;
    }

    @Override
    public int countForwardOccurrencesUntilNext(int toCount, int stopType) {
        int count = 0;
        for (int ix = stream.cursor + 1; ix < stream.size(); ix++) {
            Token t = stream.get(ix);
            if (t.getType() == toCount) {
                count++;
            } else if (t.getType() == stopType) {
                break;
            }
        }
        return count;
    }

    @Override
    public int countBackwardOccurrencesUntilPrevious(int toCount, int stopType) {
        int count = 0;
        for (int ix = stream.cursor - 1; ix >= 0; ix--) {
            Token t = stream.get(ix);
            if (t.getType() == toCount) {
                count++;
            } else if (t.getType() == stopType) {
                break;
            }
        }
        return count;
    }

    @Override
    public int countBackwardOccurrencesUntilPrevious(IntPredicate toCount, IntPredicate stopType) {
        int count = 0;
        for (int ix = stream.cursor - 1; ix >= 0; ix--) {
            Token t = stream.get(ix);
            if (toCount.test(t.getType())) {
                count++;
            } else if (stopType.test(t.getType())) {
                break;
            }
        }
        return count;
    }

    public String getModifiedText() {
        if (firstTokenInRange == -1) {
            return "";
        }
        return rew.getText(new Interval(firstTokenInRange, lastTokenInRange));
    }

    int firstTokenInRange = -1;
    int lastTokenInRange = -1;

    boolean onOneToken(ModalToken tok, int prevType, int nextType, EverythingTokenStream tokens) {
        int tokenType = tok.getType();
        if (AntlrCriteria.isWhitespace(tok.getType()) || tok.getText().trim().length() == 0) {
            int newlinePosition = tok.getText().lastIndexOf('\n');
            lastContainedNewline = newlinePosition >= 0;
            if (lastContainedNewline) {
                posInOriginalLine = (tok.getText().length() - newlinePosition);
            }
            rew.delete(tok);
            if (!lastContainedNewline) {
                posInOriginalLine += tok.getText().length();
            } else {
                posInOriginalLine = tok.getText().length() - newlinePosition;
            }
            return false;
        }
        if (tok.getStartIndex() >= start && tok.getStopIndex() <= end) {
            if (firstTokenInRange == -1) {
                firstTokenInRange = tok.getTokenIndex();
            }
            lastTokenInRange = tok.getTokenIndex();
            onToken(tok, prevType, tokenType, nextType, lastContainedNewline);
        }
        posInOriginalLine += tok.getText().length();
        lastContainedNewline = false;
        String txt = tok.getText();
        int len = 0;
        if (txt != null) {
            len = txt.length();
            int ix = txt.lastIndexOf('\n');
            if (ix > 0) {
                lineLength = 0;
                len -= (ix + 1);
            }
        }
        lineLength += len;
        prevType = tokenType;
        if (replacement != null) {
            rew.replace(tok, replacement);
        }
        if (prepend != null) {
            rew.insertBefore(tok, prepend);
        }
        if (append != null) {
            rew.insertAfter(tok, append);
            int ix = append.lastIndexOf('\n');
            if (ix >= 0) {
                lineLength = append.length() - (ix + 1);
            }
        }
        lastAppended = append;
        append = null;
        prepend = null;
        return true;
    }

    @Override
    public void replace(String replacement) {
        this.replacement = replacement;
    }

    @Override
    public void prependSpace() {
        if (!Objects.equals(lastAppended, " ")) {
            if (prepend != null && prepend.length() > 1) {
                return;
            }
            prepend = " ";
            lineLength++;
        }
    }

    @Override
    public void appendSpace() {
        if (append != null && append.length() > 1) {
            return;
        }
        append = " ";
        lineLength++;
    }

    @Override
    public void prependNewline() {
        prepend = "\n";
        lineLength = 0;
    }

    @Override
    public void prependNewlineAndIndentBy(int amt) {
        prepend = "\n" + spacesString(amt);
        lineLength = prepend.length() - 1;
    }

    @Override
    public void prependNewlineAndIndent() {
        int depth = blockDepth;
        if (depth == 0) {
            depth = get("colon", 0);
        } else {
            prepend = "\n" + indentString(depth);
            lineLength = prepend.length() - 1;
            return;
        }
        if (depth == 0) {
            depth = indentSize;
        }
        prepend = "\n" + spacesString(depth);
        lineLength = prepend.length() - 1;
    }

    @Override
    public void appendNewline() {
        append = "\n";
        lineLength = 0;
    }
    private final Map<Object, Integer> context = new HashMap<>();
    private static final String colonPosition = "colon";

    @Override
    public <T> void set(T key, int val) {
        context.put(key, val);
    }

    @Override
    public <T> int get(T key, int defaultValue) {
        if ("blockDepth".equals(key)) {
            return blockDepth;
        }
        if ("lparen".equals(key) && !leftParenPos.isEmpty()) {
            return leftParenPos.peek();
        }
        if ("beginActionBraces".equals(key)) {
            return actionBraces;
        }
        if ("openBrace".equals(key)) {
            return openBrace;
        }
        return context.getOrDefault(key, Integer.valueOf(defaultValue));
    }

    @Override
    public int origCharPositionInLine() {
        return posInOriginalLine;
    }

    @Override
    public int currentCharPositionInLine() {
        if (prepend != null && prepend.startsWith("\n")) {
            return prepend.length() - 1;
        }
        return lineLength;
    }

    @Override
    public void appendNewlineAndIndent() {
        append = "\n" + indentString(get(colonPosition, indentSize));
        lineLength = append.length() - 1;
    }

    @Override
    public void appendNewlineAndDoubleIndent() {
        append = "\n" + indentString(get(colonPosition, indentSize) * 2);
        lineLength = append.length() - 1;
    }

    @Override
    public void prependNewlineAndDoubleIndent() {
        int depth = blockDepth;
        if (depth == 0) {
            depth = get("colon", 0);
        } else {
            prepend = "\n" + indentString(depth + 1);
            lineLength = prepend.length() - 1;
            return;
        }
        if (depth == 0) {
            depth = indentSize;
        }
        prepend = "\n" + spacesString(depth * 2);
        lineLength = prepend.length() - 1;
    }

    @Override
    public void appendDoubleNewline() {
        append = "\n\n";
        lineLength = 0;
    }

    @Override
    public void indent() {
        int depth = blockDepth;
        if (depth == 0) {
            depth = get("colon", 0);
        } else {
            prepend = indentString(depth + 1);
            lineLength += prepend.length() - 1;
            return;
        }
        if (depth == 0) {
            depth = indentSize;
        }
        prepend = spacesString(depth * 2);
        lineLength += prepend.length();
    }

    String indentString(int amt) {
        return spacesString(amt * indentSize);
    }

    String spacesString(int amt) {
        char[] chars = new char[Math.max(0, amt)];
        Arrays.fill(chars, ' ');
        return new String(chars);
    }

    private void processToken(ModalToken token, int prevType, int tokenType, int nextType, boolean hasImmediatelyPrecedingNewlines) {
        boolean log = AntlrFormatter.isLoggable(token.getType()) || AntlrFormatter.isLoggable(prevType) || AntlrFormatter.isLoggable(nextType);
        if (log) {
            System.out.println("PROC "
                    + token.getText() + " "
                    + rules.vocabulary().getSymbolicName(tokenType) + " nextType "
                    + rules.vocabulary().getSymbolicName(nextType) + " prevType "
                    + rules.vocabulary().getSymbolicName(prevType) + " precedingNewline "
                    + hasImmediatelyPrecedingNewlines
                    + " mode " + token.modeName() + "(" + token.mode() + ")"
            );
        }
        rules.apply(token, prevType, nextType, hasImmediatelyPrecedingNewlines, this, log);
    }

    private void onToken(ModalToken token, int prevType, int tokenType, int nextType, boolean hasImmediatelyPrecedingNewlines) {
        int lpos = lineLength;
        try {
            processToken(token, prevType, tokenType, nextType, hasImmediatelyPrecedingNewlines);
        } finally {
            switch (tokenType) {
                case ANTLRv4Lexer.BEGIN_ACTION:
                case ANTLRv4Lexer.LBRACE:
                    openBrace = lpos;
                    actionBraces++;
                    break;
                case ANTLRv4Lexer.END_ACTION:
                    actionBraces--;
                    if (actionBraces < 0) { // broken source
                        actionBraces = 0;
                    }
                    break;
                case ANTLRv4Lexer.LPAREN:
                    leftParenPos.push(lpos);
                    blockDepth++;
                    break;
                case ANTLRv4Lexer.RPAREN:
                    blockDepth--;
                    if (blockDepth < 0) { // broken source
                        blockDepth = 0;
                    }
                    if (!leftParenPos.isEmpty()) {
                        leftParenPos.pop();
                    }
                    break;
                case ANTLRv4Lexer.COLON:
                    colonPositionInLine = lpos;
                    break;
            }
        }
    }
}
