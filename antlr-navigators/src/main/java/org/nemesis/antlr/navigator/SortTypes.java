package org.nemesis.antlr.navigator;

import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import org.nemesis.graph.StringGraph;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.graph.algorithm.Score;
import org.openide.awt.Mnemonics;
import org.openide.util.NbBundle;

/**
 * Built in sorting algorithms for regions extracted from a parse.  If
 * you don't want to use these, supply your own popupMenuPopulator and create
 * your own methods.
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {
    "ALPHA=Alpha &Sort",
    "NATURAL=&Natural Sort",
    "ALPHA_TYPE=&Grouped Alpha Sort",
    "PAGE_RANK=&Page Rank",
    "EIGENVECTOR_CENTRALITY=&Eigenvector Centrality",
    "ALPHA_description=Sort by name",
    "NATURAL_description=Sort in order of appearance",
    "ALPHA_TYPE_description=Sort alphabetically, grouping by parser/lexer/fragment rule types",
    "PAGE_RANK_description=Sort most used rules to the top",
    "EIGENVECTOR_CENTRALITY_description=Sort most-connected-through rules to the top"
})
public enum SortTypes implements Comparator<NamedSemanticRegion<?>> {
    /**
     * Sort by rule name.
     */
    ALPHA,
    /**
     * Sort by order of occurence.
     */
    NATURAL,
    /**
     * Group by type (parser/lexer/fragment rule) and then sort those groups
     * alphabetically.
     */
    ALPHA_TYPE,
    /**
     * <i>Requires a NamedReferenceSet key</i> - using a reference graph from
     * the extraction (for example a graph of the references to other rules from
     * each rule), apply the page rank algorithm to sort most-important - most
     * used - rules to the top.
     */
    PAGE_RANK,
    /**
     * <i>Requires a NamedReferenceSet key</i> - using a reference graph from
     * the extraction (for example a graph of the references to other rules from
     * each rule), apply the eigenvector centrality algorithm to sort
     * most-likely-to-be-connected-through rules to the top - those which are
     * not necessarily the most used, but the most commonly on the path from the
     * top of the graph and the bottom.
     */
    EIGENVECTOR_CENTRALITY;

    @Override
    public String toString() {
        return NbBundle.getMessage(SortTypes.class, name());
    }

    public String description() {
        return NbBundle.getMessage(SortTypes.class, name() + "_description");
    }

    public boolean isCentralitySort() {
        return PAGE_RANK == this || EIGENVECTOR_CENTRALITY == this;
    }

    Action toAction(Consumer<SortTypes> consumer) {
        return new AbstractAction(toString()) {
            @Override
            public void actionPerformed(ActionEvent e) {
                consumer.accept(SortTypes.this);
            }
        };
    }

    JMenuItem toMenuItem(Supplier<SortTypes> current, Consumer<SortTypes> consumer) {
        JRadioButtonMenuItem result = new JRadioButtonMenuItem();
        Mnemonics.setLocalizedText(result, toString());
        result.setSelected(current.get() == this);
        result.addActionListener(toAction(consumer));
        result.setToolTipText(description());
        return result;
    }

    private <T extends Enum<T>> void sortBy(List<NamedSemanticRegion<T>> rules, List<Score<String>> scores) {
        Map<String, Double> scoreMap = new HashMap<>();
        for (Score<String> s : scores) {
            scoreMap.put(s.node(), s.score());
        }
        Collections.sort(rules, (a, b) -> {
            if (!scoreMap.containsKey(a.name()) && !scoreMap.containsKey(b.name())) {
                return a.name().compareTo(b.name());
            }
            Double ad = scoreMap.getOrDefault(a.name(), Double.MIN_VALUE);
            Double bd = scoreMap.getOrDefault(b.name(), Double.MIN_VALUE);
            return bd.compareTo(ad);
        });
    }

    public <T extends Enum<T>> void sort(List<NamedSemanticRegion<T>> rules, Extraction ext, NameReferenceSetKey<T> key) {
        boolean centrality = isCentralitySort();
        StringGraph graph = centrality ? ext.referenceGraph(key) : null;
        if (graph == null && centrality) {
            IllegalStateException ex = new IllegalStateException("Extraction contains no graph for " + key);
            Logger.getLogger(SortTypes.class.getName()).log(Level.WARNING, null, ex);
            Collections.sort(rules, NATURAL);
            return;
        }
        switch (this) {
            case PAGE_RANK:
                sortBy(rules, graph.pageRank());
                break;
            case EIGENVECTOR_CENTRALITY:
                sortBy(rules, graph.eigenvectorCentrality());
                break;
            default:
                Collections.sort(rules, this);
        }
    }

    private int compareOrdinals(Enum<?> a, Enum<?> b) {
        int ao = a.ordinal();
        int bo = b.ordinal();
        return ao > bo ? 1 : ao == bo ? 0 : -1;
    }

    @Override
    public int compare(NamedSemanticRegion<?> o1, NamedSemanticRegion<?> o2) {
        switch (this) {
            case ALPHA:
                return o1.name().compareToIgnoreCase(o2.name());
            case NATURAL:
                return o1.compareTo(o2);
            case ALPHA_TYPE:
                int result = compareOrdinals(o1.kind(), o2.kind());
                if (result == 0) {
                    result = o1.name().compareToIgnoreCase(o2.name());
                }
                return result;
            default:
                throw new AssertionError(this);
        }
    }
}
