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
package org.nemesis.antlr.error.highlighting;

import org.nemesis.antlr.ANTLRv4Parser;

/**
 *
 * @author Tim Boudreau
 */
enum EbnfItem {
    QUESTION,
    PLUS,
    STAR,
    STAR_QUESTION,
    PLUS_QUESTION,
    NONE;

    boolean matchesMultiple() {
        switch (this) {
            case QUESTION:
            case NONE:
                return false;
        }
        return true;
    }

    EbnfItem nonEmptyMatchingReplacement() {
        switch (this) {
            case QUESTION:
                return NONE;
            case STAR:
                return PLUS;
            case STAR_QUESTION:
                return PLUS_QUESTION;
            default:
                return this;
        }
    }

    boolean canMatchEmptyString() {
        switch (this) {
            case QUESTION:
            case STAR:
            case STAR_QUESTION:
                return true;
            default:
                return false;
        }
    }

    boolean isNonGreedy() {
        switch (this) {
            case STAR_QUESTION:
            case PLUS_QUESTION:
            case QUESTION:
                return true;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        switch (this) {
            case PLUS:
                return "+";
            case PLUS_QUESTION:
                return "+?";
            case STAR:
                return "*";
            case STAR_QUESTION:
                return "*?";
            case QUESTION:
                return "?";
            default:
                return "";
        }
    }

    static EbnfItem forEbnfSuffix(ANTLRv4Parser.EbnfSuffixContext ctx) {
        if (ctx.STAR() != null && ctx.getText().indexOf('*') >= 0) {
            if (ctx.QUESTION() != null && ctx.getText().indexOf('?') >= 0) {
                return STAR_QUESTION;
            } else {
                return STAR;
            }
        } else if (ctx.PLUS() != null && ctx.getText().indexOf('+') >= 0) {
            if (ctx.QUESTION() != null && ctx.getText().indexOf('?') >= 0) {
                return PLUS_QUESTION;
            } else {
                return PLUS;
            }
        } else if (ctx.QUESTION() != null && ctx.getText().indexOf('?') >= 0) {
            return QUESTION;
        } else {
            return NONE;
        }
    }
}
