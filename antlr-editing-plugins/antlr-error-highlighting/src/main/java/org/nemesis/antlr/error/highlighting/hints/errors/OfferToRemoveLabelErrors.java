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
package org.nemesis.antlr.error.highlighting.hints.errors;

import com.mastfrog.function.state.Bool;
import com.mastfrog.function.state.Int;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Segment;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.error.highlighting.hints.util.EditorAttributesFinder;
import org.nemesis.antlr.error.highlighting.spi.ErrorHintGenerator;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.antlr.spi.language.highlighting.HighlightConsumer;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = ErrorHintGenerator.class)
@Messages({
    "illegalLabel=Cannot use a label here.  Remove?"
})
public class OfferToRemoveLabelErrors extends ErrorHintGenerator {

    public OfferToRemoveLabelErrors() {
        super(
                130, // Parser rule Label assigned to a block which is not a set
                201 // label in a lexer rule
        );
    }

    @Override
    protected boolean handle(ANTLRv4Parser.GrammarFileContext tree, ParsedAntlrError err, Fixes fixes,
            Extraction ext, Document d, PositionFactory positions, HighlightConsumer brandNewBag,
            Bool anyHighlights, Supplier<EditorAttributesFinder> colorings) throws BadLocationException {
        Bool added = Bool.create();
        String errorIdentifier = err.id();
        offsetsOf(d, err, (start, end) -> {
            if (end > start) {
                PositionRange pbr = positions.range(start, end);
                fixes.addError(pbr, err.message(), Bundle::illegalLabel, fixen -> {
                    Int realEnd = Int.of(-1);
                    d.render(() -> {
                        Segment seg = new Segment();
                        int len = d.getLength();
                        if (len > pbr.end()) {
                            try {
                                d.getText(pbr.end(), len - pbr.end(), seg);
                                for (int i = 0; i < seg.length(); i++) {
                                    if ('=' == seg.charAt(i)) {
                                        realEnd.set(i + 1);
                                        return;
                                    }
                                }
                            } catch (BadLocationException ex) {
                                LOG.log(Level.SEVERE, null, ex);
                            }
                        }
                    });
                    if (realEnd.getAsInt() > pbr.start()) {
                        brandNewBag.addHighlight(start, end, colorings.get().errors());
                        anyHighlights.set();
                        // Need to delay this test to here, or highlighting
                        // may be intermittent
                        if (!fixes.isUsedErrorId(errorIdentifier)) {
                            fixen.addFix(Bundle.illegalLabel(), bag -> {
                                bag.delete(pbr);
                            });
                        }
                        added.set();
                    } else {
                        LOG.log(Level.FINE, "Start end mismatch {0} vs {1} in {2}",
                                new Object[]{pbr.start(), realEnd.getAsInt(), err});
                    }
                });
            }
        });
        return added.get();
    }
}
