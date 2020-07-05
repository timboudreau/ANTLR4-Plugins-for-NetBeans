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
package org.nemesis.antlr.live.preview;

import com.mastfrog.function.throwing.io.IOConsumer;
import java.io.IOException;
import org.openide.cookies.SaveCookie;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.lookup.InstanceContent;

/**
 *
 * @author Tim Boudreau
 */
public class MetaSaveCookie implements LookupListener, SaveCookie {

    private final Lookup.Result<SaveCookie> aResult;
    private final Lookup.Result<SaveCookie> bResult;
    private final InstanceContent target;
    private boolean present;

    MetaSaveCookie(InstanceContent target, Lookup a, Lookup b) {
        this.target = target;
        aResult = a.lookupResult(SaveCookie.class);
        bResult = b.lookupResult(SaveCookie.class);
        aResult.allInstances();
        bResult.allInstances();
        resultChanged(null);
    }

    static MetaSaveCookie attach(InstanceContent target, Lookup a, Lookup b) {
        return new MetaSaveCookie(target, a, b);
    }

    private int cookiesPresent() {
        return aResult.allItems().size() + bResult.allItems().size();
    }

    private void addOrRemove(boolean add) {
        assert Thread.holdsLock(this);
        if (add != present) {
            if (add) {
                target.add(this);
            } else {
                target.remove(this);
            }
            present = add;
        }
    }

    @Override
    public synchronized void resultChanged(LookupEvent le) {
        addOrRemove(cookiesPresent() > 0);
    }

    void forEachCookie(IOConsumer<? super SaveCookie> c) throws IOException {
        for (SaveCookie sc : aResult.allInstances()) {
            c.accept(sc);
        }
        for (SaveCookie sc : bResult.allInstances()) {
            c.accept(sc);
        }
    }

    @Override
    public void save() throws IOException {
        forEachCookie(SaveCookie::save);
    }
}
