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
package org.nemesis.editor.utils;

import com.mastfrog.function.IntBiConsumer;
import java.util.function.Consumer;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * Interface which can compute the new caret position after a transformation on
 * a document - useful for cases where the document will be updated wholesale
 * and Position objects will all be pushed to the beginning or end (reformatting
 * does that).
 *
 * @author Tim Boudreau
 */
public interface CaretPositionCalculator {

    /**
     * Save whatever information is needed to re-locate the caret position
     * passed in the passed CaretInformation; return a consumer which, when
     * passed an IntBiConsumer, will re-parse (if necessary) the (possibly
     * revised) document, and pass the new dot and mark locations to that
     * consumer in that order.
     *
     * @param caret The caret
     * @param comp The component
     * @param doc The document
     * @return A consumer which will recompute the updated position from the
     * original caret position, and pass new dot and mark positions to the
     * IntBiConsumer passed to it
     */
    Consumer<IntBiConsumer> createPostEditPositionFinder(CaretInformation caret, JTextComponent comp, Document doc);

}
