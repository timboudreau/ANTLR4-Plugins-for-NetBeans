package org.nemesis.extraction.attribution;

import java.util.function.Supplier;
import org.nemesis.extraction.key.NamedRegionKey;

/**
 * Interface which can be implemented by an ImportFinder, allowing
 * the infrastructure to hyperlink or otherwise highlight semantic
 * regions which indicate imports.
 *
 * @author Tim Boudreau
 */
public interface ImportKeySupplier extends Supplier<NamedRegionKey<?>[]>{

}
