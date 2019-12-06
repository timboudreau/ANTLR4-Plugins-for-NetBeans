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
package org.nemesis.registration.typenames;

import com.mastfrog.function.TriFunction;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.nemesis.registration.typenames.JdkTypes.ATOMIC_INTEGER;
import static org.nemesis.registration.typenames.KnownTypes.LEXER_INTERPRETER;
import static org.nemesis.registration.typenames.KnownTypes.TRI_FUNCTION;

/**
 *
 * @author Tim Boudreau
 */
public class TypeNamesTest {

    @Test
    public void testSomeMethod() {
        assertEquals(TriFunction.class.getName(), TRI_FUNCTION.qname());
        assertEquals(AtomicInteger.class.getName(), ATOMIC_INTEGER.qname());
        assertEquals("AntlrLanguageRegistration", KnownTypes.ANTLR_LANGUAGE_REGISTRATION.simpleName());

        assertSame(LEXER_INTERPRETER, KnownTypes.forName("org.antlr.v4.runtime.LexerInterpreter"));

        assertEquals("LexerInterpreter", LEXER_INTERPRETER.simpleName());
        
        String msg = KnownTypes.touchedMessage(this);
//        System.out.println(msg);
        assertTrue(msg.contains("<artifactId>antlr-language-spi</artifactId>"));
        assertTrue(msg.contains("<artifactId>mastfrog-utils-wrapper</artifactId>"));
        assertTrue(msg.contains("<artifactId>antlr-language-spi</artifactId>"));

    }

}
