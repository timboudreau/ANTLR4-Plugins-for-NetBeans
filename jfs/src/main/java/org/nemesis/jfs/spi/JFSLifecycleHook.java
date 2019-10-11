package org.nemesis.jfs.spi;

import org.nemesis.jfs.JFS;

/**
 *
 * @author Tim Boudreau
 */
public abstract class JFSLifecycleHook {

    public abstract void jfsCreated(JFS jfs);

    public abstract void jfsClosed(JFS jfs);
}
