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

import java.util.List;
import java.util.logging.Level;
import static org.nemesis.adhoc.mime.types.AdhocMimeTypes.loggableMimeType;
import org.nemesis.antlr.live.language.AdhocHighlighterManager.HighlightingInfo;
import org.nemesis.antlr.live.language.coloring.AdhocHighlightsContainer;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;
import org.netbeans.spi.editor.highlighting.HighlightsContainer;
import org.netbeans.spi.editor.highlighting.ZOrder;

/**
 *
 * @author Tim Boudreau
 */
public final class AdhocRuleHighlighter extends AbstractAntlrHighlighter {

    private final AdhocHighlightsContainer bag;

    @SuppressWarnings("LeakingThisInConstructor")
    public AdhocRuleHighlighter(AdhocHighlighterManager mgr) {
        super(mgr, ZOrder.SYNTAX_RACK.forPosition(2000));
        bag = new AdhocHighlightsContainer();
    }

    @Override
    public String toString() {
        return AdhocRuleHighlighter.class.getSimpleName() + " for " + loggableMimeType(mgr.mimeType());
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
        // Document may have had content deleted
        bag.update(mgr.colorings(), info.semantics, Math.min(info.doc.getLength(), info.semantics.text().length()));
    }

    @Override
    public final HighlightsContainer getHighlightsBag() {
        return bag;
    }
}
