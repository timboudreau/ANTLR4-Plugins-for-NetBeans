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
package com.mastfrog.antlr.code.completion.spi;

import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;

/**
 * Collection of completion items which can be added to.
 *
 * @author Tim Boudreau
 */
public interface CompletionItems {

    CompletionItems add(String itemText, Enum<?> kind);

    CompletionItems add(String itemText, String description);

    CompletionItems add(String itemText, String description, CompletionApplier applier);

    interface CompletionApplier {

        void apply(JTextComponent comp, StyledDocument doc);
    }
    /*
    CompletionItemBuilder add(CompletionApplier applier);

    public static abstract class CompletionItemBuilder {

        private String description;
        private CompletionApplier applier;
        private String completionText;

        protected CompletionItemBuilder(String completionText) {

        }

        protected CompletionItemBuilder(CompletionApplier applier) {

        }

        protected abstract CompletionApplier createApplier(String completionText);

        public CompletionItemBuilder setDescription(String desc) {
            this.description = desc;
            return this;
        }

        public abstract CompletionItemBuilder setDescription(Enum<?> desc);

        interface CompletionRenderer {

            Dimension preferredSize(Graphics2D graphics, Font targetFont, Color defaultColor, Color backgroundColor, int width, int height, boolean selected);

            void render(Graphics2D graphics, Font font);
        }
    }
     */
}
