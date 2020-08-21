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
package org.nemesis.antlr.refactoring;

import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;
import org.antlr.v4.runtime.Lexer;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.netbeans.modules.refactoring.spi.ui.RefactoringUI;

/**
 * Unused at present.
 *
 * @author Tim Boudreau
 */
@Retention(SOURCE)
@Target(FIELD)
public @interface CustomRefactoringRegistration {

    int[] enabledOnTokens();

    String mimeType();

    String name();

    String description() default "";

    Class<? extends AbstractRefactoring> refactoring() default AbstractRefactoring.class;

    Class<? extends RefactoringPlugin> plugin();

    Class<? extends RefactoringUI> ui() default RefactoringUI.class;

    Class<? extends Lexer> lexer();

    int actionPosition() default 1000;

    String keybinding() default "";

    String languageHierarchyPackage() default "";

    boolean publicRefactoringPluginClass() default false;
}
