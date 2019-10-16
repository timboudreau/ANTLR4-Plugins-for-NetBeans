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
package org.nemesis.parsers;

import java.util.Collection;
import org.antlr.v4.runtime.ParserInterpreter;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATN;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class Parser2 extends ParserInterpreter {
    public Parser2
            (String             grammarFileName,
             Collection<String> tokenNames     ,
             Collection<String> ruleNames      ,
             ATN                atn            ,
             TokenStream        input          ) {
        super(grammarFileName, tokenNames, ruleNames, atn, input);
    }
    
}
