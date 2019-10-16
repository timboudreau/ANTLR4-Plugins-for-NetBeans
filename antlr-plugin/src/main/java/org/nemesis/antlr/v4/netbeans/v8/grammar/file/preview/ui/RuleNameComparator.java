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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui;

import java.util.Comparator;

/**
 * Sorts non-capitalized rules (parser rules) to the top, then
 * bicapitalized and capitalized (which are just Antlr conventions,
 * but usually indicate fragment vs lexer rule)
 */
final class RuleNameComparator implements Comparator<String> {

    private boolean isCapitalized(String s) {
        if (s.isEmpty()) {
            return false;
        }
        int len = s.length();
        if (len == 1) {
            return Character.isTitleCase(s.charAt(0));
        }
        return Character.isTitleCase(s.charAt(0)) && !Character.isTitleCase(s.charAt(1));
    }

    private boolean isUpperCase(String s) {
        if (s.isEmpty()) {
            return false;
        }
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (!Character.isTitleCase(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int compare(String o1, String o2) {
        boolean title1 = isCapitalized(o1);
        boolean title2 = isCapitalized(o2);
        if (title1 && !title2) {
            return 1;
        } else if (title2 && !title1) {
            return -1;
        }
        boolean ac1 = isUpperCase(o1);
        boolean ac2 = isUpperCase(o2);
        if (ac1 && !ac2) {
            return -1;
        } else if (ac2 && !ac1) {
            return 1;
        }
        return o1.compareTo(o2);
    }

}
