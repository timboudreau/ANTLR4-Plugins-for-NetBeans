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
import static com.mastfrog.util.strings.Strings.capitalize;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.error.highlighting.hints.util.EditorAttributesFinder;
import org.nemesis.antlr.error.highlighting.hints.util.RuleNamingConvention;
import org.nemesis.antlr.error.highlighting.spi.ErrorHintGenerator;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.antlr.spi.language.highlighting.HighlightConsumer;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@Messages({
    "capitalize=Capitalize name to make this a lexer rule",
    "# {0} - reason",
    "deleteRuleForReason=Delete rule? {0}"
})
@ServiceProvider(service = ErrorHintGenerator.class)
public class OfferToDeleteRuleErrors extends ErrorHintGenerator {

    public OfferToDeleteRuleErrors() {
        super(
                51, // rule redefinition
                52, // lexer rule in parser grammar
                53, // parser rule in lexer grammar
                184 // rule overlapped by other rule and will never be used
        );
    }

    @Override
    protected boolean handle(ANTLRv4Parser.GrammarFileContext tree, ParsedAntlrError err,
            Fixes fixes, Extraction ext, Document doc, PositionFactory positions,
            HighlightConsumer brandNewBag, Bool anyHighlights,
            Supplier<EditorAttributesFinder> colorings) throws BadLocationException {
        String errId = err.id();
        if (fixes.isUsedErrorId(errId)) {
            return false;
        }
        Bool handled = Bool.create();
        offsetsOf(doc, err, (start, end) -> {
            NamedSemanticRegion<RuleTypes> region = ext.namedRegions(AntlrKeys.RULE_BOUNDS).at(start);
            if (region == null) {
                LOG.log(Level.FINER, "No region at {0} for {1}", new Object[]{start, err});
                return;
            }
            PositionRange pr = positions.range(region);
            brandNewBag.addHighlight(start, end, colorings.get().errors());
            anyHighlights.set();
            fixes.addError(errId, region.start(), region.end(), err.message(), fixConsumer -> {
                if (err.code() == 53) {
                    String name = findRuleNameInErrorMessage(err.message());
                    if (name != null) {
                        try {
                            PositionRange rng = positions.range(start, end);
                            fixConsumer.addFix(Bundle.capitalize(), bag -> {
                                RuleNamingConvention convention = RuleNamingConvention.forExtraction(ext);
                                if (!convention.lexerRulesBiCapitalized()) {
                                    bag.replace(rng, name.toUpperCase());
                                } else {
                                    bag.replace(rng, capitalize(name));
                                }
                            });
                        } catch (BadLocationException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                }
                fixConsumer.addFix(Bundle.deleteRuleForReason(err.message()),
                        bag -> bag.delete(pr));
                handled.set(true);
            });
        });
        return handled.get();
    }

    private static String findRuleNameInErrorMessage(String msg) {
        String msgStart = "parser rule ";
        // parser rule
        if (msg.startsWith(msgStart) && msg.length() > msgStart.length()) {
            StringBuilder sb = new StringBuilder();
            for (int i = msgStart.length(); i < msg.length(); i++) {
                char c = msg.charAt(i);
                if (Character.isLetter(c) || Character.isDigit(c)) {
                    sb.append(c);
                } else {
                    break;
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return null;
    }
}
