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
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.error.highlighting.hints.util.EditorAttributesFinder;
import org.nemesis.antlr.error.highlighting.spi.ErrorHintGenerator;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@Messages({
    "# {0} - unknownRuleName",
    "# {1} - newName",
    "replaceWith=Replace {0} with {1}?",
    "# {0} - unknownName",
    "replaceHeading=Unknown rule {0}"
})
@ServiceProvider(service = ErrorHintGenerator.class)
public class UndefinedRuleRefSuggester extends ErrorHintGenerator {

    private static final Pattern PAT_56 = Pattern.compile("^.*?:(.*)");
    private static final Pattern PAT_57 = Pattern.compile("^.*rule\\s*(\\S+)");

    public UndefinedRuleRefSuggester() {
        super(
                56, // UNDEFINED_RULE_REF(56, "reference to undefined rule: <arg>",
                57 // UNDEFINED_RULE_IN_NONLOCAL_REF(57, "reference to undefined rule <arg> in non-local ref <arg3>"
        );
    }

    @Override
    @SuppressWarnings("null")
    protected boolean handle(ANTLRv4Parser.GrammarFileContext tree, ParsedAntlrError err,
            Fixes fixes, Extraction ext, Document doc, PositionFactory positions,
            OffsetsBag brandNewBag, Bool anyHighlights,
            Supplier<EditorAttributesFinder> colorings) throws BadLocationException {
        return withOffsetsOf(doc, err, (start, end) -> {
            brandNewBag.addHighlight(start, end, colorings.get().errors());
            Matcher m = null;
            switch (err.code()) {
                case 56:
                    m = PAT_56.matcher(err.message());
                    break;
                case 57:
                    m = PAT_57.matcher(err.message());
                    break;
                default:
                    return false;
            }
            if (m.find()) {
                String ruleName = m.group(1);
                PositionRange bounds = positions.range(start, end);
                fixes.ifUnusedErrorId(err.id(), () -> {
                    fixes.add(err.id(), Severity.ERROR, Bundle.replaceHeading(ruleName), bounds, err::message, fixen -> {
                        NamedSemanticRegions<RuleTypes> names = ext.namedRegions(AntlrKeys.RULE_NAMES);
                        names.topSimilarNames(ruleName, 5).forEach(name -> {
                            Supplier<String> msg = () -> Bundle.replaceWith(ruleName, name);
                            fixen.addFix(true, msg, bag -> {
                                bag.replace(bounds, name);
                            });
                        });
                    });
                });
                return true;
            }
            return false;
        });
    }
}
