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
package org.nemesis.registration;

import com.mastfrog.util.collections.CollectionUtils;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
final class LexerParserTasks {

    private LexerProxy lexer;
    private ParserProxy parser;
    private String prefix;
    private String targetPackage;
    private final Map<String, Set<LexerParserTask>> tasks = CollectionUtils.supplierMap(LinkedHashSet::new);

    void set(String mimeType, LexerProxy lexer, ParserProxy parser, String prefix, String targetPackage) {
        this.lexer = lexer;
        this.parser = parser;
        this.prefix = prefix;
        this.targetPackage = targetPackage;
        Set<LexerParserTask> tasksForMimeType = tasks.get(mimeType);
        for (LexerParserTask task : tasksForMimeType) {
            task.invoke(lexer, parser, prefix, targetPackage);
        }
    }

    void withProxies(String mimeType, LexerParserTask task) {
        if (lexer != null) {
            task.invoke(lexer, parser, prefix, targetPackage);
        } else {
            tasks.get(mimeType).add(task);
        }
    }

    interface LexerParserTask {

        void invoke(LexerProxy lexer, ParserProxy parser, String prefix, String targetPackage);
    }
}
