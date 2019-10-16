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
package org.nemesis.antlr.language.formatting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * A dirt simple collator for text. Takes an IntPredicate to match characters a
 * line break may occur on (default is any whitespace), and emits exactly one
 * null when it encounters two or more newlines either adjacent or separated
 * only by whitespace, unless those newlines are leading or trailing.
 *
 * @author Tim Boudreau
 */
final class SimpleCollator implements Iterator<String>, Iterable<String> {

    private final String text;
    private int cursor = 0;
    private final IntPredicate whitespace;

    public SimpleCollator(String text) {
        this(text, defaultWordBreak());
    }

    public SimpleCollator(String text, IntPredicate whitespace) {
        this.text = text;
        this.whitespace = whitespace;
        cursor = 0;
    }

    static IntPredicate defaultWordBreak() {
        return (int ch) -> {
            return Character.isWhitespace(ch);
        };
    }
    private boolean needAdvance = true;
    private final StringBuilder scratch = new StringBuilder(32);
    private boolean hasEmittedCharacters;

    private void advance() {
        hasNext = false;
        next = null;
        if (cursor >= text.length()) {
            needAdvance = false;
            return;
        }
        scratch.setLength(0);
        int seenNewlineCount = 0;
        for (int i = cursor; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean isWhitespace = whitespace.test(c);
            boolean isNewline = c == '\n';
            if (!isWhitespace && !isNewline) {
                if (seenNewlineCount > 1 && hasEmittedCharacters) {
                    hasNext = true;
                    next = null;
                    cursor = i;
                    break;
                }
                scratch.append(c);
            } else if (isWhitespace && scratch.length() > 0) {
                next = scratch.toString();
                hasNext = true;
                cursor = i;
                break;
            } else if (isNewline) {
                seenNewlineCount++;
            }
            if (i == text.length() - 1 && scratch.length() > 0) {
                next = scratch.toString();
                hasNext = true;
                cursor = i + 1;
            }
        }
        needAdvance = false;
    }
    private boolean hasNext;
    private String next;

    @Override
    public String next() {
        if (needAdvance) {
            advance();
        }
        String result = next;
        needAdvance = true;
        hasEmittedCharacters |= result != null;
        return result;
    }

    @Override
    public boolean hasNext() {
        if (needAdvance) {
            advance();
        }
        return hasNext;
    }

    @Override
    public Iterator<String> iterator() {
        return new SimpleCollator(text, whitespace);
    }

    List<String> toList() {
        List<String> result = new ArrayList<>();
        int old = cursor;
        String oldNext = next;
        boolean oldNeedAdvance = needAdvance;
        boolean oldHasNext = hasNext;
        boolean oldHasEmitted = hasEmittedCharacters;
        cursor = 0;
        while (hasNext()) {
            result.add(next());
        }
        cursor = old;
        next = oldNext;
        needAdvance = oldNeedAdvance;
        hasNext = oldHasNext;
        hasEmittedCharacters = oldHasEmitted;
        return result;
    }

    public String toString() {
        List<String> all = toList();
        StringBuilder sb = new StringBuilder();
        boolean lastWasNewline = true;
        for (int i = 0; i < all.size(); i++) {
            String word = all.get(i);
            if (word == null) {
                lastWasNewline = true;
                sb.append("\n\n");
            } else {
                if (!lastWasNewline) {
                    sb.append(' ');
                }
                sb.append(word);
                lastWasNewline = false;
            }
        }
        return sb.toString();
    }
}
