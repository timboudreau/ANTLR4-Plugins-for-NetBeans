/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.spi.language.keybindings;

/**
 *
 * @author Tim Boudreau
 */
public enum NetBeansActions {
    ToggleToolbar("toggle-toolbar"),
    ToggleLineNumbers("toggle-line-numbers"),
    ToggleNonPrintableCharacters("toggle-non-printable-characters"),
    GotoDeclaration("goto-declaration"),
    ZoomTextIn("zoom-text-in"),
    ZoomTextOut("zoom-text-out"),
    ToggleRectangularSelection("toggle-rectangular-selection"),
    TransposeLetters("transpose-letters"),
    MoveCodeElementUp("move-code-element-up"),
    MoveCodeElementDown("move-code-element-down"),
    RemoveSurroundingCode("remove-surrounding-code"),
    OrganizeImports("organize-imports"),
    OrganizeMembers("organize-members"),
    ToggleTypingMode("toggle-typing-mode"),
    RemoveLastCaret("remove-last-caret"),
    GotoPrevOccurrence("prev-marked-occurrence"),
    GotoNextOccurrence("next-marked-occurrence"),
    AddCaretUp("add-caret-up"),
    AddCaretDown("add-caret-down"),
    NONE("")
    ;

    private final String programmaticName;

    NetBeansActions(String name) {
        this.programmaticName = name;
    }

    @Override
    public String toString() {
        return programmaticName;
    }

    public static void main(String[] args) throws Throwable {
        System.out.println("switch(name) {");
        for (NetBeansActions a : values()) {
            if (a == NetBeansActions.NONE) {
                continue;
            }
            System.out.println("    case \"" + a.name() + "\" :");
            System.out.println("        return \"" + a.toString() + "\";");
        }
        System.out.println("}");
        System.out.println("return null;");
    }

}
