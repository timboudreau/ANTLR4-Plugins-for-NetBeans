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
import java.util.logging.Level;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Segment;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.error.highlighting.HintsAndErrorsExtractors.SUPERFLUOUS_PARENTEHSES;
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

    @Override
    protected void generate(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate, Fixes fixes,
            Document doc, PositionFactory positions) throws BadLocationException {
        SemanticRegions<Integer> regs = extraction.regions(SUPERFLUOUS_PARENTEHSES);
        LOG.log(Level.FINE, "Check superfluous parentheses {0} with {1} regions: {2}",
                new Object[]{extraction.source(), regs.size(), regs});
        for (SemanticRegion<Integer> reg : regs) {
            String errId = "sp-" + reg.key();
            fixes.ifUnusedErrorId(errId, () -> {
                PositionRange rng = positions.range(reg);
                if (reg.start() < reg.stop()) {
                    fixes.addHint(errId, rng, Bundle.unneededParentheses(), fixen -> {
                        Segment seg = new Segment();
                        Bool failed = Bool.create();
                        Bool appendSpace = Bool.create();
                        doc.render(() -> {
                            try {
                                int startOffset = reg.start() + 1;
                                int endOffset = reg.stop();
                                doc.getText(startOffset, endOffset - startOffset, seg);
                                if (endOffset < doc.getLength() - 1) {
                                    // A rule such as
                                    // ID : ('a'..'z'|'A'..'Z')('a'..'z'|'A'..'Z'|'0'..'9' | '_')*;
                                    // should get a space where the first ) is
                                    appendSpace.set(doc.getText(reg.end(), 1).charAt(0) == '(');
                                }
                            } catch (BadLocationException ex) {
                                Exceptions.printStackTrace(ex);
                                failed.set();
                            }
                        });
                        failed.ifUntrue(() -> {
                            LOG.log(Level.FINEST, "Superfluous parens at {0} in {1}",
                                    new Object[]{reg, extraction.source()});
                            fixen.addFix(Bundle.superfluousParentheses("("
                                    + elidedContent(seg.toString()) + ")"), bag -> {
                                String txt = seg.toString().trim();
                                if (appendSpace.get()) {
                                    txt += ' ';
                                }
                                bag.replace(rng, txt);
                            });
                        });
                    });
                }
            });
        }
    }
}
