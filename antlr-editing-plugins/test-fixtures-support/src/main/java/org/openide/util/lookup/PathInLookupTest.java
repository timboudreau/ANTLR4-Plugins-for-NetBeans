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
package org.openide.util.lookup;

import org.nemesis.test.fixtures.support.TestFixtures.MockNamedServicesImpl;
import org.openide.util.Lookup;
import org.openide.util.lookup.implspi.NamedServicesProvider;

/**
 * The constructor for NamedServicesProvider throws an exception if not one of a
 * few class names, including "PathInLookupTest$P" - so if we want to mock it,
 * we need one of the names it allows.
 *
 * @author Tim Boudreau
 */
public class PathInLookupTest {

    public static final class P extends NamedServicesProvider {

        @Override
        protected Lookup create(String path) {
            return MockNamedServicesImpl.lookupFor(path);
        }

    }
}
