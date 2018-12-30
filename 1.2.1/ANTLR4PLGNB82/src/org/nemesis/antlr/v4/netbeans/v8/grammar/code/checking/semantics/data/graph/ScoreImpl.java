package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.graph;

/**
 *
 * @author Tim Boudreau
 */
final class ScoreImpl implements StringGraph.Score {

    private final double score;
    private final int ruleIndex;
    private final String node;

    ScoreImpl(double score, int ruleIndex, String node) {
        this.score = score;
        this.ruleIndex = ruleIndex;
        this.node = node;
    }

    @Override
    public String node() {
        return node;
    }

    @Override
    public double score() {
        return score;
    }

    @Override
    public int ruleIndex() {
        return ruleIndex;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StringGraph.Score && ((StringGraph.Score) o).ruleIndex() == ruleIndex();
    }

    @Override
    public int hashCode() {
        return 7 * ruleIndex;
    }

    @Override
    public String toString() {
        return node + ":" + score;
    }

}
