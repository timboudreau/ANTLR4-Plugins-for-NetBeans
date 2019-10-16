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

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

/**
 * Facade interface to allow tests to construct a FormattingContextImpl from old
 * and new implementations of stream rewriters to check that they produce the
 * same results with and without optimizations.
 *
 * @author Tim Boudreau
 */
interface StreamRewriterFacade {

    void delete(Token tok);

    void delete(int tokenIndex);

    String getText();

    String getText(Interval interval);

    void insertAfter(Token tok, String text);

    void insertAfter(int tokenIndex, String text);

    void insertBefore(Token tok, String text);

    void insertBefore(int tokenIndex, String text);

    int lastNewlineDistance(int tokenIndex);

    void replace(Token tok, String text);

    void replace(int tokenIndex, String text);

    void close();

    String rewrittenText(int tokenIndex);
}
