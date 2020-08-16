/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.error.highlighting;

import com.mastfrog.function.state.Bool;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.error.highlighting.hints.util.EditorAttributesFinder;
import org.nemesis.antlr.live.RebuildSubscriptions;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.antlr.spi.language.highlighting.AbstractHighlighter;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.openide.filesystems.FileObject;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrImportedItemsHiglighter extends AbstractHighlighter implements Subscriber, Runnable {

    private Runnable unsubscriber;
    private final EditorAttributesFinder attrsFinder = new EditorAttributesFinder();
    private final AtomicReference<Extraction> lastExtraction = new AtomicReference<>();
    private final RequestProcessor.Task task;

    AntlrImportedItemsHiglighter(HighlightsLayerFactory.Context ctx) {
        super(ctx, true);
        task = threadPool().create(this, false);
    }

    @MimeRegistrations({
        @MimeRegistration(mimeType = ANTLR_MIME_TYPE,
                service = HighlightsLayerFactory.class, position = 50)
    })
    public static HighlightsLayerFactory factory() {
        return AbstractHighlighter.factory("antlr-foreign-tokens", ZOrder.SYNTAX_RACK, ctx -> new AntlrImportedItemsHiglighter(ctx));
    }

    @Override
    protected void activated(FileObject file, Document doc) {
        unsubscriber = RebuildSubscriptions.subscribe(file, this);
    }

    @Override
    protected void deactivated(FileObject file, Document doc) {
        task.cancel();
        Runnable un = unsubscriber;
        if (un != null) {
            un.run();
        }
    }

    @Override
    public void onRebuilt(ANTLRv4Parser.GrammarFileContext tree, String mimeType, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes) {
        // What we are doing here is far more expensive than normal highlighting,
        // so get it out of the critical path of rebuilds and coalesce as much as
        // possible
        lastExtraction.set(extraction);
        task.schedule(1500);
    }

    @Override
    public void run() {
        Bool wasRun = Bool.create();
        Extraction extraction = lastExtraction.getAndSet(null);
        if (isActive() && extraction != null) {
            if (!extraction.unknowns(AntlrKeys.RULE_NAME_REFERENCES).isEmpty()) {
                Attributions<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> attributions
                        = extraction.resolveAll(AntlrKeys.RULE_NAME_REFERENCES);
                SemanticRegions<AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes>> attributed = attributions.attributed();
                if (!attributed.isEmpty()) {
                    Map<RuleTypes, AttributeSet> cache = new HashMap<>(RuleTypes.values().length);
                    super.updateHighlights(bag -> {
                        wasRun.set();
                        attributed.forEach(region -> {
                            AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> attribution = region.key();
                            RuleTypes kind = attribution.element().kind();
                            AttributeSet attrs = cache.computeIfAbsent(kind, rt -> rt == null ? null : attrsFinder.apply(rt.get()));
                            if (attrs != null) {
                                bag.addHighlight(region.start(), region.end(), attrs);
                            }
                        });
                        return true;
                    });
                }
            }
        }
        wasRun.ifUntrue(() -> {
            // clear the highlights
            updateHighlights(bag -> false);
        });
    }
}
