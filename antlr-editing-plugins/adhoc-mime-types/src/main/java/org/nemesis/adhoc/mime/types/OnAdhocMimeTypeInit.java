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
