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
package org.nemesis.antlr.live.language;

import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.adhoc.mime.types.OnAdhocMimeTypeInit;
import org.openide.loaders.DataLoader;
import org.openide.loaders.DataLoaderPool;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = OnAdhocMimeTypeInit.class, position = 1)
public class AdhocInitHook implements OnAdhocMimeTypeInit {

    public void run() {
        Logger log = Logger.getLogger(AdhocInitHook.class.getName());
        Enumeration<DataLoader> ldrs = DataLoaderPool.getDefault().producersOf(AdhocDataObject.class);
        while (ldrs.hasMoreElements()) {
            DataLoader ldr = ldrs.nextElement(); // force initialization
            log.log(Level.FINE, "Force init of {0} for adhoc mime types init", ldr);
        }
    }
}
