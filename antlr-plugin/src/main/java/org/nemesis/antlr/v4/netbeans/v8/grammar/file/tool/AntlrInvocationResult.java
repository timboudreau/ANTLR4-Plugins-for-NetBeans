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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrInvocationResult {

    final List<String> infoMessages = new ArrayList<>();
    final List<ParsedAntlrError> errors = new ArrayList<>();

    public List<String> infoMessages() {
        return infoMessages;
    }

    public List<ParsedAntlrError> errors() {
        return errors;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String s : infoMessages) {
            sb.append(s);
            sb.append('\n');
        }
        for (ParsedAntlrError e : errors) {
            sb.append(e);
            sb.append('\n');
        }
        return sb.toString();
    }
}
