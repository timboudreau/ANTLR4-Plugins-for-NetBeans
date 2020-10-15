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
package org.nemesis.antlr.live.language.ambig;

import com.mastfrog.graph.ObjectGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class GridTest {

    @Test
    public void testSomeMethod() {
        Random rnd = new Random(10732931029L);
        pathsAndItems(paths(), (all, paths) -> {
            int max = 0;
            for (List<PT> l : paths) {
                max = Math.max(max, l.size());
            }
            int[][] cells = new int[paths.size()][max * 2];
            ObjectGraph<PT> gph = PT.graph(paths);
            IntFunction<String> stringifier = pt2s(gph);
            int mm = max;
            gph.toIntGraph((ir, g) -> {
                for (int row = 0; row < paths.size(); row++) {
                    int[] thisRow = cells[row];
                    Arrays.fill(thisRow, -1);
                    List<PT> curr = paths.get(row);
                    for (int col = 0; col < curr.size(); col++) {
                        thisRow[col] = gph.toNodeId(curr.get(col));
                    }
                }
                Grid grid = new Grid(mm * 2, cells);
                System.out.println("GRID \n" + grid.toString(stringifier));

//                for (int i = 0; i < 3; i++) {

                    grid.shuffle(rnd);
                    System.out.println("SORTED \n" + grid.toString(stringifier));

                    int[][] mx = grid.matchMatrix();

                    System.out.println("MATRIX:\n" + Grid.toString(mx, grid.rowSize(), Integer::toString));

                    grid.applyMatrix(mx);

                    System.out.println("AFTER: \n" + grid.toString(stringifier));


                    grid.align();

//                    while (grid.align()) {
                        System.out.println("ALIGNED: \n" + grid.toString(stringifier));
//                        break;
//                    }

                    grid.coalesceAdjacent();
                    System.out.println("CA:\n" + grid.toString(stringifier));

                    grid.makeOrphansAdjacaent();
                    System.out.println("OAA-1\n" + grid.toString(stringifier));
//
                    grid.makeOrphansAdjacaent();
                    System.out.println("OAA-2\n" + grid.toString(stringifier));
//
//                    grid.makeOrphansAdjacaent();
//                    System.out.println("OAA-3\n" + grid.toString(stringifier));
//
//                    grid.makeOrphansAdjacaent();
//                    System.out.println("OAA-4\n" + grid.toString(stringifier));
//
//                    grid.makeOrphansAdjacaent();
//                    System.out.println("OAA-5\n" + grid.toString(stringifier));




//                }

//                grid.tune();
            });
        });
    }

    static IntFunction<String> pt2s(ObjectGraph<PT> gph) {
        return iv -> {
            if (iv == -1) {
                return "-";
            }
            return gph.toNode(iv).toString();
        };
    }

    private static void pathsAndItems(List<List<PT>> paths, BiConsumer<Set<PT>, List<List<PT>>> c) {
        Set<PT> all = new HashSet<>();
        for (List<PT> l : paths) {
            all.addAll(l);
        }
        c.accept(all, paths);
    }

    private static List<List<PT>> paths() {
        String[] chs = new String[]{
            "ABCDEFG",
            "RABCDEFG",
            "RQACDG",
            "AFG",
            "ABFG",
            "AXYZEFG"
        };
        int max = 0;
        for (int i = 0; i < chs.length; i++) {
            max = Math.max(max, chs[i].length());
        }
        StringBuilder sb = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        List<List<PT>> paths = new ArrayList<>();
        for (int i = 0; i < chs.length; i++) {
            List<PT> curr = new ArrayList<>();
            paths.add(curr);
            char[] ch = chs[i].toCharArray();
            sb2.append("paths.add(new ArrayList<>(Arrays.asList(");
            for (int j = 0; j < max; j++) {
                char c;
                if (j < ch.length) {
                    c = ch[j];
                    sb2.append("PT.of(\"").append(c).append("\")");
                    curr.add(PT.of("" + c));
                    if (j != ch.length - 1) {
                        sb2.append(", ");
                    }
                } else {
                    c = '-';
                }
                if (j == 0) {
                    sb.append("| ");
                } else {
                    sb.append(" | ");
                }
                sb.append(c);
                if (j == max - 1) {
                    sb.append('|');
                }
            }
            sb.append('\n');
            sb2.append("));\n");
        }
        System.out.println(sb);
        System.out.println("\n");
        System.out.println(sb2);
        return paths;
    }

}
