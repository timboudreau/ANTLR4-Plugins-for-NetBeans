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
package org.nemesis.antlrformatting.grammarfile;

import org.nemesis.antlrformatting.grammarfile.SimpleCollator;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class SimpleCollatorTest {

//    @Test
    public void testCollator() {
        String text = "Hello world, this is a thing.";
        SimpleCollator c = new SimpleCollator(text);
        List<String> all = c.toList();
        assertNotNull(all);
        assertFalse(all.isEmpty());
        assertEquals(Arrays.asList("Hello", "world,", "this", "is", "a", "thing."), all);
        assertEquals(text, c.toString());

        // Test some pathological cases
        text = "I";
        c = new SimpleCollator(text);
        all = c.toList();
        assertEquals(Arrays.asList("I"), all);

        c = new SimpleCollator("   ");
        assertEquals(Collections.emptyList(), c.toList());

        c = new SimpleCollator("\n   \n");
        assertEquals(Collections.emptyList(), c.toList());

        c = new SimpleCollator("\n\n");
        assertEquals(Collections.emptyList(), c.toList());

        c = new SimpleCollator(" ");
        assertEquals(Collections.emptyList(), c.toList());

        c = new SimpleCollator("");
        assertEquals(Collections.emptyList(), c.toList());

        c = new SimpleCollator("\n\nI\n\n");
        assertEquals(Arrays.asList("I"), c.toList());

        // Ensure a single newline is treated as a space, but three or more
        // are treated the same as two
        text = "Hello world,\nI have newlines\n\n\nwhat do you think?";
        c = new SimpleCollator(text);
        all = c.toList();
        assertEquals(Arrays.asList("Hello", "world,", "I", "have", "newlines",
                null, "what", "do", "you", "think?"), all);

        System.out.println("IT IS\n" + c);

        text = "Hello world,\n\nI have newlines\n\n\nwhat do you think?";
        c = new SimpleCollator(text);
        all = c.toList();
        assertEquals(Arrays.asList("Hello", "world,", null, "I", "have", "newlines",
                null, "what", "do", "you", "think?"), all);
        SimpleCollator stringTest = c;

        // Various multiple newline cases, multiple odd and even, leading, trailing
        // and both
        text = "Hello world,\n\nI have newlines\n\n\n\n\n\n\n\n\n\n\nwhat do you think?";
        c = new SimpleCollator(text);
        all = c.toList();
        assertEquals(Arrays.asList("Hello", "world,", null, "I", "have", "newlines",
                null, "what", "do", "you", "think?"), all);
        assertEquals(stringTest.toString(), c.toString());

        text = "  Hello world,\n\nI have newlines\n\n\nwhat do you think?";
        c = new SimpleCollator(text);
        all = c.toList();
        assertEquals(Arrays.asList("Hello", "world,", null, "I", "have", "newlines",
                null, "what", "do", "you", "think?"), all);
        assertEquals(stringTest.toString(), c.toString());

        text = "Hello world,\n\nI have newlines\n\n\n\nwhat do you think? ";
        c = new SimpleCollator(text);
        all = c.toList();
        assertEquals(Arrays.asList("Hello", "world,", null, "I", "have", "newlines",
                null, "what", "do", "you", "think?"), all);
        assertEquals(stringTest.toString(), c.toString());

        text = "Hello world,\n\nI have newlines\n\n\n\nwhat do you think?\n\n\n";
        c = new SimpleCollator(text);
        all = c.toList();
        assertEquals(Arrays.asList("Hello", "world,", null, "I", "have", "newlines",
                null, "what", "do", "you", "think?"), all);
        assertEquals(stringTest.toString(), c.toString());
    }

    @Test
    public void testLeadingNewlines() {
        String text = "\n\n\nHello world,\n\nI have newlines\n\n\n\nwhat do you think?\n\n\n";
        SimpleCollator c = new SimpleCollator(text);
        List<String> all = c.toList();
        assertEquals(Arrays.asList("Hello", "world,", null, "I", "have", "newlines",
                null, "what", "do", "you", "think?"), all);
        assertEquals("Hello world,\n\nI have newlines\n\nwhat do you think?", c.toString());
    }
}
