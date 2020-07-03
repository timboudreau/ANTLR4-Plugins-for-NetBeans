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
        SemanticRegions<Integer> items = nmExtraction.regions(ChannelsAndSkipExtractors.SUPERFLUOUS_PARENTEHSES);
//        System.out.println("ITEMS: " + items.size());
        List<String> toElide = new ArrayList<>(items.size());
        for (SemanticRegion<Integer> reg : items) {
//            System.out.println(reg.key() + " '" + Escaper.CONTROL_CHARACTERS.escape(text.substring(reg.start(), reg.end())) + "' " + reg.start() + ":" + reg.end());
            String txt = text.substring(reg.start(), reg.end());
            toElide.add(txt);
        }
        /*
1685125976 '( map ( Comma map )* )' 50:72
760413008 '( Colon value )' 164:179
-466325160 '( Minus? Digits )' 369:386
101309344 '( STRING | STRING2 )' 416:436
1514614016 '( '{' )' 493:500
347418080 '( 'a'..'z' | 'A'..'Z' | '_' )' 826:855        
         */
        List<String> expect = Arrays.asList(
                "( map ( Comma map )* )",
                "( Colon value )",
                "( Minus? Digits )",
                "( STRING | STRING2 )",
                "( '{' )",
                "( 'a'..'z' | 'A'..'Z' | '_' )"
        );
        assertEquals(expect, toElide);
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
