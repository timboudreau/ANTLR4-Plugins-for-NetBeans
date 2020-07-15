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
package org.nemesis.editor.edit;

import java.util.function.Supplier;
import org.nemesis.editor.ops.DocumentOperator;

/**
 * Passed to the constructor of (exactly one) EditBag and allows the creator of
 * it to apply any edits collected in it.
 *
 * @author Tim Boudreau
 */
public final class Applier {

    private EditBag set;
    private final Supplier<DocumentOperator> op;

    public Applier(DocumentOperator op) {
        this.op = () -> op;
    }

    public Applier() {
        this(() -> DocumentOperator.NON_JUMP_REENTRANT_UPDATE_DOCUMENT);
    }

    public Applier(Supplier<DocumentOperator> op) {
        this.op = op;
    }

    void setSet(EditBag set) {
        if (this.set != null) {
            throw new IllegalStateException("Can only be used for one " + EditBag.class.getSimpleName());
        }
        this.set = set;
    }

    public void apply() throws Exception {
        apply(op.get());
    }

    public void apply(DocumentOperator op) throws Exception {
        if (set == null) {
            throw new IllegalStateException("Not associated with a " + EditBag.class.getSimpleName());
        }
        set.apply(op);
    }

}
