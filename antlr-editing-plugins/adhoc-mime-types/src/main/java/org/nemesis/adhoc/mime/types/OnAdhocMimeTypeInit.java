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
package org.nemesis.adhoc.mime.types;

/**
 * When AdhocMimeTypes is initialized, we need to ensure the <i>real</i>
 * DataLoader for AdhocDataObject gets initialized - e.g.
 * <pre>
 * Enumeration<DataLoader> ldrs = DataLoaderPool.getDefault().producersOf(AdhocDataObject.class);
 * while (ldrs.hasMoreElements()) {
 *     ldrs.nextElement(); // force initialization
 * }
 * </pre>
 * since it needs to track when a mime type gets assigned to a file extension
 * and take over loading files of that extension.  So we need a hook when
 * the adhoc mime type system gets initialized to trigger that if it
 * has not already happened.
 *
 * @author Tim Boudreau
 */
public interface OnAdhocMimeTypeInit extends Runnable {

    /*
    public void run() {
        Enumeration<DataLoader> ldrs = DataLoaderPool.getDefault().producersOf(AdhocDataObject.class);
        while (ldrs.hasMoreElements()) {
            ldrs.nextElement(); // force initialization
        }
    }

    */
}
