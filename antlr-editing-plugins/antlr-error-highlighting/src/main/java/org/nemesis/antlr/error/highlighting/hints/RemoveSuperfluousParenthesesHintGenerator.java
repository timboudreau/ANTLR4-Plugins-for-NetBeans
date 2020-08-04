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
import com.mastfrog.function.state.Bool;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Segment;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.error.highlighting.ChannelsAndSkipExtractors.SUPERFLUOUS_PARENTEHSES;
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
    "unneededParentheses=Superfluous parentheses",
    "# {0} - content",
    "superfluousParentheses=Superfluous parentheses `{0}`. Remove?"
})
@ServiceProvider(service = AntlrHintGenerator.class)
public final class RemoveSuperfluousParenthesesHintGenerator extends NonHighlightingHintGenerator {

    public static String elidedContent(String content) {
        if (content.length() <= 12) {
            return content;
        }
        String head = content.substring(0, 5);
        String tail = content.substring(content.length() - 6);
        return head + "\u2026" + tail; // ellipsis
    }

    @Override
    protected void generate(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction, 
            AntlrGenerationResult res, ParseResultContents populate, Fixes fixes,
            Document doc, PositionFactory positions) throws BadLocationException {
        SemanticRegions<Integer> regs = extraction.regions(SUPERFLUOUS_PARENTEHSES);
        for (SemanticRegion<Integer> reg : regs) {
            String errId = "sp-" + reg.key();
            fixes.ifUnusedErrorId(errId, () -> {
                PositionRange rng = positions.range(reg);
                if (reg.start() < reg.stop()) {
                    fixes.addHint(errId, rng, Bundle.unneededParentheses(), fixen -> {
                        Segment seg = new Segment();
                        Bool failed = Bool.create();
                        doc.render(() -> {
                            try {
                                int startOffset = reg.start() + 1;
                                int endOffset = reg.stop();
                                doc.getText(startOffset, endOffset - startOffset, seg);
                            } catch (BadLocationException ex) {
                                Exceptions.printStackTrace(ex);
                                failed.set();
                            }
                        });
                        failed.ifUntrue(() -> {
                            fixen.addFix(Bundle.superfluousParentheses("(" + seg + ")"), bag -> {
                                bag.replace(rng, seg.toString().trim());
                            });
                        });
                    });
                }
            });
        }
    }
}