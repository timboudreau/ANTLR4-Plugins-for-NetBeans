/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.live.language;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javax.swing.text.Document;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.tree.ParseTree;
import org.nemesis.antlr.file.AntlrNbParser;
import org.nemesis.antlr.live.language.coloring.AdhocColoringsRegistry;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.debug.api.TrackingRoots;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.spi.lexer.Lexer;
import org.openide.cookies.EditorCookie;
import org.openide.loaders.DataObject;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = TrackingRoots.class)
public class DebugReferencesSupport implements TrackingRoots {

    @Override
    public void collect(BiConsumer<String, Object> nameAndObject) {
        nameAndObject.accept("AdhocMimeDataProvider singleton", AdhocMimeDataProvider.getDefault());
        nameAndObject.accept("AdhocLanguageFactory singleton", AdhocLanguageFactory.get());
        nameAndObject.accept("AdhocColoringsRegistry singleton", AdhocColoringsRegistry.getDefault());
        nameAndObject.accept("AdhocLanguageHierarchy.HINFOS", AdhocLanguageHierarchy.H_INFOS);
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            DataObject dob = tc.getLookup().lookup(DataObject.class);
            if (dob != null) {
                EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                if (ck != null) {
                    Document doc = ck.getDocument();
                    if (doc != null) {
                        nameAndObject.accept("Document for " + tc.getDisplayName(), doc);
                    }
                }
            }
        }
        nameAndObject.accept("NbAntlrUtils caches", NbAntlrUtils.class);
        nameAndObject.accept("AdhocParser.LIVE_PARSERS", AdhocParser.LIVE_PARSERS);
        nameAndObject.accept("AntlrNbParser.class", AntlrNbParser.class);
    }

    @Override
    public Predicate<? super Object> shouldIgnorePredicate() {
        return obj -> {
            if (obj == null) {
                return true;
            }
            if (obj instanceof ParseTree || obj instanceof Lexer || obj instanceof Parser
                    || obj instanceof ATN || obj instanceof ATNConfigSet || obj instanceof Token) {
                return true;
            }
            return false;
        };
    }
}
