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
package com.mastfrog.tiny.ebnf.parser;

import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class EbnfSuggesterTest {
    static String[] ITEMS = new String[] {
        "K_NAMESPACE? S_ASTERISK* MOOP? PARB",
        "(DESC_DELIMITER? TRUE*)* | (FALSE? STRING*)",
        "S_PERCENT? STRING*",
        "DESCRIPTION*",
        "wookie+?",
        "'.'*",
        "'g'+ 'x'..'y'*? foo+? \"q\"+ 'y'*? booger?",
    };

    @Test
    public void testSomeMethod() {
        for (String i : ITEMS) {
            System.out.println("\n************************");
            System.out.println(i + "");
            for (String s : new EbnfSuggester(i).suggest()) {
                System.out.println("\t" + s);
            }
        }
    }

}
