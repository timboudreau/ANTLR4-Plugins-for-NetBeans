/*
 * The MIT License
 *
 * Copyright 2019 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
