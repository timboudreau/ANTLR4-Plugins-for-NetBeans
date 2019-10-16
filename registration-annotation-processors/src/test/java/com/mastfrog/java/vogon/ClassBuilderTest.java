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
package com.mastfrog.java.vogon;

import com.mastfrog.java.vogon.ClassBuilder;
import javax.lang.model.element.Modifier;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class ClassBuilderTest {

    private static final String CMT = "This is a long doc comment that ought to get "
            + "wrapped with the appopriate prefix added to it, because that is the sort of thing "
            + "which doc comments ought to do if done right, right?  I mean, it's a nice thing, I think.";

    @Test
    public void testSomeMethod() {
        String txt = ClassBuilder.forPackage("com.foo.bar").named("MyClass")
                .docComment(CMT)
                .importing("java.util.List")
                .importing("java.io.IOException")
                .withModifier(Modifier.FINAL)
                .withModifier(Modifier.PUBLIC)
                .annotatedWith("ServiceProvider").addExpressionArgument("service", "MyClass.class").closeAnnotation()
                .field("DOOHICKEY")
                .withModifier(Modifier.FINAL)
                .withModifier(Modifier.STATIC)
                .withModifier(Modifier.PRIVATE)
                .initializedTo("\"foo\"")
                .ofType("String")
                .method("doSomething")
                .returning("String")
                .addArgument("String", "bar")
                .withModifier(Modifier.PUBLIC).withModifier(Modifier.FINAL)
                .annotatedWith("MimeRegistration").addExpressionArgument("position", "32")
                .addArgument("value", "text/x-foo")
                .closeAnnotation()
                .body()
                .iff().booleanExpression("bar.equals(\"moo\")").returning("\"hey\"")
                .orElse()
                .switchingOn("bar")
                .inStringLiteralCase("hoober")
                .returningStringLiteral("woo hoo").endBlock()
                .inStringLiteralCase("quo\"te")
                .returningStringLiteral("hey hey").endBlock()
                .inDefaultCase()
                .statement("System.out.println(\"uh oh\")").endBlock()
                .build()
                .endBlock()
                .returning("DOOHICKEY").endBlock()
                .innerClass("Wookie")
                .implementing("Iterable<String>")
                .makePublic().makeStatic().makeFinal()
                .method("iterator")
                .throwing("NoClassDefFoundError").throwing("IOException")
                .withModifier(Modifier.PUBLIC)
                .returning("Iterator<String>")
                .body().statement("throw new UnsupportedOperationException()")
                .endBlock()
                .method("doStuff")
                .addArgument("String", "foo")
                .body()
                .declare("result").initializedWith("\"not found\"").as("String")
                .iff().invoke("length").on("foo").notEquals().literal(0)
                .or().literal("whee").equals().expression("foo")
                .endCondition().returning("wookie")
                .elseIf().invoke("length").on("foo").greaterThan().literal(80)
                .endCondition().returningStringLiteral("hello\nyou")
                .orElse().returning("DOOHICKEY")
                .endBlock()
                .returning("foo")
                .endBlock()
                .build()
                .build();

        System.out.println("TEXT:\n" + txt);

    }

}
