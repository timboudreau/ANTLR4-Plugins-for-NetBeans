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
package org.nemesis.antlrformatting.api;

import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.IntList;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.collections.IntSet;
import com.mastfrog.util.search.Bias;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.util.Arrays;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

/**
 * Originally, this code used Antlr's TokenStreamRewriter - however, this is (!)
 * 180x faster.
 *
 * @author Tim Boudreau
 */
final class FastStreamRewriter implements StreamRewriterFacade {

    final EverythingTokenStream stream; // the stream
    final IntMap<RewriteInfo> rewritesForToken; // per-token rewrite info
    // caches so we don't have to iterate back to the top of the document
    // to figure out the nearest newline or the start position of a
    // token that has modifications
    final IntList newlinePositions; // pkg private for tests
    final IntList startPositions; // pkg private for tests
    private int cachedNewlineDistance;
    private int cachedLastRequestedNewlineDistance;

    /*
    The problem we are solving here is that reformatting very frequently needs
    to know the distance of the current character from the nearest newline -
    in the *updated* text, which has not yet been composed.  The original
    implementation using Antlr's TokenStreamRewriter could only do this by
    rendering a partial or complete document and then iterating backwards,
    which, even with a subclass that cached partial renderings, was absurdly,
    embarassingly slow.  So we need a stream rewriter that keeps track of
    these things.
    How this works:
     1. We maintain a cache of token start positions by token index,
     2. We maintain a cache of newline positions by character index
     3. Both of these can take advantage of IntList's fuzzy binary
       search that makes it possible to find the index of the nearest
       value to the one passed in a sorted list (maintaining sort is
       critically important or the binary search can loop endlessly)
     4. ModalToken maintains an internal cache of the original newline
       positions
     5. When we add or change a rewrite, positions may be recomputed
     6. When needing to know the position of a newline in the document,
        all we do is
         A. Look up the character start position of the token (taking into account
            modifications, since the cache is updated on modification) in
            startPositions
         B. Ask the newlinePositions list to find the nearest value less than
            or equal to the token in question

    TODO: Token deletion leaves adjacent entries with the same value in
    startPositions (since the deleted token still exists by index, but
    has become zero-length). There are currently no indexOfAssumingSorted
    or indexOf calls to startPositions, but if any were added, they should use
    nearestIndexToPresumingSorted(value, Bias.NONE), which is duplicate-tolerant
    and will return the highest indexed entry that matches.
     */
    FastStreamRewriter(EverythingTokenStream stream) {
        this.stream = stream;
        rewritesForToken = CollectionUtils.intMap(stream.size(), true, RewriteInfo::new);
        newlinePositions = IntList.create(stream.size() + stream.size() / 2);
        startPositions = IntList.create(stream.size());
        int pos = 0;
        // Initialize the start positions
        for (int i = 0; i < stream.size(); i++) {
            ModalToken tok = stream.get(i);
            startPositions.add(tok.getStartIndex());
            int[] nlp = tok.newlinePositions();
            for (int j = 0; j < nlp.length; j++) {
                int nl = nlp[j];
                newlinePositions.add(pos + nl);
            }
            pos += tok.getText().length();
        }
    }

    @Override
    public String toString() {
        // Indented for ease of reading as part of FormattingContextImpl.toString()
        StringBuilder sb = new StringBuilder("  FastStreamRewriter(")
                .append("\n    newlinePositions: ").append(newlinePositions)
                .append("\n    startPositions: ").append(startPositions)
                .append("\n    rewrites: ").append(rewritesForToken);
        return sb.append("\n  )").toString();
    }

    void incrementNewlinePositions(int above, int by) {
        newlinePositions.adjustValues(above, by);
    }

    public void close() {
        newlinePositions.clear();
        rewritesForToken.clear();
    }

    @Override
    public String getText() {
        StringBuilder sb = new StringBuilder();
        for (ModalToken tok : stream) {
            if (tok.getType() == -1) {
                continue;
            }
            if (rewritesForToken.containsKey(tok.getTokenIndex())) {
                RewriteInfo info = rewritesForToken.get(tok.getTokenIndex());
                info.rewrite(sb, tok);
            } else {
                sb.append(tok.getText());
            }
        }
        return sb.toString();
    }

    @Override
    public String getText(Interval interval) {
        int start = interval.a;
        int stop = interval.b;
        // ensure start/end are in range
        if (stop > stream.size() - 1) {
            stop = stream.size() - 1;
        }
        if (start < 0) {
            start = 0;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= stop; i++) {
            ModalToken tok = stream.get(i);
            if (tok.getType() == -1) {
                continue;
            }
            if (rewritesForToken.containsKey(tok.getTokenIndex())) {
                RewriteInfo info = rewritesForToken.get(tok.getTokenIndex());
                info.rewrite(sb, tok);
            } else {
                sb.append(tok.getText());
            }
        }
        return sb.toString();
    }

    @Override
    public void delete(Token tok) {
        delete(tok.getTokenIndex());
    }

    @Override
    public void delete(int tokenIndex) {
        // Drop the cached value for this
        cachedLastRequestedNewlineDistance = -1;
        RewriteInfo info = rewritesForToken.get(tokenIndex);
        // If it's already deleted, don't double-delete
        if (info.deleted) {
            return;
        }
        // Get the old positions of things before altering anything
        int oldLength = info.length(stream.get(tokenIndex));
        int[] newlinesRemoved = info.newlinePositions(stream.get(tokenIndex));

        info.delete();
        // Get the new positions
        int start = startPositions.get(tokenIndex);
        if (newlinesRemoved.length > 0) {
            for (int i = 0; i < newlinesRemoved.length; i++) {
                // Get the exact index of that entry in the array
                int ix = newlinePositions.nearestIndexToPresumingSorted(start + newlinesRemoved[i], Bias.NONE);
                if (ix >= 0) {
                    newlinePositions.remove(ix);
                }
            }
        }
        // shift subsequent ones backwards
        int nlix = newlinePositions.nearestIndexToPresumingSorted(start, Bias.FORWARD);
        if (nlix >= 0) {
            newlinePositions.adjustValues(nlix, -oldLength);
        }
        startPositions.adjustValues(tokenIndex + 1, -oldLength);
    }

    @Override
    public void insertAfter(Token tok, String text) {
        insertAfter(tok.getTokenIndex(), text);
    }

    @Override
    public void insertAfter(int index, String text) {
        RewriteInfo info = rewritesForToken.get(index);
        int[] oldNewlines = info.newlinePositions(stream.get(index));
        int oldLength = info.length(stream.get(index));
        info.setAfter(text);
        int newLength = info.length(stream.get(index));
        newlinesMayBeChanged(info, index, oldNewlines, oldLength, newLength);
    }

    @Override
    public void insertBefore(Token tok, String text) {
        insertBefore(tok.getTokenIndex(), text);
    }

    @Override
    public void insertBefore(int index, String text) {
        RewriteInfo info = rewritesForToken.get(index);
        int[] oldNewlines = info.newlinePositions(stream.get(index));
        int oldLength = info.length(stream.get(index));
        info.setBefore(text);
        int newLength = info.length(stream.get(index));
        newlinesMayBeChanged(info, index, oldNewlines, oldLength, newLength);
    }

    public void newlinesMayBeChanged(RewriteInfo info, int index, int[] oldNewlines, int oldLength, int newLength) {
        // Discard the cached answer to nearest newline pos
        cachedLastRequestedNewlineDistance = -1;
        int[] newNewlines = info.newlinePositions(stream.get(index));
        int lengthDiff = newLength - oldLength;
        boolean newlinesChanged
                = (oldNewlines.length != newNewlines.length)
                || (oldNewlines.length > 0 && newNewlines.length > 0
                && !Arrays.equals(oldNewlines, newNewlines));

        boolean lengthChanged = lengthDiff != 0;

        boolean newlinesNeedSort = false;

        if (newlinesChanged || lengthChanged) {
            int oldStart = startPositions.get(index);
            IntSet newNewlineSet = newNewlines.length == 0 ? IntSet.EMPTY
                    : IntSet.create(newNewlines);

            IntSet oldNewlineSet = oldNewlines.length == 0 ? IntSet.EMPTY
                    : IntSet.create(oldNewlines);

            for (int i = oldNewlines.length - 1; i >= 0; i--) {
                int oldNewline = oldNewlines[i];
                if (!newNewlineSet.contains(oldNewline)) {
                    int ix = newlinePositions.nearestIndexToPresumingSorted(oldNewlines[i], Bias.NONE);
                    if (ix >= 0) {
                        newlinePositions.removeAt(ix);
                    }
                }
            }
            if (lengthChanged) {
                int oldEnd = oldStart + oldLength;
                if (oldEnd < 0) {
                    throw new IllegalStateException("Huh? " + oldEnd);
                }
                int shiftSubequentNewlinesStartingAt
                        = newlinePositions.nearestIndexToPresumingSorted(
                                oldEnd, Bias.FORWARD);
                if (shiftSubequentNewlinesStartingAt >= 0) {
                    newlinePositions.adjustValues(shiftSubequentNewlinesStartingAt, lengthDiff);
                }
            }
            if (!newNewlineSet.isEmpty() && !newNewlineSet.equals(oldNewlineSet)) {
                for (int i = 0; i < newNewlines.length; i++) {
                    if (!oldNewlineSet.contains(newNewlines[i])) {
                        newlinePositions.add(oldStart + newNewlines[i]);
                        newlinesNeedSort = true;
                    }
                }
            }
            if (newlinesNeedSort) {
                newlinePositions.sort();
            }
            if (lengthChanged) {
                // XXX will this wind us up with duplicate start indices, and
                // do we need another custom binrary search variant to deal
                // with it?  A deleted token would wind us up with two
                // identical start positions, one for the removed token
                startPositions.adjustValues(index + 1, lengthDiff);
            }
        }
    }

    @Override
    public void replace(Token tok, String text) {
        replace(tok.getTokenIndex(), text);
    }

    @Override
    public void replace(int index, String text) {
        RewriteInfo info = rewritesForToken.get(index);
        int[] oldNewlines = info.newlinePositions(stream.get(index));
        int oldLength = info.length(stream.get(index));
        info.setReplacement(text);
        int newLength = info.length(stream.get(index));
        newlinesMayBeChanged(info, index, oldNewlines, oldLength, newLength);
    }

    @Override
    public int lastNewlineDistance(int tokenIndex) {
        if (cachedLastRequestedNewlineDistance == tokenIndex) {
            return cachedNewlineDistance;
        }
        cachedLastRequestedNewlineDistance = tokenIndex;
        if (tokenIndex == 0) {
            return cachedNewlineDistance = 0;
        }
        int start = startPositions.get(tokenIndex);
        int valIx = newlinePositions.nearestIndexToPresumingSorted(start - 1, Bias.BACKWARD);
        if (valIx == -1) {
            return cachedNewlineDistance = start;
        }
        int result = Math.max(0, (start - newlinePositions.get(valIx)) - 1);
        return cachedNewlineDistance = result;
    }

    @Override
    public String rewrittenText(int index) {
        ModalToken tok = stream.get(index);
        RewriteInfo info = this.rewritesForToken.containsKey(index)
                ? rewritesForToken.get(index) : null;
        if (info != null) {
            StringBuilder sb = new StringBuilder((tok.getStopIndex() - tok.getStartIndex()) + 10);
            info.rewrite(sb, tok);
            return sb.toString();
        }
        return tok.getText();
    }

    static String elideSpaces(String orig, StringBuilder into) {
        orig = Strings.escape(orig, Escaper.NEWLINES_AND_OTHER_WHITESPACE);
        int spaceCount = 0;
        int len = orig.length();
        for (int i = 0; i < len; i++) {
            char c = orig.charAt(i);
            if (c == ' ') {
                spaceCount++;
            }
            if (c != ' ' || i == len - 1) {
                if (spaceCount > 0) {
                    into.append('-').append(spaceCount).append("sp-");
                }
                if (c != ' ') {
                    into.append(c);
                }
            }
        }
        return into.toString();
    }

    static class RewriteInfo {

        private String before;
        private String replacement;
        private String after;
        int[] newlinePositions;
        boolean hasAddedNewlines;
        boolean deleted;

        RewriteInfo() {

        }

        void delete() {
            deleted = true;
        }

        @Override
        public String toString() {
            if (deleted) {
                return "DEL";
            }
            StringBuilder sb = new StringBuilder();
            if (before != null) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append("bef:");
                elideSpaces(before, sb);
            }
            if (replacement != null) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append("repl:");
                elideSpaces(replacement, sb);
            }
            if (after != null) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append("aft:");
                elideSpaces(after, sb);
            }
            return sb.toString();
        }

        boolean hasAddedNewlines() {
            return hasAddedNewlines;
        }

        void rewrite(StringBuilder into, ModalToken what) {
            if (deleted) {
                return;
            }
            if (before != null) {
                into.append(before);
            }
            if (replacement != null) {
                into.append(replacement);
            } else {
                into.append(what.getText());
            }
            if (after != null) {
                into.append(after);
            }
        }

        int[] newlinePositions(ModalToken tok) {
            if (deleted) {
                return new int[0];
            }
            if (newlinePositions != null) {
                return newlinePositions;
            }
            int base = 0;
            if (before == null && after == null && replacement == null) {
                int[] nlp = tok.newlinePositions();
                if (base != 0 && nlp.length > 0) {
                    nlp = Arrays.copyOf(nlp, nlp.length);
                    for (int i = 0; i < nlp.length; i++) {
                        nlp[i] += base;
                    }
                }
                return newlinePositions = nlp;
            }
            IntList l = IntList.create(16);
            int len = 0;
            if (before != null) {
                for (int i = 0; i < before.length(); i++) {
                    if (before.charAt(i) == '\n') {
                        l.add(len + base);
                    }
                    len++;
                }
            }
            if (replacement != null) {
                for (int i = 0; i < replacement.length(); i++) {
                    if (replacement.charAt(i) == '\n') {
                        l.add(len + base);
                    }
                    len++;
                }
            } else {
                for (int pos : tok.newlinePositions()) {
                    l.add(len + pos);
                }
                len += tok.getText().length();
            }
            if (after != null) {
                for (int i = 0; i < after.length(); i++) {
                    if (after.charAt(i) == '\n') {
                        l.add(len + base);
                    }
                    len++;
                }
            }
            return newlinePositions = l.toIntArray();
        }

        int length(ModalToken tok) {
            if (deleted) {
                return 0;
            }
            int result = 0;
            if (before != null) {
                result += before.length();
            }
            if (replacement != null) {
                result += replacement.length();
            } else {
                result += tok.getText().length();
            }
            if (after != null) {
                result += after.length();
            }
            return result;
        }

        void setAfter(String after) {
            if (this.after != null) {
                throw new IllegalStateException("Setting after to "
                        + Strings.escape(after, Escaper.NEWLINES_AND_OTHER_WHITESPACE)
                        + " when already set to "
                        + Strings.escape(this.after, Escaper.NEWLINES_AND_OTHER_WHITESPACE));
            }
            this.after = after;
            hasAddedNewlines |= after.indexOf('\n') >= 0;
            newlinePositions = null;
        }

        void setBefore(String before) {
            if (this.before != null) {
                throw new IllegalStateException("Setting before to "
                        + Strings.escape(before, Escaper.NEWLINES_AND_OTHER_WHITESPACE)
                        + " when already set to "
                        + Strings.escape(this.before, Escaper.NEWLINES_AND_OTHER_WHITESPACE));
            }
            this.before = before;
            hasAddedNewlines |= before.indexOf('\n') >= 0;
            newlinePositions = null;
        }

        void setReplacement(String replacement) {
            if (this.replacement != null) {
                throw new IllegalStateException("Setting replacement to "
                        + Strings.escape(replacement, Escaper.NEWLINES_AND_OTHER_WHITESPACE)
                        + " when already set to "
                        + Strings.escape(this.replacement, Escaper.NEWLINES_AND_OTHER_WHITESPACE));
            }
            this.replacement = replacement;
            hasAddedNewlines |= replacement.indexOf('\n') >= 0;
            newlinePositions = null;
        }
    }

}
