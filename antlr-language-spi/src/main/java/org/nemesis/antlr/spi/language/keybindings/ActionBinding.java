package org.nemesis.antlr.spi.language.keybindings;

/**
 * Allows a custom keybinding to be set up for an existing NetBeans action
 * for a particular mime type and profile.
 *
 * @author Tim Boudreau
 */
public @interface ActionBinding {

    NetBeansActions action() default NetBeansActions.NONE;
    String actionName() default "";
    Keybinding[] bindings();
    String mimeType();
}
