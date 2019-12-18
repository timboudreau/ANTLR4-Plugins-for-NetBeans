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
package org.nemesis.antlr.completion.grammar;

import com.mastfrog.function.throwing.io.IOFunction;
import java.io.IOException;
import java.util.function.Function;
import javax.swing.text.Document;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 *
 * @author Tim Boudreau
 */
class ParserAndRuleContextProvider<P extends Parser, R extends ParserRuleContext> {

    private final IOFunction<Document, P> parserForDoc;
    private final Function<P, R> ruleContextForParser;

    public ParserAndRuleContextProvider(IOFunction<Document, P> parserForDoc, Function<P, R> ruleContextForParser) {
        this.parserForDoc = parserForDoc;
        this.ruleContextForParser = ruleContextForParser;
    }

    P createParser(Document doc) throws IOException {
        return parserForDoc.apply(doc);
    }

    R rootElement(P parser) {
        return ruleContextForParser == null ? null : ruleContextForParser.apply(parser);
    }

}
