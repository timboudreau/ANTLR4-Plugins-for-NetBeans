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
package org.nemesis.antlr.file.impl;

import com.mastfrog.antlr.utils.CompletionsSupplier;
import com.mastfrog.antlr.utils.CompletionsSupplier.Completer;
import java.util.function.BiConsumer;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.localizers.annotations.Localize;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = CompletionsSupplier.class, position = 200000)
public class AdditionalCompletions extends CompletionsSupplier implements Completer {

    @Override
    public Completer forDocument(Document document) {
        String mime = DocumentUtilities.getMimeType(document);
        if (!ANTLR_MIME_TYPE.equals(mime)) {
            return noop();
        }
        return this;
    }

    @Override
    public void namesForRule(int parserRuleId, String optionalPrefix,
            int maxResultsPerKey, String optionalSuffix, BiConsumer<String, Enum<?>> names) {
        switch (parserRuleId) {
            case ANTLRv4Parser.RULE_ebnfSuffix:
                for (EBNFs ebnf: EBNFs.values()) {
                    if (ebnf.canComplete(optionalPrefix, optionalSuffix)){
                        names.accept(ebnf.toString(), ebnf);
                    }
                }
        }
    }

    public static enum EBNFs {
        @Localize(displayName = "Any (greedy)")
        ANY_OR_NONE_GREEDY("*"),
        @Localize(displayName = "Any (non-greedy)")
        ANY_OR_NONE_NON_GREEDY("*?"),
        @Localize(displayName = "One or more (greedy)")
        ONE_OR_MORE_GREEDY("+"),
        @Localize(displayName = "One or more (non-greedy)")
        ONE_OR_MORE_NON_GREEDY("+?"),
        @Localize(displayName = "One or none")
        ONE_OR_NONE("?");

        private final String ebnf;

        EBNFs(String s) {
            this.ebnf = s;
        }

        @Override
        public String toString() {
            return ebnf;
        }

        public boolean canComplete(String prefix, String suffix) {
            if (!suffix.isEmpty()) {
                return false;
            }
            if (!prefix.isEmpty()) {
                switch(prefix.charAt(0)) {
                    case '*':
                    case '+':
                        if (this != ANY_OR_NONE_NON_GREEDY &&
                                this != ONE_OR_MORE_NON_GREEDY) {
                            return false;
                        }
                        return true;
                    default :
                        return false;
                }
            }
            switch (prefix) {
                case "":
                    return true;
                case "*":
                    return this == ANY_OR_NONE_NON_GREEDY && suffix.isEmpty();
                case "+":
                    return this == ONE_OR_MORE_NON_GREEDY && suffix.isEmpty();
                default:
                    return false;
            }
        }
    }
}
