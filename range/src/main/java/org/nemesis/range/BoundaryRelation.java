package org.nemesis.range;

/**
 * Interface for relations between positions and/or ranges.
 *
 * @see PositionRelation
 * @see RangeRelation
 * @see RangePositionRelation
 * @author Tim Boudreau
 */
public interface BoundaryRelation {

    boolean isOverlap();

}
