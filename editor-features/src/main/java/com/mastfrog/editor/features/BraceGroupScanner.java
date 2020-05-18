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
package com.mastfrog.editor.features;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
public class BraceGroupScanner {

    private final String txt;

    public BraceGroupScanner(String txt) {
        txt = txt.trim();
        if (!balancedBraces(txt)) {
            throw new IllegalArgumentException("Unbalanced braces in '" + txt + "'");
        }
        if (txt.length() > 0 && txt.charAt(0) != '{') {
            txt = '{' + txt + '}';
        }
        System.out.println("txt is " + txt);
        this.txt = txt;
    }

    private static boolean balancedBraces(String s) {
        int openCount = 0;
        int closeCount = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    openCount++;
                    break;
                case '}':
                    closeCount++;
                    break;
            }
        }
        return openCount == closeCount;
    }

    public static void main(String[] args) {
        String t = "{foo bar {baz quux } whee {woo {hoo har} vah}}";
        System.out.println("DOING '" + t + "'");
        List<Object> test = new BraceGroupScanner(t).listify();
        System.out.println("GOT: \n" + test);
        System.out.println("SIZE " + test.size());
        assert test.equals(new BraceGroupScanner("foo bar {baz quux } whee {woo {hoo har} vah}").listify());

        System.out.println("");
        test = new BraceGroupScanner("{bug gum} {woo hoo} thing").listify();
        System.out.println("NOW: " + test);

        System.out.println("");

        test = new BraceGroupScanner("foo").listify();
        System.out.println("SINGLE: " + test);

        test = new BraceGroupScanner("{moo} {bar} {woo}").listify();
        System.out.println("SINGLES: " + test);

        test = new BraceGroupScanner("{moo} woo {bar} far {woo} goo").listify();
        System.out.println("SINGLES2: " + test);


        test = new BraceGroupScanner("{moo} woo {{{bar}}} far {{woo soo}} goo {{coo roo} snoo}").listify();
        System.out.println("SINGLES2: " + test);
        test = new BraceGroupScanner("{moo} car {} woo {{{bar}}} far {{woo soo}} goo {{coo {roo}}}").listify();
        System.out.println("SINGLES2: " + test);

        test = new BraceGroupScanner("  bub    gug  koo   { flu roo { snoo snee} x {whee blee } { moo goo } flee }    sroo   ").listify();
        System.out.println("SPACES: " + test);

    }

    public List<Object> listify() {
        List<Object> result = new ArrayList<>(20);
        LinkedList<List<Object>> ll = new LinkedList<>();
        ll.push(result);
        scan(new BraceGroupVisitor() {
            int depth = 0;

            @Override
            public void onOpenGroup() {
                List<Object> nue = new ArrayList<>(5);
                ll.push(nue);
            }

            @Override
            public void onCloseGroup() {
                List<Object> curr = ll.pop();
                if (!curr.isEmpty()) {
                    if (curr.size() == 1) {
                        ll.peek().add(curr.get(0));
                    } else {
                        ll.peek().add(curr);
                    }
                }
            }

            @Override
            public void item(String name) {
                List<Object> curr = ll.peek();
                curr.add(name);
            }
        });
        if (result.size() == 1 && result.get(0) instanceof List<?>) {
            return (List<Object>) result.get(0);
        }
        return result;
    }

    public void scan(BraceGroupVisitor v) {
        StringBuilder currItem = new StringBuilder();
        v.onOpenGroup();
        int opened = 1;
        for (int i = 0; i < txt.length(); i++) {
            char c = txt.charAt(i);
            if (Character.isWhitespace(c)) {
                if (currItem.length() > 0) {
                    v.item(currItem.toString());
                    currItem.setLength(0);
                }
            } else {
                switch (c) {
                    case '}':
                        if (currItem.length() > 0) {
                            v.item(currItem.toString());
                            currItem.setLength(0);
                        }
                        opened--;
                        v.onCloseGroup();
                        continue;
                    case '{':
                        if (currItem.length() > 0) {
                            v.item(currItem.toString());
                            currItem.setLength(0);
                        }
                        v.onOpenGroup();
                        opened++;
                        continue;
                    default:
                        currItem.append(c);

                }
            }
            if (i == txt.length() - 1) {
                if (currItem.length() > 0) {
                    v.item(currItem.toString());
                }
                opened--;
                v.onCloseGroup();
            }
        }
        while (opened > 0) {
            v.onCloseGroup();
            opened--;
        }
    }

    interface BraceGroupVisitor {

        void onOpenGroup();

        void onCloseGroup();

        void item(String name);
    }
}
