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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract;

import java.io.IOException;
import java.util.function.Consumer;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
public interface ParseProxyBuilder {

    GenerateBuildAndRunGrammarResult buildNoParse() throws IOException;

    GenerateBuildAndRunGrammarResult parse(String text) throws IOException;

    @NbBundle.Messages(value = {"# {0} - grammar file ", "GENERATING_ANTLR_SOURCES=Running antlr on {0}", "# {0} - grammar file ", "COMPILING_ANTLR_SOURCES=Compiling generated sources for {0}", "# {0} - grammar file ", "EXTRACTING_PARSE=Extracting parse for {0}", "CANCELLED=Cancelled."})
    GenerateBuildAndRunGrammarResult parse(String text, Consumer<String> status) throws IOException;

    ParseProxyBuilder regenerateAntlrCodeOnNextCall();

}
