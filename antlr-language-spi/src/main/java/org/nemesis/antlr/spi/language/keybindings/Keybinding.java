package org.nemesis.antlr.spi.language.keybindings;

/**
 *
 * @author Tim Boudreau
 */
public @interface Keybinding {

    String[] profiles() default "NetBeans";

    KeyModifiers[] modifiers();

    Key key();

    boolean appleSpecific() default false;

    boolean asynchronous() default false;
}
