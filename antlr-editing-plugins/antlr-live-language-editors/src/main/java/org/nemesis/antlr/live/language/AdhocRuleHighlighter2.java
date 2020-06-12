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
package org.nemesis.antlr.live.language;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import static org.nemesis.adhoc.mime.types.AdhocMimeTypes.loggableMimeType;
import org.nemesis.antlr.live.language.AdhocHighlighterManager.HighlightingInfo;
import org.nemesis.antlr.live.language.coloring.AdhocColoring;
import org.nemesis.antlr.live.language.coloring.AdhocColorings;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;

/**
 *
 * @author Tim Boudreau
 */
public final class AdhocRuleHighlighter2 extends AbstractAntlrHighlighter {

    private final OffsetsBag bag;

    @SuppressWarnings("LeakingThisInConstructor")
    public AdhocRuleHighlighter2(AdhocHighlighterManager mgr) {
        super(mgr, ZOrder.SYNTAX_RACK.forPosition(2000));
        bag = new OffsetsBag(mgr.document(), true);
    }

    @Override
    public String toString() {
        return AdhocRuleHighlighter2.class.getSimpleName() + " for " + loggableMimeType(mgr.mimeType());
    }

    @Override
    protected void refresh(HighlightingInfo info) {
        LOG.log(Level.FINEST, "Refresh {0} with {1}", new Object[]{this, info});
        if (info.semantics.isUnparsed() || info.semantics.text() == null || info.semantics.text().length() == 0) {
            LOG.log(Level.INFO, "Unusable parse: {0}", new Object[]{info.semantics.loggingInfo()});
            List<AntlrProxies.ProxySyntaxError> errs = info.semantics.syntaxErrors();
            if (errs != null) {
                for (AntlrProxies.ProxySyntaxError err : errs) {
                    LOG.log(Level.INFO, "Error: {0}", err);
                }
            }
            return;
        }
        update(mgr.colorings(), info.semantics, info.semantics.text().length());
    }

    public final HighlightsContainer getHighlightsBag() {
        return bag;
    }

    private void update(AdhocColorings colorings, AntlrProxies.ParseTreeProxy semantics, int length) {
        OffsetsBag nue = new OffsetsBag(mgr.document(), true);
        List<AntlrProxies.ProxyToken> tokens = semantics.tokens();
        if (tokens.isEmpty()) {
            bag.clear();
        }
        List<AntlrProxies.ParseTreeElement> ruleElements = new ArrayList<>(semantics.allTreeElements());
        for (AntlrProxies.ParseTreeElement el : ruleElements) {
            if (el.kind() == AntlrProxies.ParseTreeElementKind.RULE) {
                AntlrProxies.RuleNodeTreeElement rn = (AntlrProxies.RuleNodeTreeElement) el;
                AdhocColoring a = colorings.get(rn.name());
                // Ignore rules with no coloring, or rules which do not have
                // highlighting activated
                if (a != null && a.isActive() && !a.isEmpty()) {
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
                    nue.addHighlight(start, end, a);
//                    DepthAttributeSet s = new DepthAttributeSet(a, rn.depth());
//                    DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> range
//                            = Range.of(start, end - start, s);
//                    if (last != null && last.start() == start && last.end() == end) {
//                        // iterate back and while the immediately preceding range
//                        // is exactly the same offsets (two rules with highlighting
//                        // where the parent has one child rule, so they have the same
//                        // bounds), and merge those now, so we ensure the top-most
//                        // item includes the deepest coloring rules in case of conflict
//                        while (!ranges.isEmpty()) {
//                            // Now, go back up and pre-coalesce only identically sized
//                            // highlights
//                            last = ranges.get(ranges.size() - 1);
//                            if (last.start() == start && last.end() == end && last.get() instanceof DepthAttributeSet) {
//                                DepthAttributeSet ds = (DepthAttributeSet) last.get();
//                                // If all of the below are set, marging is not going to change anything,
//                                // so just remove the item
//                                if (!s.isBackgroundColor() || !s.isItalic() || !s.isForegroundColor() || !s.isBold()) {
//                                    s = new DepthAttributeSet(AdhocColoring.merge(ds.delegate(), s.delegate()), rn.depth());
//                                    range = Range.of(start, end - start, s);
//                                }
//                                ranges.remove(ranges.size() - 1);
//                            } else {
//                                break;
//                            }
//                        }
//                    }
//                    ranges.add(range);
//                    last = range;
//                }
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
                    nue.addHighlight(start, end, a);
//                DataIntRange<AdhocAttributeSet, ? extends DataIntRange<AdhocAttributeSet, ?>> range = Range.of(start, len, a);
//                ranges.add(range);
                }
            }

            bag.setHighlights(nue);
        }
    }
}