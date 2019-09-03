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
