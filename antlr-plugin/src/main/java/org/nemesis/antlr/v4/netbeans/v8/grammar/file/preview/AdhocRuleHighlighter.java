package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.highlighting.AbstractAntlrHighlighter;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.AdhocMimeTypes.loggableMimeType;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeElementKind;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyToken;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxyTokenType;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocRuleHighlighter extends AbstractAntlrHighlighter.DocumentOriented<Void, AdhocParserResult, ParseTreeProxy> implements ChangeListener {

    private final String mimeType;
    private final AdhocColorings colorings;

    public AdhocRuleHighlighter(Document doc, String mimeType) {
        super(doc, AdhocParserResult.class, res -> {
            return res.parseTree();
        });
        this.mimeType = mimeType;
        colorings = AdhocColoringsRegistry.getDefault().get(mimeType);
        colorings.addChangeListener(WeakListeners.change(this, colorings));
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        super.scheduleRefresh();
    }

    @Override
    public String toString() {
        return AdhocRuleHighlighter.class.getSimpleName() + " for " + loggableMimeType(mimeType);
    }

    @Override
    protected void refresh(Document doc, Void argument, ParseTreeProxy semantics, AdhocParserResult result) {
        LOG.log(Level.FINER, "Refresh rule highlights for {0} with {1} tree elements"
                + " and {2} tokens",
                new Object[]{loggableMimeType(mimeType), semantics.allTreeElements().size(),
                    semantics.tokens().size()});
        bag.clear();
        int ruleCount = 0;
        int allRuleCount = 0;
        int length = doc.getLength();
        HS hs = seq(semantics.tokenCount() + semantics.allTreeElements().size());
        for (ProxyToken tok : semantics.tokens()) {
            int tp = tok.getType();
            ProxyTokenType type = semantics.tokenTypeForInt(tp);
            AdhocColoring a = colorings.get(AdhocTokenId.categorize(type));
            // We can be highlighting a stale text
            if (tok.getStartIndex() > length || tok.getStopIndex() > length) {
                break;
            }
            if (a != null && a.isActive()) {
                int start = tok.getStartIndex();
                int end = tok.getStopIndex() + 1;
                if (end >= length) {
                    end = length - 1;
                }
                if (end <= start) {
                    continue;
                }
                hs.add(start, end, a);
//                bag.addHighlight(start, end, a);
            }
        }
        for (AntlrProxies.ParseTreeElement el : semantics.allTreeElements()) {
            if (el.kind() == ParseTreeElementKind.RULE) {
                AntlrProxies.RuleNodeTreeElement rn = (AntlrProxies.RuleNodeTreeElement) el;
                AdhocColoring a = colorings.get(rn.name());
                allRuleCount++;
                if (a != null && a.isActive()) {
                    ruleCount++;
                    int start = rn.startTokenIndex();
                    int end = rn.stopTokenIndex();
                    if (start < 0 || end < 0) {
                        start = 0;
                        end = Math.min(20, semantics.text().length());
                    } else {
                        ProxyToken startToken = semantics.tokens().get(start);
                        ProxyToken endToken = semantics.tokens().get(end);
                        start = startToken.getStartIndex();
                        end = Math.min(length - 1, endToken.getStopIndex() + 1);
                    }
                    if (start > length || end > length) {
                        continue;
                    }
                    if (end <= start) {
                        continue;
                    }
                    hs.add(start, end, a);
                }
            }
        }
        // Using highlights sequence avoids the bag firing a change and a repaint
        // for every item
        if (!hs.isEmpty()) {
            bag.addAllHighlights(hs);
        }
        LOG.log(Level.FINER, "rules highlighted: {0} of {1}", new Object[]{ruleCount, allRuleCount});
    }

    private final ThreadLocal<HS> hsLocal = new ThreadLocal<>();

    private HS seq(int defaultSize) {
        HS result = hsLocal.get();
        if (result == null) {
            result = new HS(defaultSize);
            hsLocal.set(result);
        }
        result.reset();
        return result;
    }

    static List<HL> split(List<HL> hls) {
        // Ugly but necessary.  This splits overlapping sets of highlights into
        // separate elements e.g. if you have the equivalent of
        // <b>bold text <i>bold italic text</i> more bold text</b>
        // that would arrive as a wide section with the bold attribute and
        // a narrow section with the italic attribute whose bounds are inside
        // the other.  What we actually *need* is three separate wads of
        // attributes, comprising a bold section, a bold italic section, and
        // another bold section.  This does that.

        // First, sort so we have wider then narrower ordered by start
        Collections.sort(hls);
        Set<Integer> boundaries = new TreeSet<>();
        // Now make a list with no duplicates of every start or end
        // position
        for (HL hl : hls) {
            boundaries.add(hl.start);
            boundaries.add(hl.end);
        }
        List<HL> nue = new ArrayList<>();
        // better to unbox once than many times - 9x slower than primitive access
        // and we may be in the middle of the paint loop
        int[] bds = (int[]) Utilities.toPrimitiveArray(boundaries.toArray(new Integer[boundaries.size()]));
        int scanStart = 0;
        // Iterate the boundaries and find items that are active over
        // them
        for (int ix = 0; ix < bds.length - 1; ix++) {
            int i = bds[ix];
            AttributeSet set = null;
            for (int j = scanStart; j < hls.size(); j++) {
                HL hl = hls.get(j);
                // If it's apropos, coalesce the attributes
                // AdhocColoring only supports a foreground or background color,
                // so when necessary, use SimpleAttributeSet
                if (hl.contains(i)) {
                    if (set == null) {
                        set = hl.attrs;
                    } else if (set instanceof SimpleAttributeSet) {
                        ((SimpleAttributeSet) set).addAttributes(hl.attrs);
                    } else {
                        AdhocColoring maybeReplacement = null;
                        // AdhocColoring is much cheaper to create and use, so prefer
                        // it where possible
//                        if (set instanceof AdhocColoring && hl.attrs instanceof AdhocColoring) {
//                            maybeReplacement = ((AdhocColoring) hl.attrs).combine((AdhocColoring) set);
//                            if (maybeReplacement != null) {
//                                set = maybeReplacement;
//                            }
//                        }
//                        if (maybeReplacement == null) {
                            SimpleAttributeSet replacement = new SimpleAttributeSet(set);
                            replacement.addAttributes(hl.attrs);
                            set = replacement;
//                        }
                    }
                } else if (hl.endsBefore(i)) {
                    scanStart = Math.max(0, j - 1);
                }
            }
            if (set != null) {
                nue.add(new HL(i, bds[ix + 1], set));
            }
        }
        return nue;
    }

    static final class HS implements HighlightsSequence {

        private List<HL> all;
        private int cursor = -1;

        HS(int defaultSize) {
            all = new ArrayList<>(defaultSize);
        }

        @Override
        public boolean moveNext() {
            cursor++;
            if (cursor == 0) {
                all = split(all);
            }
            boolean result = cursor < all.size();
            return result;
        }

        @Override
        public int getStartOffset() {
            return all.get(cursor).start;
        }

        @Override
        public int getEndOffset() {
            return all.get(cursor).end;
        }

        @Override
        public AttributeSet getAttributes() {
            return all.get(cursor).attrs;
        }

        void reset() {
            all.clear();
            cursor = -1;
        }

        boolean isEmpty() {
            return all.isEmpty();
        }

        void add(int start, int end, AdhocColoring attrs) {
            if (end <= start || end < 0 || start < 0) {
                return;
            }
            if (!all.isEmpty()) {
                HL last = all.get(all.size() - 1);
                if (start == last.start && end == last.end) {
                    all.set(all.size() - 1, new HL(start, end, attrs));
                    return;
                }
            }
            all.add(new HL(start, end, attrs));
        }
    }

    static final class HL implements Comparable<HL> {

        final int start;
        final int end;
        final AttributeSet attrs;

        HL(int start, int end, AttributeSet attrs) {
            this.start = start;
            this.end = end;
            this.attrs = attrs;
        }

        public String toString() {
            return (start + ":" + end + " - " + attrs).trim();
        }

        boolean contains(int pos) {
            return pos >= start && pos < end;
        }

        @Override
        public int compareTo(HL o) {
            if (o.start == start && o.end == end) {
                return 0;
            }
            if (o.start > start && o.end > end) {
                return -1;
            }
            if (o.start < end && o.end < end) {
                return 1;
            }
            if (o.end == end) {
                if (start > o.start) {
                    return 1;
                } else {
                    return -1;
                }
            }
            if (o.start == start) {
                if (end < o.end) {
                    return 1;
                } else {
                    return -1;
                }
            }
            return 0;
        }

        void apply(OffsetsBag bag) {
            bag.addHighlight(start, end, attrs);
        }

        private boolean endsBefore(int j) {
            return end < j;
        }
    }
}
