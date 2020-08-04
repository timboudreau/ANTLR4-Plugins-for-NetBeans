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

package org.nemesis.antlr.output;

import java.lang.ref.WeakReference;
import org.nemesis.jfs.JFSFileObject;
import org.openide.util.Utilities;

/**
 *
 * @author Tim Boudreau
 */
final class TextSupplierRef extends WeakReference<TextSupplier> implements Runnable {

    final JFSFileObject fo;
    final FileIndices indices;
    final int index;
    volatile boolean disposed;

    public TextSupplierRef(JFSFileObject fo, FileIndices indices, int index, TextSupplier referent) {
        super(referent, Utilities.activeReferenceQueue());
        this.fo = fo;
        this.indices = indices;
        this.index = index;
    }

    @Override
    public void run() {
        disposed = true;
        fo.delete();
        indices.readerDisposed(index);
    }

}
