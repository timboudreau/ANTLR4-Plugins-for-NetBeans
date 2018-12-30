/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.navigator;

import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.BitSetStringGraph;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.Extraction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.NameReferenceSetKey;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.StringGraph.Score;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {
    "ALPHA=Alpha Sort",
    "NATURAL=Natural Sort",
    "ALPHA_TYPE=Grouped Alpha Sort",
    "PAGE_RANK=PageRank",
    "EIGENVECTOR_CENTRALITY=Eigenvector Centrality"
})
enum SortTypes implements Comparator<NamedSemanticRegion<?>> {
    ALPHA, NATURAL, ALPHA_TYPE, PAGE_RANK, EIGENVECTOR_CENTRALITY;

    public String toString() {
        return NbBundle.getMessage(SortTypes.class, name());
    }

    public Action toAction(Consumer<SortTypes> consumer) {
        return new AbstractAction(toString()) {
            @Override
            public void actionPerformed(ActionEvent e) {
                consumer.accept(SortTypes.this);
            }
        };
    }

    public JMenuItem toMenuItem(Supplier<SortTypes> current, Consumer<SortTypes> consumer) {
        JRadioButtonMenuItem result = new JRadioButtonMenuItem(toString());
        result.setSelected(current.get() == this);
        result.addActionListener(toAction(consumer));
        return result;
    }

    private <T extends Enum<T>> void sortBy(List<NamedSemanticRegion<T>> rules, List<Score> scores) {
        Map<String, Double> scoreMap = new HashMap<>();
        for (Score s : scores) {
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

    <T extends Enum<T>> void sort(List<NamedSemanticRegion<T>> rules, Extraction ext, NameReferenceSetKey<T> key) {
        BitSetStringGraph graph = ext.referenceGraph(key);
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
