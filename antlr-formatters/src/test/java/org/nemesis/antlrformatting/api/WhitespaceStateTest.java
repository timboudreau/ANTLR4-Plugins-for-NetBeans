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
package org.nemesis.antlrformatting.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class WhitespaceStateTest {

    @Test
    public void testIndenting() {
        WhitespaceState state = new WhitespaceState(4, new WhitespaceStringCache());
        assertTrue(state.isEmpty());
        assertEquals("", state.preview());
        assertEquals(0, state.getLineOffset(0));
        assertEquals(23, state.getLineOffset(23));
        state.doubleIndent();
        assertFalse(state.isEmpty());
        assertEquals("        ", state.preview());
        assertEquals(8, state.getLineOffset(0));
        assertEquals(30, state.getLineOffset(22));
        state.newline();
        assertEquals("\n        ", state.preview());
        assertEquals(8, state.getLineOffset(0));
        assertEquals(8, state.getLineOffset(22));
        state.doubleNewline();
        assertEquals("\n\n        ", state.preview());
        assertEquals(8, state.getLineOffset(0));
        assertEquals(8, state.getLineOffset(22));
        assertEquals(8, state.getLineOffset(4));

        int[] lod = new int[] { 4 };
        StringBuilder sb = new StringBuilder("whaa");
        state.apply(sb, lod);
        assertEquals(8, lod[0], "'" + sb.toString().replace("\n", "\\n") + "'");
        assertEquals("whaa\n\n        ", sb.toString());
    }

    @Test
    public void testNewlines() {
        WhitespaceState state = new WhitespaceState(4, new WhitespaceStringCache());
        state.newline();
        assertEquals(0, state.getLineOffset(0));
        assertEquals(0, state.getLineOffset(32));
        assertEquals("\n", state.preview());

        int[] lod = new int[] { 4 };
        StringBuilder sb = new StringBuilder("whaa");
        state.apply(sb, lod);
        assertEquals(0, lod[0]);
        assertEquals("whaa\n", sb.toString());

    }

}
