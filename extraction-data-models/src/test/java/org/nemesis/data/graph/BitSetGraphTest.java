package org.nemesis.data.graph;

import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class BitSetGraphTest {

    static int[][] EDGES_1 = new int[][]{
        {0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5},
        {1, 5},
        {2, 20}, {20, 5},
        {10, 11}, {11, 12}, {12, 13},
        {3, 6}, {6, 7}, {7, 8},
        {4, 30}, {10, 31}, {31, 30}
    };

    static int[][] EDGES_WITH_CYCLES = new int[][]{
        {0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5},
        {1, 5},
        {2, 20}, {20, 5},
        {10, 11}, {11, 12}, {12, 13}, {13, 10},
        {3, 6}, {6, 7}, {7, 8}, {8, 6},
        {4, 30}, {10, 31}, {31, 30}
    };

    PairSet pathoEdges;
    PairSet graphEdges = PairSet.fromIntArray(EDGES_1);
    PairSet cyclesEdges = PairSet.fromIntArray(EDGES_WITH_CYCLES);

    BitSetGraph graph;
    BitSetGraph cyclesGraph;
    BitSetGraph pathologicalGraph;

    @Test
    public void sanityCheckGraph() {
        PairSet ps = new PairSet(graph.size());
        for (int[] edge : EDGES_1) {
            assertTrue("Edge not present: " + edge[0] + "->" + edge[1], graph.containsEdge(edge[0], edge[1]));
            ps.add(edge[0], edge[1]);
        }
    }

    @Test
    public void testPaths() {
        List<IntPath> allPaths = graph.pathsBetween(0, 5);
        List<IntPath> expected = Arrays.asList(
                new IntPath().addAll(0, 5),
                new IntPath().addAll(0, 1, 5),
                new IntPath().addAll(0, 2, 20, 5)
        );
        assertEquals(expected, allPaths);
        Optional<IntPath> opt = graph.shortestPathBetween(0, 5);
        assertTrue(opt.isPresent());
        assertEquals(opt.get(), new IntPath().addAll(0, 5));
        opt = graph.shortestPathBetween(0, 14);
        assertFalse(opt.isPresent());

        for (int i = 0; i < EDGES_1.length; i++) {
            for (int j = 0; j < EDGES_1.length; j++) {
                int dist = graph.distance(i, j);
                Optional<IntPath> pth = graph.shortestPathBetween(i, j);
                if (!pth.isPresent()) {
                    pth = graph.shortestPathBetween(j, i);
                }
                if (dist == -1) {
                    assertFalse(pth.isPresent());
                } else {
                    assertTrue(pth.isPresent());
                    assertEquals(dist, pth.get().size());
                }
            }
        }
    }

    @Test
    public void testClosure() {
        for (int i = 0; i < EDGES_1.length; i++) {
            BitSet closure = computeClosureSlow(i, EDGES_1);
            if (closure.cardinality() > 0) {
                BitSet graphClosure = graph.closureOf(i);
                assertEquals("Closures for " + i + " differ", closure, graphClosure);
            }
        }
    }

    @Test
    public void testReverseClosure() {
        for (int i = 0; i < EDGES_1.length; i++) {
            BitSet closure = computeReverseClosureSlow(i, EDGES_1);
            if (closure.cardinality() > 0) {
                BitSet graphClosure = graph.reverseClosureOf(i);
                assertEquals("Closures for " + i + " differ", closure, graphClosure);
            }
        }
    }

    @Test
    public void testWalkTouchesAllEdges() {
        // Graph walking only visits each node once, so if a node is incorporated
        // into several paths, there will be unvisited pairs
        PairSet edges = graphEdges.copy();
        edges.remove(0, 5).remove(14, 31);
        assertAllEdgesWalked(graphEdges, graph, new PairSet(graphEdges.size()).add(0, 5).add(14, 31));

        edges = cyclesEdges.copy();
        assertAllEdgesWalked(edges, cyclesGraph, new PairSet(cyclesEdges.size())
                .add(0, 5).add(1, 6).add(8, 6).add(10, 11).add(12, 31)
        );
    }

    @Test
    public void testPairSetConversion() {
        PairSet set = graph.toPairSet();
        BitSetGraph revived = set.toGraph();
        assertEquals(graph, revived);
    }

    private void assertAllEdgesWalked(PairSet expectedPairs, BitSetGraph graph, PairSet expectedUnvisited) {
        PairSet testPairs = expectedPairs.copy();
        PairSet foundPairs = new PairSet(graph.size());
        graph.walk(new BitSetGraphVisitor() {
            LinkedList<Integer> last = new LinkedList<>();

            {
                last.push(-1);
            }

            @Override
            public void enterRule(int edge, int depth) {
                int parent = last.peek();
                if (parent != -1) {
                    foundPairs.add(parent, edge);
                    testPairs.remove(parent, edge);
                }
                last.push(edge);
            }

            @Override
            public void exitRule(int ruleId, int depth) {
                last.pop();
            }
        });
        assertEquals("Not all pairs visited" + testPairs, expectedUnvisited, testPairs);
    }

    private BitSet computeClosureSlow(int of, int[][] edges) {
        BitSet set = new BitSet(edges.length);
        for (int[] pair : edges) {
            if (pair[0] == of) {
                set.set(pair[1]);
            }
        }
        int cardinality;
        do {
            cardinality = set.cardinality();
            for (int[] pair : edges) {
                if (set.get(pair[0])) {
                    set.set(pair[1]);
                }
            }
        } while (cardinality != set.cardinality());
        return set;
    }

    private BitSet computeReverseClosureSlow(int of, int[][] edges) {
        BitSet set = new BitSet(edges.length);
        for (int[] pair : edges) {
            if (pair[1] == of) {
                set.set(pair[0]);
            }
        }
        int cardinality;
        do {
            cardinality = set.cardinality();
            for (int[] pair : edges) {
                if (set.get(pair[1])) {
                    set.set(pair[0]);
                }
            }
        } while (cardinality != set.cardinality());
        return set;
    }

    @Test
    public void testInvert() {
        BitSet set = new BitSet();
        for (int i = 0; i < 100; i++) {
            if (i % 2 == 1) {
                set.set(i);
            }
        }
        BitSet inverted = BitSetUtils.invert(set);
        for (int i = 0; i < 100; i++) {
            if (i % 2 == 1) {
                assertFalse("Should not be set: " + i, inverted.get(i));
            } else {
                assertTrue("Should be set: " + i, inverted.get(i));
            }
        }
        for (int i = 100; i < 140; i++) {
            assertFalse("Bits after end should not be set but " + i + " is, in " + inverted, inverted.get(i));
        }

    }

    @Before
    public void buildGraph() {
        graph = BitSetGraph.builder(5).addEdges(EDGES_1).build();
        cyclesGraph = BitSetGraph.builder(5).addEdges(EDGES_WITH_CYCLES).build();
        BitSetGraph.Builder bldr = BitSetGraph.builder();
        pathoEdges = new PairSet(20);
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 20; j++) {
                bldr.addEdge(i, j);
                pathoEdges.add(i, j);
            }
        }
        pathologicalGraph = bldr.build();
    }

}
