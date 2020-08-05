/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.error.highlighting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.extraction.Extraction;

/**
 *
 * @author Tim Boudreau
 */
public class SuperfluousParenthesesDetectionTest {

    @Test
    public void testExpectedItemsAreDetected() throws IOException {
        Extraction nmExtraction = SFUtil.nmExtraction();
        String text = SFUtil.nestedMaps.text();
//        System.out.println(text);
        SemanticRegions<Integer> items = nmExtraction.regions(HintsAndErrorsExtractors.SUPERFLUOUS_PARENTEHSES);
//        System.out.println("ITEMS: " + items.size());
        List<String> toElide = new ArrayList<>(items.size());
        for (SemanticRegion<Integer> reg : items) {
//            System.out.println(reg.key() + " '" + Escaper.CONTROL_CHARACTERS.escape(text.substring(reg.start(), reg.end())) + "' " + reg.start() + ":" + reg.end());
            String txt = text.substring(reg.start(), reg.end());
            toElide.add(txt);
        }
        List<String> expect = Arrays.asList(
                "( map ( Comma map )* )",
                "( Colon value )",
                "(String)",
                "( Minus? Digits )",
                "( STRING | STRING2 )",
                "( '{' )",
                "( 'a'..'z' | 'A'..'Z' | '_' )"
        );
        assertLists(expect, toElide);
    }

    private static void assertLists(List<String> a, List<String> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            String aa = a.get(i);
            String bb = b.get(i);
            assertText(i + ".", aa, bb);
        }
        assertEquals(a, b);
    }

    private static void assertText(String msg, String a, String b) {
        if (!a.equals(b)) {
            for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
                char aa = a.charAt(i);
                char bb = b.charAt(i);
                if (a != b) {
                    fail(msg + ": Mismatch at char " + i + " '" + aa + "' vs '" + bb + "' in\n" + a + "'\n'" + b + "'");
                }
            }

            fail(msg + ": Strings do not match:\n'" + a + "'\n'" + b + "'");
        }
    }

    private void out(ParserRuleContext ctx) {
        out(ctx, 0);
    }

    private static void out(ParseTree ctx, int depth) {
        char[] ch = new char[depth * 2];
        Arrays.fill(ch, ' ');
        System.out.println(new String(ch) + ctx.getClass().getSimpleName()
                + " '" + ctx.getText() + "'");
        for (int i = 0; i < ctx.getChildCount(); i++) {
            out(ctx.getChild(i), depth + 1);
        }
    }
}
