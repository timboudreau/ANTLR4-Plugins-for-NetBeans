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
package com.mastfrog.antlr.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class HeuristicRuleNameComparatorTest {

    @Test
    public void testSuffix() throws Exception {
        assertEquals("Comment", HeuristicRuleNameComparator.suffix("BlockComment"));
        assertEquals("Comment", HeuristicRuleNameComparator.suffix("InlinedDocComment"));
        assertEquals("Comment", HeuristicRuleNameComparator.suffix("Comment"));
        assertEquals("COMMENT", HeuristicRuleNameComparator.suffix("BLOCK_COMMENT"));
        assertNull(HeuristicRuleNameComparator.suffix("COMMENT"));
        assertEquals("COMMENT", HeuristicRuleNameComparator.suffix("INLINE_BLOCK_COMMENT"));
    }

    @Test
    public void testSharedHead() {
        assertEquals("ListItem", HeuristicRuleNameComparator.sharedHead("ListItemHead", "ListItemTail"));
        assertEquals("ListItem", HeuristicRuleNameComparator.sharedHead("ListItem", "ListItemTail"));
        assertNull(HeuristicRuleNameComparator.sharedHead("Listitemizer", "ListItemHead"));
        assertEquals("LIST_ITEM_", HeuristicRuleNameComparator.sharedHead("LIST_ITEM_HEAD", "LIST_ITEM_TAIL"));
        assertEquals("LIST_ITEM", HeuristicRuleNameComparator.sharedHead("LIST_ITEM", "LIST_ITEM_TAIL"));
    }

    @Test
    public void testSortWithSuffix() throws Exception {
        List<String> bicapitalizedRules = new ArrayList<>(Arrays.asList(
                "Comment",
                "BlockComment", "OuterDocComment",
                "InlineBlockComment", "Function", "Block", "Art", "Wookie",
                "ListItem", "ListItemHead", "ListItemTail", "FunctionHead", "ListItemizer"
        ));
        Collections.sort(bicapitalizedRules, HeuristicRuleNameComparator.INSTANCE);
        assertEquals(Arrays.asList("Art", "Block", "Comment", "BlockComment", "InlineBlockComment",
                "OuterDocComment", "Function", "FunctionHead", "ListItem", "ListItemHead",
                "ListItemizer", "ListItemTail", "Wookie"), bicapitalizedRules);

        List<String> capUnderscoreDelimitedRules = new ArrayList<>(Arrays.asList(
                "COMMENT",
                "BLOCK_COMMENT", "OUTER_DOC_COMMENT",
                "INLINE_BLOCK_COMMENT", "FUNCTION", "BLOCK", "ART", "WOOKIE",
                "LIST_ITEM", "LIST_ITEM_HEAD", "LIST_ITEM_TAIL", "FUNCTION_HEAD", "LIST_ITEMIZER",
                "WOOKIE_FOOD", "BIG_WOOKIE"
        ));

        Collections.sort(capUnderscoreDelimitedRules, HeuristicRuleNameComparator.INSTANCE);
        assertEquals(Arrays.asList("ART", "BLOCK", "COMMENT", "BLOCK_COMMENT", "INLINE_BLOCK_COMMENT",
                "OUTER_DOC_COMMENT", "WOOKIE_FOOD", "FUNCTION", "FUNCTION_HEAD", "LIST_ITEM",
                "LIST_ITEMIZER", "LIST_ITEM_HEAD", "LIST_ITEM_TAIL", "WOOKIE", "BIG_WOOKIE"),
                capUnderscoreDelimitedRules);
    }

}
