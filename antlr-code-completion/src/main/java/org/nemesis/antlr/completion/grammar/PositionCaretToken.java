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
package org.nemesis.antlr.completion.grammar;

import com.mastfrog.antlr.code.completion.spi.CaretToken;
import com.mastfrog.util.search.Bias;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class PositionCaretToken implements CaretToken {

    private final CaretToken orig;
    private final StyledDocument doc;
    private final Position start;
    private final Position stop;
    private final Position caretPosition;

    PositionCaretToken(StyledDocument doc, CaretToken orig) throws BadLocationException {
        start = NbDocument.createPosition(doc, orig.tokenStart(), Position.Bias.Forward);
        stop = NbDocument.createPosition(doc, orig.tokenStart(), Position.Bias.Backward);
        caretPosition = NbDocument.createPosition(doc, orig.caretPositionInDocument(), Position.Bias.Backward);
        this.orig = orig;
        this.doc = doc;
    }

    private CaretToken wrap(CaretToken orig) {
        if (orig == this.orig) {
            return this;
        }
        if (orig.isUserToken()) {
            try {
                if (orig instanceof PositionCaretToken) {
                    return orig;
                }
                return new PositionCaretToken(doc, orig);
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return orig;
    }

    @Override
    public CaretToken after() {
        return wrap(orig.after());
    }

    @Override
    public CaretToken before() {
        return wrap(orig.before());
    }

    @Override
    public CaretToken biasedBy(Bias bias) {
        return wrap(orig.biasedBy(bias));
    }

    @Override
    public int tokenStart() {
        return start.getOffset();
    }

    @Override
    public int tokenStop() {
        return stop.getOffset();
    }

    @Override
    public String tokenText() {
        return orig.tokenText();
    }

    @Override
    public int tokenType() {
        return orig.tokenType();
    }

    @Override
    public boolean isUserToken() {
        return orig.isUserToken();
    }

    @Override
    public int caretPositionInDocument() {
        return caretPosition.getOffset();
    }

    @Override
    public int tokenIndex() {
        return orig.tokenIndex();
    }
}
