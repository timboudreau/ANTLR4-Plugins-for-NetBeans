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
import org.openide.util.Mutex;

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
    public  void refresh(HighlightingInfo info) {
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
                AdhocColoring a = colorings.get(rn.name(semantics));
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
                        end = Math.min(length - 1, endToken.getEndIndex());
                    }
                    // We may be handed a proxy for text that has changed
                    if (start > length || end > length) {
                        continue;
                    }
                    if (end <= start) {
                        continue;
                    }
                    nue.addHighlight(start, end, a);
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
                String category = type.category();
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
                    int len = tok.length();
                    if (len == 0) {
                        continue;
                    }
                    if (start + len > length) {
                        len = length - start;
                    }
                    nue.addHighlight(start, end, a);
                }
            }
            // Avoids flashing
            Mutex.EVENT.readAccess(() -> {
                bag.setHighlights(nue);
            });
        }
    }
}
