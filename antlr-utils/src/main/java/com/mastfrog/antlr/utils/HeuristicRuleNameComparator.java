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

import java.util.Comparator;

/**
 * Sorts rule names to group related ones together based on bicapitalization or
 * underscores in their names and shared prefixes or suffixes, using the
 * following algorithm:
 * <ul>
 * <li>If the rules share leading characters of length &gt; 2, use standard
 * case-sensitive string comparison</li>
 * <li>If the rules share an identical tail, where in both cases, the tail
 * terminates in a case transition or underscore, sort the rules first
 * prepending the tail to the head of both items</li>
 * <li>Failing that, fall back to standard case-sensitive string comparison</li>
 * </ul>
 * The result on most Antlr grammars is to group related rules together,
 * regardless of naming convention - so "ListItem", "ListItemHead" and
 * "ListItemTail" get grouped together, but so do "BlockComment",
 * "OuterBlockComment", "InnerBlockComment", "LineComment", and are sensibly
 * internally sorted.  Similar for capitalized/underscore-delmited names
 * such as "LIST_ITEM", "LIST_ITEM_TAIL", "LIST_ITEM_HEAD" - while incidentally
 * related terms such as "ListItem" and "ListItemizer" sort normally.
 *
 * @author Tim Boudreau
 */
public final class HeuristicRuleNameComparator implements Comparator<String> {

    public static final HeuristicRuleNameComparator INSTANCE = new HeuristicRuleNameComparator();

    @Override
    public int compare(String a, String b) {
        // A smidgen of black magic here:  We want semantically related names
        // to sort together, and it's not unusual for the tail of the name to
        // express the relation.  So, if we encounter "BlockComment" and
        // "LineComment" it makes sense that they should be grouped together.
        //
        // So, find a suffix on each word being compared, looing for _ if all
        // caps, or the first non-tail capital letter if bi-capitalized, and
        // if in the tail, copy it to the head of the word before comparing
        //
        // First, if they share some text at the head, prefer that as a basis
        // for coparison and just do a normal string compare
        String sha = sharedHead(a, b);
        if (sha != null) {
            return a.compareTo(b);
        }
        // From here, the starting characters are different
        String workingA = a;
        String workingB = b;
        String suffA = suffix(a);
        String suffB = suffix(b);
        if (suffA != null && b.startsWith(suffA)) {
            workingA = suffA + a;
        } else if (suffA != null && b.endsWith(suffA)) {
            workingB = suffA + b;
        } else if (suffA != null) {
            workingA = suffA + a;
        }
        if (suffB != null && a.startsWith(suffB)) {
            workingB = suffB + b;
        } else if (suffB != null && a.endsWith(suffB)) {
            workingA = suffB + a;
        } else if (suffB != null) {
            workingB = suffB + b;
        }
        return workingA.compareTo(workingB);
    }

    static String sharedHead(String a, String b) {
        int max = Math.min(a.length(), b.length());
        StringBuilder result = new StringBuilder(max);
        boolean onTransition = false;
        for (int i = 0; i < max; i++) {
            char c = a.charAt(i);
            if (c == b.charAt(i)) {
                onTransition = i == max - 1;
                result.append(a.charAt(i));
            } else {
                if (result.length() > 0) {
                    if (i == max - 1) {
                        onTransition = true;
                    } else {
                        onTransition = c == '_' || (Character.isLetter(c) && Character.isUpperCase(c));
                    }
                }
                break;
            }
        }
        return result.length() == 0 ? null : onTransition && result.length() > 2 ? result.toString() : null;
    }

    static String suffix(String s) {
        int start = s.length() - 1;
        boolean lc = containsLowerCase(s);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = start; i >= 0; i--) {
            char c = s.charAt(i);
            if (lc && i != start && Character.isUpperCase(c) && Character.isAlphabetic(c)) {
                sb.insert(0, c);
                return sb.toString();
            } else if (!lc && i != start && c == '_') {
                return sb.toString();
            }
            sb.insert(0, c);
        }
        return null;
    }

    private static boolean containsLowerCase(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isAlphabetic(s.charAt(i)) && Character.isLowerCase(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
