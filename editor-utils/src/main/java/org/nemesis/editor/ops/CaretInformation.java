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
package org.nemesis.editor.ops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import org.netbeans.api.editor.caret.CaretInfo;
import org.netbeans.api.editor.caret.EditorCaret;

/**
 * Wraps the Editor API's CaretInfo, or a Swing Caret, whichever is present, in
 * order to provide position information regardless of which is present without
 * needing a separate method on CaretPositionCalculator for both.
 *
 * @author Tim Boudreau
 */
public interface CaretInformation {

    int dot();

    int mark();

    Position.Bias dotBias();

    Position.Bias markBias();

    default int selectionStart() {
        return Math.min(dot(), mark());
    }

    default int selectionEnd() {
        return Math.max(dot(), mark());
    }

    public static List<CaretInformation> create(JTextComponent comp) {
        Caret c = comp.getCaret();
        if (c instanceof EditorCaret) {
            EditorCaret ec = (EditorCaret) c;
            List<CaretInformation> result = new ArrayList<>();
            List<CaretInfo> carets = ec.getCarets();
            for (CaretInfo info : carets) {
                result.add(new DocumentOperator.EditorCaretInformation(info));
            }
            return result;
        } else {
            try {
                return Arrays.asList(new DocumentOperator.SwingCaretInformation(c, comp.getDocument()));
            } catch (BadLocationException ex) {
                org.openide.util.Exceptions.printStackTrace(ex);
                return Collections.emptyList();
            }
        }
    }
}
