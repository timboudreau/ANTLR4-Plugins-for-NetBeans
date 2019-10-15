/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.source.spi;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class RelativeResolverRegistryTest {

    private static final String FAKE_MIME = "text/x-wuzzle";
    FakeRegistry reg = new FakeRegistry();

    @Test
    public void testAdaptersAreUsed() {
        RelativeResolverImplementation<Integer> res = reg.forDocumentAndMimeType(0, FAKE_MIME);
        assertNotNull(res);
        Optional<Integer> neighbor = res.resolve(1, "2a");
        assertNotNull(neighbor);
        assertTrue(neighbor.isPresent());
        assertEquals(Integer.valueOf(2), neighbor.get());

        neighbor = res.resolve(2, "3a");
        assertNotNull(neighbor);
        assertTrue(neighbor.isPresent());
        assertEquals(Integer.valueOf(3), neighbor.get());

        neighbor = res.resolve(3, "4a");
        assertNotNull(neighbor);
        assertTrue(neighbor.isPresent());
        assertEquals(Integer.valueOf(4), neighbor.get());

        neighbor = res.resolve(4, "5a");
        assertNotNull(neighbor);
        assertFalse(neighbor.isPresent());
    }

    private static StringBuilder sb(CharSequence s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s);
        return sb;
    }

    @Test
    public void testAdaptersAreUsed2() {
        RelativeResolverImplementation<StringBuilder> res = reg.forDocumentAndMimeType(sb("0"), FAKE_MIME);

        Optional<StringBuilder> neighbor = res.resolve(sb("1"), "2a");
        assertNotNull(neighbor);
        assertTrue(neighbor.isPresent());
        assertEquals("2", neighbor.get().toString());

        neighbor = res.resolve(sb("2"), "3a");
        assertNotNull(neighbor);
        assertTrue(neighbor.isPresent());
        assertEquals("3", neighbor.get().toString());

        neighbor = res.resolve(sb("3"), "4a");
        assertNotNull(neighbor);
        assertTrue(neighbor.isPresent());
        assertEquals("4", neighbor.get().toString());

        neighbor = res.resolve(sb("4"), "5a");
        assertNotNull(neighbor);
        assertFalse(neighbor.isPresent());

    }

    @Test
    public void testResolverCreatedCorrectly() {
        RelativeResolverImplementation<String> res = reg.forDocumentAndMimeType("0", FAKE_MIME);

        Optional<String> neighbor = res.resolve("1", "2a");
        assertNotNull(neighbor);
        assertTrue(neighbor.isPresent());
        assertEquals("2", neighbor.get());

        neighbor = res.resolve("2", "3a");
        assertNotNull(neighbor);
        assertTrue(neighbor.isPresent());
        assertEquals("3", neighbor.get());

        neighbor = res.resolve("3", "4a");
        assertNotNull(neighbor);
        assertTrue(neighbor.isPresent());
        assertEquals("4", neighbor.get());

        neighbor = res.resolve("4", "5a");
        assertNotNull(neighbor);
        assertFalse(neighbor.isPresent());

    }

    static class FakeRegistry extends DocumentAdapterRegistry {

        @Override
        protected List<? extends RelativeResolverImplementation<?>> allResolvers(String mimeType) {
            if (!FAKE_MIME.equals(mimeType)) {
                return Collections.emptyList();
            }
            Map<String, String> items = new HashMap<>();
            items.put("1a", "1");
            items.put("2a", "2");
            items.put("3a", "3");
            items.put("4a", "4");
            FakeResolver<String> fr = new FakeResolver<>(String.class, items);
            return Collections.singletonList(fr);
        }

        @Override
        protected List<? extends DocumentAdapter<?, ?>> allAdapters() {
            FakeAdapter<String, Integer> fake = new FakeAdapter<>(String.class, Integer.class, Integer::parseInt, FakeRegistry::i2s);
            FakeAdapter<String, StringBuilder> fake2 = new FakeAdapter<>(String.class, StringBuilder.class, FakeRegistry::s2sb, FakeRegistry::sb2s);
            return Arrays.asList(fake, fake2);
        }

        static String i2s(Integer val) {
            return val.toString();
        }

        static StringBuilder s2sb(String s) {
            return new StringBuilder(s);
        }

        static String sb2s(StringBuilder sb) {
            return sb.toString();
        }
    }

    static final class FakeResolver<T> extends RelativeResolverImplementation<T> {

        private final Map<String, T> objs;

        FakeResolver(Class<T> type, Map<String, T> objs) {
            super(type);
            this.objs = objs;
        }

        @Override
        public Optional<T> resolve(T relativeTo, String name) {
            return Optional.ofNullable(objs.get(name));
        }
    }

    static final class FakeAdapter<T, R> extends DocumentAdapter<T, R> {

        private final Function<T, R> fromTo;
        private final Function<R, T> toFrom;

        FakeAdapter(Class<T> from, Class<R> to, Function<T, R> fromTo, Function<R, T> toFrom) {
            super(from, to, fromTo, toFrom);
            this.fromTo = fromTo;
            this.toFrom = toFrom;
        }
    }
}
