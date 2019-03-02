package org.nemesis.antlr.spi.language.highlighting.semantic;

/**
 *
 * @author Tim Boudreau
 */
public enum HighlightZOrder {

        /**
     * The highest rack of z-orders. Layers in this rack will be placed at
     * the top of the hierarchy.
     */
    TOP_RACK,

    /**
     * The show off rack of z-orders. This rack is meant to be used by
     * layers with short-lived highlights that can temporarily override highlights
     * provided by other layers (eg. syntax coloring).
     */
    SHOW_OFF_RACK,

    /**
     * The default rack of z-orders. This rack should be used by most of the layers.
     */
    DEFAULT_RACK,

    /**
     * The rack for highlights showing the position of a caret.
     */
    CARET_RACK,

    /**
     * The syntax highlighting rack of z-order. This rack is meant to be used by
     * layers that provide highlighting of a text according to its syntactical or
     * semantical rules.
     */
    SYNTAX_RACK,

    /**
     * The lowest rack of z-orders. Layers in this rack will be placed at the
     * bottom of the hierarchy.
     */
    BOTTOM_RACK;

    /*
    ZOrder toZOrder() {
        switch(this) {
            case BOTTOM_RACK :
                return ZOrder.BOTTOM_RACK;
            case CARET_RACK :
                return ZOrder.CARET_RACK;
            case DEFAULT_RACK :
                return ZOrder.DEFAULT_RACK;
            case SHOW_OFF_RACK :
                return ZOrder.SHOW_OFF_RACK;
            case SYNTAX_RACK :
                return ZOrder.SYNTAX_RACK;
            case TOP_RACK :
                return ZOrder.TOP_RACK;
            default :
                throw new AssertionError(this);
        }
    }
*/
}
