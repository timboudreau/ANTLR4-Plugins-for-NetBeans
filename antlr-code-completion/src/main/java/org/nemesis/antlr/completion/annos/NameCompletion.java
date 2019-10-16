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
package org.nemesis.antlr.completion.annos;

import java.util.function.IntFunction;
import org.nemesis.antlr.completion.StringKind;
import org.nemesis.antlr.completion.Stringifier;

/**
 *
 * @author Tim Boudreau
 */
public @interface NameCompletion {

    Class<? extends Stringifier<? super String>> stringifier();

    StringifierPatterns stringifyByPattern() default @StringifierPatterns(patterns = {});

    Class<? extends IntFunction<?>> sorter();

    public static @interface StringifierPatterns {

        StringPattern[] patterns();
    }

    public static @interface StringPattern {

        String pattern();

        StringKind kind();

        String format() default "";

    }



    public static @interface TokenPatterns {

        int[] ignoreTokenTypes() default {};

        TokenPattern[] patterns();
    }

    public static @interface TokenPattern {

        String name();

        int[] before() default {};

        int[] after() default {};

        int[] caret() default {};

        InsertPolicy[] insert() default {};

        InsertAction action();
    }

}
