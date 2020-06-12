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

package org.nemesis.antlr.live.parsing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.nemesis.antlr.live.parsing.EmbeddedAntlrParserImpl.charSequencesMatchModuloTrailingNewline;

/**
 *
 * @author Tim Boudreau
 */
public class TestStringHandling {


    @Test
    public void testEqualityWithNewline() {
        assertTrue(charSequencesMatchModuloTrailingNewline("Hello\n", "Hello"));
        assertTrue(charSequencesMatchModuloTrailingNewline("Hello", "Hello"));
        assertTrue(charSequencesMatchModuloTrailingNewline("Hello", "Hello\n"));
        assertTrue(charSequencesMatchModuloTrailingNewline("Hello\n", "Hello\n"));
        assertFalse(charSequencesMatchModuloTrailingNewline("Hellor\n", "Hello\n"));
        assertFalse(charSequencesMatchModuloTrailingNewline("Hellor\n\n", "Hello\n"));
        assertTrue(charSequencesMatchModuloTrailingNewline("Hello\n\n", "Hello\n"));
        assertFalse(charSequencesMatchModuloTrailingNewline("Hello\n\n", "\n"));
        assertFalse(charSequencesMatchModuloTrailingNewline("Hello\n\n", "\n"));

        assertTrue(charSequencesMatchModuloTrailingNewline("Hellor\n", "Hellor"));
        assertTrue(charSequencesMatchModuloTrailingNewline("Hellor", "Hellor"));
        assertTrue(charSequencesMatchModuloTrailingNewline("Hellor", "Hellor\n"));
        assertTrue(charSequencesMatchModuloTrailingNewline("Hellor\n", "Hellor\n"));


        assertFalse(charSequencesMatchModuloTrailingNewline("Hello", ""));
        assertFalse(charSequencesMatchModuloTrailingNewline("x", ""));
        assertFalse(charSequencesMatchModuloTrailingNewline("x\n", ""));
        assertFalse(charSequencesMatchModuloTrailingNewline("x\n", "\n"));

        assertTrue(charSequencesMatchModuloTrailingNewline("\n\n", "\n"));
        assertTrue(charSequencesMatchModuloTrailingNewline("\n", "\n"));
        assertTrue(charSequencesMatchModuloTrailingNewline("", "\n"));
        assertTrue(charSequencesMatchModuloTrailingNewline("", ""));
    }
}
