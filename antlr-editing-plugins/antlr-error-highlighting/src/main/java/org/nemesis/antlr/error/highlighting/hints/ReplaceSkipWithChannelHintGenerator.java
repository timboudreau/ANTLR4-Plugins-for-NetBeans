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
package org.nemesis.antlr.error.highlighting.hints;

import org.nemesis.antlr.error.highlighting.spi.AntlrHintGenerator;
import org.nemesis.antlr.error.highlighting.spi.NonHighlightingHintGenerator;
import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.error.highlighting.ChannelsAndSkipExtractors;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({
    "skipBad=Using skip creates problems for editors which cannot ignore tokens. Replace with a channel directive.",
    "# {0} - n",
    "replaceWithChannel=Replace skip with channel({0})?"
})
@ServiceProvider(service = AntlrHintGenerator.class)
public final class ReplaceSkipWithChannelHintGenerator extends NonHighlightingHintGenerator {

    @Override
    protected void generate(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate, Fixes fixes,
            Document doc, PositionFactory positions) throws BadLocationException {
        SemanticRegions<ChannelsAndSkipExtractors.ChannelOrSkipInfo> all = extraction.regions(ChannelsAndSkipExtractors.CHSKIP);
        int[] maxUsedChannel = new int[1];
        List<SemanticRegion<ChannelsAndSkipExtractors.ChannelOrSkipInfo>> skips = new ArrayList<>();
        all.forEach(maybeSkip -> {
            switch (maybeSkip.key().kind()) {
                case CHANNEL:
                    maxUsedChannel[0] = Math.max(maybeSkip.key().channelNumber(), maxUsedChannel[0]);
                    return;
                default:
                    String errId = "skip-" + maybeSkip.key() + "-" + maybeSkip.index();
                    if (!fixes.isUsedErrorId(errId)) {
                        skips.add(maybeSkip);
                    }
            }
        });
        if (!skips.isEmpty()) {
            maxUsedChannel[0]++;
            for (SemanticRegion<ChannelsAndSkipExtractors.ChannelOrSkipInfo> region : skips) {
                String errId = "skip-" + region.key() + "-" + region.index();
                PositionRange rng = positions.range(region);
                try {
                    fixes.addWarning(errId, region, Bundle.skipBad(), fixen -> {
                        fixen.addFix(true, () -> Bundle.replaceWithChannel(maxUsedChannel[0]), bag -> {
                            bag.replace(rng, () -> "channel (" + maxUsedChannel[0] + ")");
                        });
                        fixen.addFix(true, () -> Bundle.replaceWithChannel(maxUsedChannel[0] + 1), bag -> {
                            bag.replace(rng, () -> "channel (" + (maxUsedChannel[0] + 1) + ")");
                        });
                    });
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }
}
