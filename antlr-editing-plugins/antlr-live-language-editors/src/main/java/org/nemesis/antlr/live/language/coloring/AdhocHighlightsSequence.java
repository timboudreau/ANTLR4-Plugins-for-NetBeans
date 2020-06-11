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
package org.nemesis.antlr.live.language.coloring;

import com.mastfrog.range.Coalescer;
import com.mastfrog.range.DataIntRange;
import com.mastfrog.range.Range;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.AttributeSet;
import org.nemesis.antlr.live.language.AdhocTokenId;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies.ProxyToken;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;

/**
 * An extremely lightweight highlights sequence, taking advantage of the
 * merge-api's ability to do complex coalescing of overlapping ranges
 * efficiently.
 *
 * @author Tim Boudreau
 */
public class AdhocHighlightsSequence implements HighlightsSequence {

    private static final Logger LOG = Logger.getLogger(AdhocHighlightsSequence.class.getName());
    private final Iterator<DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>>> iter;
    private DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> current;

    public AdhocHighlightsSequence(AdhocColorings colorings, ParseTreeProxy semantics, int length) {
        this.iter = computeRanges(colorings, semantics, length).iterator();
    }

    public AdhocHighlightsSequence(Iterator<DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>>> iter) {
        this.iter = iter;
    }

    @Override
    public boolean moveNext() {
        if (iter.hasNext()) {
            current = iter.next();
            return true;
        }
        return false;
    }

    @Override
    public int getStartOffset() {
        if (current == null) {
            throw new NoSuchElementException();
        }
        return current.start();
    }

    @Override
    public int getEndOffset() {
        if (current == null) {
            throw new NoSuchElementException();
        }
        return current.end();
    }

    @Override
    public AttributeSet getAttributes() {
        if (current == null) {
            throw new NoSuchElementException();
        }
        AdhocAttributeSet result = current.get();
        assert result != null : current + " has a null attribute set";
        return result;
    }

    static List<DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>>> computeRanges(AdhocColorings colorings, ParseTreeProxy semantics, int length) {
        // Okay, how this all works:
        // The range library has generic support for creating ranges with data associated
        // with them, and more importantly, supports *coalescing* them with an implementation
        // of Coalescer to merge the data items.  In our case the data items are super-lightweight
        // attribute sets.
        //
        // The editor wants the output to be a set of entirely non-overlapping regions.
        List<ProxyToken> tokens = semantics.tokens();
        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }
        List<DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>>> ranges
                = new ArrayList<>(semantics.tokenCount());
        int allRuleCount = 0;
        int ruleCount = 0;
        List<AntlrProxies.ParseTreeElement> ruleElements = new ArrayList<>(semantics.allTreeElements());
        DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> last = null;
        // First we iterate the rule elements - these will all be pre-sorted in the order
        // they were encountered, so we have biggerRule -> nextSmallerOrSameSizeRule -> smallerOrSameSizeRule
        //
        // Some pre-coalescing is required for identically sized regions, where the order they will
        // be handed to the coalescer is non-deterministic, and we want the highlighting associated
        // with the deepest rule to win
        for (AntlrProxies.ParseTreeElement el : ruleElements) {
            if (el.kind() == AntlrProxies.ParseTreeElementKind.RULE) {
                AntlrProxies.RuleNodeTreeElement rn = (AntlrProxies.RuleNodeTreeElement) el;
                AdhocColoring a = colorings.get(rn.name());
                allRuleCount++;
                // Ignore rules with no coloring, or rules which do not have
                // highlighting activated
                if (a != null && a.isActive() && !a.isEmpty()) {
                    ruleCount++; // for logging
                    int start = rn.startTokenIndex();
                    int end = rn.stopTokenIndex();
                    if (start < 0 || end < 0) { // EOF or broken source
                        continue;
                    } else {
                        AntlrProxies.ProxyToken startToken = tokens.get(start);
                        // FIXME We *can* get passed an end token off the end
                        AntlrProxies.ProxyToken endToken = end >= tokens.size()
                                ? tokens.get(tokens.size() - 1) : tokens.get(end);
                        // Trim whitespace tokens off the tail of rule highlighting
                        while (endToken.isWhitespace() || endToken.isEOF()) {
                            end--;
                            if (end < 0) {
                                break;
                            }
                            endToken = end >= tokens.size()
                                    ? tokens.get(tokens.size() - 1) : tokens.get(end);
                        }
                        start = startToken.getStartIndex();
                        // Make sure the end is not after the end of the document, or
                        // we can wind up with a mess on edited but not yet re-lexed
                        // documents.
                        // Use the *trimmed* length of the end token, as it is not
                        // useful to highlight whitespace up to the start of the
                        // next rule
//                        end = Math.min(length - 1, endToken.getStartIndex() + endToken.trimmedLength() - 1);
                        end = Math.min(length - 1, endToken.getEndIndex());
                    }
                    // We may be handed a proxy for text that has changed
                    if (start > length || end > length) {
                        continue;
                    }
                    if (end <= start) {
                        continue;
                    }
                    // Record the depth - we try to use it in the coalescer (does this do anything
                    // at this point, or is it solved below?)
                    DepthAttributeSet s = new DepthAttributeSet(a, rn.depth());
                    DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> range
                            = Range.of(start, end - start, s);
                    if (last != null && last.start() == start && last.end() == end) {
                        // iterate back and while the immediately preceding range
                        // is exactly the same offsets (two rules with highlighting
                        // where the parent has one child rule, so they have the same
                        // bounds), and merge those now, so we ensure the top-most
                        // item includes the deepest coloring rules in case of conflict
                        while (!ranges.isEmpty()) {
                            // Now, go back up and pre-coalesce only identically sized
                            // highlights
                            last = ranges.get(ranges.size() - 1);
                            if (last.start() == start && last.end() == end && last.get() instanceof DepthAttributeSet) {
                                DepthAttributeSet ds = (DepthAttributeSet) last.get();
                                // If all of the below are set, marging is not going to change anything,
                                // so just remove the item
                                if (!s.isBackgroundColor() || !s.isItalic() || !s.isForegroundColor() || !s.isBold()) {
                                    s = new DepthAttributeSet(AdhocColoring.merge(ds.delegate(), s.delegate()), rn.depth());
                                    range = Range.of(start, end - start, s);
                                }
                                ranges.remove(ranges.size() - 1);
                            } else {
                                break;
                            }
                        }
                    }
                    ranges.add(range);
                    last = range;
                }
            }
        }
        // Now iterate the tokens - coalescing will take care of ordering
        // these properly.  We could do this inside the parsing rule loop,
        // and maintain order, but then there would be no syntax highlighting
        // for lexer-only grammars.  And coalescing will take care of the sorting.
        for (AntlrProxies.ProxyToken tok : tokens) {
            int tp = tok.getType();
            if (tp == -1) { // EOF
                break;
            }
            AntlrProxies.ProxyTokenType type = semantics.tokenTypeForInt(tp);
            // We can be highlighting stale text, if a repaint comes after
            // an edit but before a new proxy has been generated, so we
            // need to be permissive here
            if (tok.getStartIndex() > length || tok.getStopIndex() > length) {
                break;
            }
            String category = AdhocTokenId.categorize(type);
            // If no category, look for a coloring for the token name
            if ("default".equals(category)) {
                if (type.symbolicName != null && colorings.contains(type.symbolicName)) {
                    category = type.symbolicName;
                } else if (type.displayName != null && colorings.contains(type.displayName)) {
                    category = type.displayName;
                }
            }
            // Ignore the default category, that's what you get if no
            // highlighting, so no point in specifying it explicitly
            if ("default".equals(category)) {
                continue;
            }
            AdhocColoring a = colorings.get(category);
            if (a != null && a.isActive() && !a.isEmpty()) {
                int start = tok.getStartIndex();
                int end = tok.getEndIndex();
                if (end >= length) {
                    end = length - 1;
                }
                if (end <= start) { // EOF or goofiness
                    continue;
                }
                // Trim highlighting of tokens which have trailing whitespace
                // so that we don't highlight all the way up to the next token
//                int len = tok.trimmedLength();
//                if (len == 0 && tok.length() > 0) {
//                    // The token simply is whitespace, so we must actually need
//                    // to highlight it
//                    len = tok.length();
//                }
                int len = tok.length();
                if (len == 0) {
                    continue;
                }
                if (start + len > length) {
                    len = length - start;
                }

                DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> range = Range.of(start, len, a);
                ranges.add(range);
            }
        }
        // Now flatten out our list of ranges into a set of non-overlapping
        // ranges that merge attribute sets in the correct order.
        //
        // The API for Coalescer needs some fixing - DataIntRange is parameterized
        // on the package-private DataIntRangeImpl, and returned as ? - which
        // means no coalescer with that signature can be constructed, because like
        // enum, Range is parameterized on its own implementation type.  Ugh.
        ranges = Range.coalesce((List) ranges, (Coalescer) new C());
        LOG.log(Level.FINER, "rules highlighted: {0} of {1}", new Object[]{ruleCount, allRuleCount});
        return ranges;
    }

    static final class C implements Coalescer<DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>>> {

        @Override
        public DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> combine(
                DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> a,
                DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> b,
                int start, int size) {
            AdhocAttributeSet ca = a.get();
            AdhocAttributeSet cb = b.get();
            if (ca.isEqual(cb)) { // Same highlighting?  Can use either
                return a.newRange(start, size);
            }
            AdhocAttributeSet nue;
            // A bit of jujitsu to merge colorings so the smallest token /
            // deepest rule wins.  Maybe can delete some of this with the
            // coalescing we do above?
            if (ca.depth() > cb.depth()) {
                nue = AdhocColoring.merge(ca, cb);
            } else if (ca.depth() < cb.depth()) {
                if (a.start() == b.start() && a.end() == b.end()) {
                    nue = AdhocColoring.merge(cb, ca);
                } else {
                    nue = AdhocColoring.merge(ca, cb);
                }
            } else if (ca.depth() > cb.depth()) {
                nue = AdhocColoring.merge(cb, ca);
            } else if (a.size() == size) {
                nue = AdhocColoring.merge(cb, ca);
            } else if (b.size() == size) {
                nue = AdhocColoring.merge(ca, cb);
            } else if (a.size() <= b.size()) {
                nue = AdhocColoring.merge(ca, cb);
            } else {
                nue = AdhocColoring.merge(cb, ca);
            }
            DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> result
                    = Range.of(start, size, nue);
            return result;
        }
    }
}
