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

package org.nemesis.antlr.live.parsing;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.swing.event.ChangeListener;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Task;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.ParserFactory;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;

// Sigh - we need to grab the parsing infrastructure's lock inside

// EmbeddedAntlrParserImpl preemptively, to keep from code we call back
// from acquiring it out-of-order.  Which means we need to register a do-nothing
// parser to keep the plumbing from complaining that there's no parser registered
// for our mime type
public final class FakeParserFactory extends ParserFactory {

    @Override
    public Parser createParser(Collection<Snapshot> clctn) {
        return new FakeParser();
    }

    static class FakeParser extends Parser {

        private final Map<Task, Result> results = new HashMap<>();

        @Override
        public void parse(Snapshot snpsht, Task task, SourceModificationEvent sme) throws ParseException {
            results.put(task, new Result(snpsht) {
                @Override
                protected void invalidate() {
                    // do nothing
                }
            });
        }

        @Override
        public Result getResult(Task task) throws ParseException {
            return results.remove(task);
        }

        @Override
        public void addChangeListener(ChangeListener cl) {
        }

        @Override
        public void removeChangeListener(ChangeListener cl) {
        }
    }

}
