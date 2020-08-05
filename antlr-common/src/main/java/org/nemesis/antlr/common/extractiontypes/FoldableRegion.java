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
package org.nemesis.antlr.common.extractiontypes;

import java.util.Objects;
import org.nemesis.localizers.annotations.Localize;

/**
 *
 * @author Tim Boudreau
 */
public final class FoldableRegion {

    public final FoldableKind kind;
    public final String text;
    private static final int MAX_LENGTH = 45;

    private FoldableRegion(String text, FoldableKind kind) {
        this.text = text;
        this.kind = kind;
    }

    @Override
    public String toString() {
        return kind + "(" + text + ")";
    }

    public boolean shouldFold(String text) {
        return text != null && text.length() > MAX_LENGTH - 3;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.kind);
        hash = 53 * hash + Objects.hashCode(this.text);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FoldableRegion other = (FoldableRegion) obj;
        if (!Objects.equals(this.text, other.text)) {
            return false;
        }
        if (this.kind != other.kind) {
            return false;
        }
        return true;
    }

    public static enum FoldableKind {
        @Localize(displayName="Comment")
        COMMENT,
        @Localize(displayName="Documentation Comment")
        DOC_COMMENT,
        @Localize(displayName="Rule")
        RULE,
        @Localize(displayName="Action")
        ACTION;

        public FoldableRegion createFor(String text) {
            return new FoldableRegion(truncate(text), this);
        }

        private String truncate(String text) {
            switch(this) {
                case RULE :
                    return text;
            }
            if (text.length() > MAX_LENGTH - 1) {
                text = text.substring(MAX_LENGTH - 1) + "\u2026";
            }
            return text;
        }
    }
}
