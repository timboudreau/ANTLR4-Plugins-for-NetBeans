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
package org.nemesis.antlr.live.language;

import com.mastfrog.colors.RotatingColors;
import com.mastfrog.graph.BitSetUtils;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.awt.Color;
import java.awt.EventQueue;
import java.io.File;
import static java.lang.Boolean.TRUE;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Segment;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.file.api.Ambiguities;
import org.nemesis.antlr.file.api.AmbiguityRecord;
import org.nemesis.antlr.live.language.AlternativesExtractors.AlternativeKey;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.antlr.spi.language.highlighting.AbstractHighlighter;
import org.nemesis.antlr.spi.language.highlighting.HighlightConsumer;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle.Messages;
import static org.nemesis.antlr.live.language.AlternativesExtractors.OUTER_ALTERNATIVES_WITH_SIBLINGS;
import org.nemesis.editor.ops.DocumentOperator;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class AmbiguitiesHighlighter extends AbstractHighlighter implements Runnable {

    private Ambiguities ambiguities;
    private final SimpleAttributeSet base;

    public AmbiguitiesHighlighter(HighlightsLayerFactory.Context ctx) {
        super(ctx);
        base = new SimpleAttributeSet();
        base.addAttribute(StyleConstants.Underline, new Color(129, 148, 236));
//        base.addAttribute(StyleConstants.Background, new Color(255, 190, 0, 90));
        base.addAttribute(StyleConstants.Bold, TRUE);
        base.addAttribute(EditorStyleConstants.LeftBorderLineColor, Color.BLUE);
        base.addAttribute(EditorStyleConstants.RightBorderLineColor, Color.BLUE);
        base.addAttribute(EditorStyleConstants.TopBorderLineColor, Color.BLUE);
        base.addAttribute(EditorStyleConstants.BottomBorderLineColor, Color.BLUE);
    }

    @Override
    protected void activated(FileObject fo, Document doc) {
        if (fo == null) {
            fo = NbEditorUtilities.getFileObject(doc);
        }
        if (fo != null) {
            File file = FileUtil.toFile(fo);
            if (file != null) {
                Path path = file.toPath();
                ambiguities = Ambiguities.forGrammar(path);
                ambiguities.listen(this);
                run();
            }
        }
    }

    @Override
    protected void deactivated(FileObject file, Document doc) {
        ambiguities = null;
    }

    @Override
    public void run() {
        threadPool().submit(this::refresh);
    }

    private void refresh() {
        Ambiguities amb = ambiguities;
        if (amb != null) {
            refreshItems(amb.get());
        }
        EventQueue.invokeLater(() -> {
            ctx.getComponent().invalidate();
            ctx.getComponent().revalidate();
            ctx.getComponent().repaint();
        });
    }

    private static String tooltipMessage(String rule, BitSet bits, Collection<? extends CharSequence> triggers,
            IntMap<String> labelForAlt, IntMap<CharSequence> fragmentTextsForAlternatives,
            AmbiguityRecord rec) {
        StringBuilder bitsList = new StringBuilder(bits.cardinality() * 2);
        for (int bit = bits.nextSetBit(0); bit >= 0; bit = bits.nextSetBit(bit + 1)) {
            if (bitsList.length() != 0) {
                bitsList.append(", ");
            }
            String lbl = labelForAlt.get(bit);
            if (lbl == null || Integer.toString(bit).equals(lbl)) {
                bitsList.append(bit);
            } else {
                bitsList.append(lbl).append('(').append(bit).append(')');
            }
        }
        return Bundle.ambiguityDesc(bitsList, rule,
                formatTriggers(triggers, 400), ruleTextList(fragmentTextsForAlternatives, rec, labelForAlt));
    }

    /**
     * Format the texts that triggered an ambiguity as HTML pre elements with
     * dividers.
     *
     * @param triggers The text elements
     * @param maxChars The maximum length in characters
     * @return Formatted text
     */
    @Messages({
        "# {0} - remaining",
        "andNmore=...and {0} more."
    })
    private static StringBuilder formatTriggers(Collection<? extends CharSequence> triggers, int maxChars) {
        StringBuilder sb = new StringBuilder();
        List<CharSequence> byLength = new ArrayList<>(triggers);
        Collections.sort(byLength, (a, b) -> -Integer.compare(a.length(), b.length()));
        int minLength = Integer.MAX_VALUE;
        int maxLength = Integer.MIN_VALUE;
        for (CharSequence seq : byLength) {
            minLength = Math.min(seq.length(), minLength);
            maxLength = Math.max(seq.length(), maxLength);
        }
        int used = 0;
        int items = 0;
        int maxItems= 3;
        sb.append("<ul>\n");
        Set<CharSequence> seen = new HashSet<>();
        for (CharSequence seq : byLength) {
            if (seq.length() > maxChars && minLength < maxChars || seen.contains(seq)) {
                continue;
            }
            seen.add(seq);
            seq = Strings.trim(seq);
            int remaining = maxChars - used;
            if (seq.length() > 0 && remaining < minLength) {
                break;
            }
            if (seq.length() > remaining && remaining > 4) {
                seq = Strings.elide(seq, (maxChars - used) / 2);
            }
            // do this before escaping
            used += seq.length();
            seq = Escaper.BASIC_HTML.escape(seq);
            sb.append("\n<li><pre style='border-bottom: 1px dashed gray; margin-top: 7px'>");
            sb.append(seq);
            sb.append(seq);
            sb.append("\n</pre></li>");
            if (++items == maxItems) {
                break;
            }
        }
        sb.append("</ul>");
        if (byLength.size() > maxItems) {
            sb.append(Bundle.andNmore(byLength.size()-maxItems));
        }
        return sb;
    }

    @SuppressWarnings("null")
    private static Map<String, IntMap<CharSequence>> ruleTextForAmbiguities(Iterable<? extends AmbiguityRecord> records,
            Extraction ext, SemanticRegions<AlternativeKey> alterns, Document doc) throws BadLocationException {
        Map<String, IntMap<CharSequence>> result = new HashMap<>();
        Map<String, BitSet> alternativesForRule = new HashMap<>();
        Map<String, IntMap<SemanticRegion<AlternativeKey>>> regionsForRule = CollectionUtils.supplierMap(IntMap::create);
        for (AmbiguityRecord ambig : records) {
            assert ambig != null : "Null in ambiguities collection: " + ambig
                    + " of type " + records.getClass().getName();
            if (ambig == null) {
                continue;
            }
            assert ambig.alternatives() != null : "Null alternatives in " + ambig;
            assert ambig.rule() != null : "Null rule name in " + ambig;
            alternativesForRule.computeIfAbsent(ambig.rule(), rl -> new BitSet(ambig.alternatives().cardinality()))
                    .or(ambig.alternatives());
        }
        alterns.collect((AlternativeKey ak) -> {
            if (alternativesForRule.containsKey(ak.rule())) {
                return alternativesForRule.get(ak.rule()).get(ak.alternativeIndex());
            }
            return false;
        }).forEach(rk -> {
            regionsForRule.get(rk.key().rule()).put(rk.key().alternativeIndex(), rk);
        });
        Segment seg = DocumentOperator.render(doc, () -> {
            Segment s = new Segment();
            doc.getText(0, doc.getLength(), s);
            return s;
        });
        for (Map.Entry<String, IntMap<SemanticRegion<AlternativeKey>>> e : regionsForRule.entrySet()) {
            e.getValue().forEachPair((key, val) -> {
                CharSequence seq = seg.subSequence(val.start(), val.end());
                result.computeIfAbsent(e.getKey(), k -> IntMap.create()).put(key, seq);
            });
        }
        return result;
    }

    @Messages("ruleTextNotPresent=<s>could not find alternative text text</s>")
    private static CharSequence ruleTextList(IntMap<CharSequence> fragmentTextsForAlternatives, AmbiguityRecord rec, IntMap<String> labelForAlt) {
        StringBuilder sb = new StringBuilder("<ul>");
        if (fragmentTextsForAlternatives == null) {
            return "";
        }
        BitSetUtils.forEach(rec.alternatives(), alt -> {
            CharSequence seq = fragmentTextsForAlternatives.get(alt);
            sb.append("\n<li>");
            sb.append("<b>").append(labelForAlt.get(alt)).append("</b> &mdash; <code>");
            if (seq != null) {
                sb.append(Escaper.BASIC_HTML.escape(seq));
            } else {
                sb.append(Bundle.ruleTextNotPresent());
            }
            sb.append("</code></li>");
        });
        return sb.append("\n</ul>");
    }

    @Messages({
        "# {0} - listOfAlternatives",
        "# {1} - ruleName",
        "# {2} - parsedText",
        "# {3} - ruleTextList",
        "ambiguityDesc=<html style='margin: 7px'><b>Ambiguity in Rule {1}</b><p>Ambiguous alternatives <b>{0}</b> "
        + "when parsing the text</p>{2}<p>"
        + "<b>Alternatives</b></p>{3}<p>Ambiguities can slow down parsing.</p>"
    })
    private void refreshItems(Collection<? extends AmbiguityRecord> set) {
//        System.out.println("REFRESH AMBIGUITIES for " + ctx.getComponent().isShowing() + " with " + set.size()
//                + " comp " + ctx.getComponent().getName());
        if (!set.isEmpty()) {
            Extraction ext = NbAntlrUtils.extractionFor(ctx.getDocument());
            if (ext != null && !ext.isPlaceholder()) {
                try {
                    NamedSemanticRegions<RuleTypes> ruleBounds = ext.namedRegions(AntlrKeys.RULE_BOUNDS);
                    SemanticRegions<AlternativeKey> alterns = ext.regions(OUTER_ALTERNATIVES_WITH_SIBLINGS);
                    if (!ruleBounds.isEmpty() && !alterns.isEmpty()) {
//                        System.out.println("  some to update");
                        updateHighlights((HighlightConsumer bag) -> {
//                            System.out.println("  enter update highlights");
                            RotatingColors colors = new RotatingColors();
                            boolean any = false;
                            Map<String, IntMap<CharSequence>> ruleFragments;
                            try {
                                ruleFragments = ruleTextForAmbiguities(set, ext, alterns, ctx.getDocument());
                            } catch (BadLocationException ex) {
                                Exceptions.printStackTrace(ex);
                                ruleFragments = new HashMap<>();
                            }
                            int ct = 0;
                            for (AmbiguityRecord rec : set) {
                                NamedSemanticRegion<RuleTypes> rule = ruleBounds.regionFor(rec.rule());
                                if (rule != null) {
                                    BitSet conflictingAlts = rec.alternatives();
                                    List<? extends SemanticRegion<AlternativeKey>> causes
                                            = alterns.collectBetween(rule.start(), rule.end() + 1, (AlternativeKey ak) -> {
                                                return conflictingAlts.get(ak.alternativeIndex());
                                            });
                                    if (!causes.isEmpty()) {
                                        IntMap<String> labelForAlt = IntMap.create(conflictingAlts.cardinality());
                                        causes.forEach(ak -> {
                                            labelForAlt.put(ak.key().alternativeIndex(), ak.key().label());
                                        });
                                        SimpleAttributeSet hint = new SimpleAttributeSet();
                                        IntMap<CharSequence> fragmentTextsForAlternatives = ruleFragments.get(rec.rule());
                                        String msg = tooltipMessage(rec.rule(), conflictingAlts, rec.causeText(),
                                                labelForAlt, fragmentTextsForAlternatives, rec);
                                        hint.addAttribute(EditorStyleConstants.Tooltip, msg);
                                        hint.addAttribute(StyleConstants.Background, colors.get());
                                        AttributeSet attrs = AttributesUtilities.createComposite(base, hint);
                                        for (SemanticRegion<AlternativeKey> reg : causes) {
                                            ct++;
//                                        System.out.println("Highlight " + msg + " " + reg.start() + ":" + reg.end());
                                            bag.addHighlight(reg.start(), reg.end(), attrs);
                                            any = true;
                                        }
                                    }
                                }
                            }
//                            System.out.println("  any updated? " + any + " count " + ct);
                            return any;
                        });
                    }
                } catch (Exception | Error ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        } else {
//            System.out.println("no ambs to highlight");
            updateHighlights(ignored -> false);
        }
    }

    static boolean logged;

    @MimeRegistrations({
        @MimeRegistration(mimeType = ANTLR_MIME_TYPE,
                service = HighlightsLayerFactory.class, position = 60)
    })
    public static HighlightsLayerFactory registration() {
        return factory("ambiguities", ZOrder.SHOW_OFF_RACK.forPosition(10000), AmbiguitiesHighlighter::new, true);
    }

}
