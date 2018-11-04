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
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.RuleTree.Score;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleDeclaration;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElement;
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
 enum SortTypes implements Comparator<RuleElement> {
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

    private void sortBy(List<RuleDeclaration> rules, List<Score> scores) {
        Map<String, Double> scoreMap = new HashMap<>();
        for (Score s : scores) {
            scoreMap.put(s.node(), s.score());
        }
        Collections.sort(rules, (a, b) -> {
            if (!scoreMap.containsKey(a.getRuleID()) && !scoreMap.containsKey(b.getRuleID())) {
                return a.getRuleID().compareTo(b.getRuleID());
            }
            Double ad = scoreMap.getOrDefault(a.getRuleID(), Double.MIN_VALUE);
            Double bd = scoreMap.getOrDefault(b.getRuleID(), Double.MIN_VALUE);
            return bd.compareTo(ad);
        });
    }

    void sort(List<RuleDeclaration> rules, ANTLRv4SemanticParser sem) {
        switch(this) {
            case PAGE_RANK :
                sortBy(rules, sem.ruleTree().pageRank());
                break;
            case EIGENVECTOR_CENTRALITY :
                sortBy(rules, sem.ruleTree().eigenvectorCentrality());
                break;
            default :
                Collections.sort(rules, this);
        }
    }

    @Override
    public int compare(RuleElement o1, RuleElement o2) {
        switch (this) {
            case ALPHA:
                return o1.getRuleID().compareToIgnoreCase(o2.getRuleID());
            case NATURAL:
                return o1.compareTo(o2);
            case ALPHA_TYPE:
                int result = o1.kind().compareTo(o2.kind());
                if (result == 0) {
                    result = o1.getRuleID().compareToIgnoreCase(o2.getRuleID());
                }
                return result;
            default:
                throw new AssertionError(this);
        }
    }

}
