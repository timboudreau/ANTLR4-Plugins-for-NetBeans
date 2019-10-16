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
