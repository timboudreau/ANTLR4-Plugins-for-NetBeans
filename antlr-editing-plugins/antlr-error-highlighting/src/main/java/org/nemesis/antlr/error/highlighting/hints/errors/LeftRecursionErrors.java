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
import com.mastfrog.graph.ObjectPath;
import java.awt.Color;
import static java.awt.Color.BLACK;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import static javax.swing.text.StyleConstants.Background;
import static javax.swing.text.StyleConstants.Foreground;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.error.highlighting.hints.errors.RecursionAnalyzer.NameReference;
import org.nemesis.antlr.error.highlighting.hints.util.EditorAttributesFinder;
import org.nemesis.antlr.error.highlighting.spi.ErrorHintGenerator;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.antlr.spi.language.highlighting.HighlightConsumer;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = ErrorHintGenerator.class)
public class LeftRecursionErrors extends ErrorHintGenerator {

    private static final Pattern LEFT_RECURSION_ERROR_MSG_PATTERN = Pattern.compile("^(.*?)\\s*\\[(.*?)\\].*");

    public LeftRecursionErrors() {
        super(119); // e.g., "The following sets of rules are mutually left-recursive [foo, baz, koog]"
    }

    @Messages({
        "# {0} - path start",
        "# {1} - path end",
        "# {2} - paths as html",
        "leftRecursionTip=<html>Causes left-recusion of <code>{0}</code> to <code>{1}</code> via <ul>{2}"
    })
    @Override
    protected boolean handle(ANTLRv4Parser.GrammarFileContext tree, ParsedAntlrError err,
            Fixes fixes, Extraction ext, Document doc, PositionFactory positions,
            HighlightConsumer brandNewBag, Bool anyHighlights,
            Supplier<EditorAttributesFinder> colorings) throws BadLocationException {
        int bstart = err.message().lastIndexOf('[') + 1;
        int bend = err.message().lastIndexOf(']') + 1;
        boolean res = false;
        if (bend > bstart + 1 && bstart > 0) {
            String sub = err.message().substring(bstart, bend - 1);
            NamedSemanticRegions<RuleTypes> regions = ext.namedRegions(AntlrKeys.RULE_NAMES);
            String[] all = sub.split(",\\s*");
            String first = all.length > 0 ? all[0].trim() : null;
            if (first != null) {
                String eid = err.id();

                Map<RecursionAnalyzer.NameReference, Set<ObjectPath<String>>> m = new RecursionAnalyzer().analyze(tree, ext, new LinkedHashSet<>(Arrays.asList(all)));
                for (Map.Entry<RecursionAnalyzer.NameReference, Set<ObjectPath<String>>> e : m.entrySet()) {
                    if (e.getValue().isEmpty()) {
                        continue;
                    }
                    SimpleAttributeSet s = new SimpleAttributeSet();
                    s.addAttribute(Background, new Color(255, 255, 0, 160));
                    s.addAttribute(Foreground, BLACK);

                    StringBuilder tip = new StringBuilder();
                    for (ObjectPath<String> op : e.getValue()) {
                        tip.append("<li>").append(op).append("</li>");
                    }
                    tip.append("</ul>");
                    NameReference nr = e.getKey();
                    ObjectPath<String> firstPath = e.getValue().iterator().next();
                    s.addAttribute(EditorStyleConstants.Tooltip, Bundle.leftRecursionTip(firstPath.start(), firstPath.end(), tip));
                    brandNewBag.addHighlight(nr.start, nr.end, AttributesUtilities.createImmutable(s));
                }
//                Map<RecursionAnalyzer.NamePair, Set<RecursionAnalyzer.NameReference>> m = new RecursionAnalyzer().analyze(tree, ext, new LinkedHashSet<>(Arrays.asList(all)));
//                for (Map.Entry<RecursionAnalyzer.NamePair, Set<RecursionAnalyzer.NameReference>> e : m.entrySet()) {
//                    SimpleAttributeSet s = new SimpleAttributeSet();
//                    s.addAttribute(Background, new Color(255, 255, 0, 160));
//                    s.addAttribute(Foreground, BLACK);
//                    for (RecursionAnalyzer.NameReference nr : e.getValue()) {
//                        s.addAttribute(EditorStyleConstants.Tooltip, "Causes left recursion: " + e.getKey().toString());
//                        AttributeSet s1 = AttributesUtilities.createImmutable(s);
//                        brandNewBag.addHighlight(nr.start, nr.end, s1);
//                    }
//                }
                NamedSemanticRegion<RuleTypes> reg = regions.regionFor(first);
                if (reg == null) {
                    LOG.log(Level.INFO, "Did not find a region for ''{0}'' in "
                            + "{1}", new Object[]{first, err.message()});
                    if (err.hasFileOffset()) {
                        String smsg = stringifyLeftRecursionMessage(err.message());
                        fixes.addError(eid, err.fileOffset(), err.fileOffset()
                                + err.length(), smsg, () -> htmlifyLeftRecursionMessage(err.message()));
                        res = true;
                    }
                    return res;
                }
                brandNewBag.addHighlight(reg.start(), reg.end(), colorings.get().errors());
                anyHighlights.set();

                if (!fixes.isUsedErrorId(eid)) {
                    String smsg = stringifyLeftRecursionMessage(err.message());
                    fixes.addError(eid, reg, smsg, () -> htmlifyLeftRecursionMessage(err.message()));
                    res = true;
                } else {
                    LOG.log(Level.FINEST, "Already handled eid {0} for {1}",
                            new Object[]{eid, first});
                }
            } else {
                LOG.log(Level.FINER, "Did not find any rule names in {0}", err.message());
            }

        }
        return res;
    }

    private static String stringifyLeftRecursionMessage(String msg) {
        Matcher m = LEFT_RECURSION_ERROR_MSG_PATTERN.matcher(msg);
        if (m.find()) {
            String[] parts = m.group(2).split(",");
            StringBuilder sb = new StringBuilder(msg.length() + (parts.length * 4));
            sb.append(m.group(1));
            sb.append(' ');
            for (int i = 0; i < parts.length; i++) {
                sb.append(' ').append(i + 1).append(". ").append(parts[i]);
            }
            return sb.toString();
        }
        return msg;

    }

    private static String htmlifyLeftRecursionMessage(String msg) {
        Matcher m = LEFT_RECURSION_ERROR_MSG_PATTERN.matcher(msg);
        if (m.find()) {
            StringBuilder sb = new StringBuilder("<html>");
            sb.append(m.group(1));
            String[] parts = m.group(2).split(",");
            sb.append("<ul>");
            for (String part : parts) {
                sb.append("<li>").append(part).append("</li>");
            }
            return sb.append("</ul></html>").toString();
        }
        return msg;
    }
}
