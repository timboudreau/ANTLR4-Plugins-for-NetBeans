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
package org.nemesis.registration.typenames;

import com.mastfrog.annotation.AnnotationUtils;
import com.mastfrog.java.vogon.ClassBuilder;

/**
 *
 * @author Tim Boudreau
 */
public interface TypeName {

    /**
     * Fetch the fully qualified name of the type.  Calling this method
     * on some instances causes its originating library to be tracked and
     * added to the set of libraries the user will be warned to add dependencies on.
     *
     * @return The qualified name
     */
    String qname();

    /**
     * Fetch the simple name of the type.  Calling this method
     * on some instances causes its originating library to be tracked and
     * added to the set of libraries the user will be warned to add dependencies on.
     *
     * @return The qualified name
     */
    String simpleName();

    /**
     * Get the library this type originated in, if not the JDK and not
     * a dynamically created instance.
     *
     * @return The library this type came from
     */
    Library origin();

    /**
     * Get the qualified name, without updating the set of used
     * libraries.
     *
     * @return The qualified name
     */
    String qnameNotouch();

    /**
     * If this is a parameterized type representation, fetch the
     * erasure of it - for example, "java.util.Map" for "java.util.Map&lt;Foo,Bar&gt;".
     *
     * @return The type's erasure
     */
    default TypeName erasure() {
        return this;
    }

    /**
     * Add this type to the passed ClassBuilder as an import.
     *
     * @param <T> The type
     * @param t The builder
     * @return The builder
     */
    default <T> ClassBuilder<T> addImport(ClassBuilder<T> t) {
        String qn = erasure().qname();
        if (!qn.startsWith("java.lang")) {
            t.importing(erasure().qname());
        }
        return t;
    }

    /**
     * Add a set of types to the passed ClassBuilder as an import.
     *
     * @param <T> The type
     * @param t The builder
     * @return The builder
     */
    static <T> ClassBuilder<T> addImports(ClassBuilder<T> t, TypeName... names) {
        for (TypeName tn : names) {
            tn.addImport(t);
        }
        return t;
    }

    default String parametrizedName(String... types) {
        StringBuilder sb = new StringBuilder(simpleName()).append('<');
        for (int i = 0; i < types.length; i++) {
            sb.append(types[i]);
            if (i != types.length - 1) {
                sb.append(", ");
            }
        }
        return sb.append('>').toString();
    }

    default String parameterizedInferenced() {
        return simpleName() + "<>";
    }

    default TypeName parameterizedOn(TypeName... params) {
        return new TypeName() {

            @Override
            public TypeName erasure() {
                return TypeName.this;
            }

            @Override
            public String qname() {
                StringBuilder sb = new StringBuilder(TypeName.this.qname()).append('<');
                for (int i = 0; i < params.length; i++) {
                    sb.append(params[i].qname());
                    if (i != params.length - 1) {
                        sb.append(", ");
                    }
                }
                return sb.append('>').toString();
            }

            @Override
            public String simpleName() {
                StringBuilder sb = new StringBuilder(TypeName.this.simpleName()).append('<');
                for (int i = 0; i < params.length; i++) {
                    sb.append(params[i].simpleName());
                    if (i != params.length - 1) {
                        sb.append(", ");
                    }
                }
                return sb.append('>').toString();
            }

            @Override
            public Library origin() {
                return TypeName.this.origin();
            }

            public String toString() {
                StringBuilder sb = new StringBuilder(TypeName.this.toString()).append('<');
                for (int i = 0; i < params.length; i++) {
                    sb.append(params[i].toString());
                    if (i != params.length - 1) {
                        sb.append(", ");
                    }
                }
                return sb.append('>').toString();
            }

            @Override
            public String qnameNotouch() {
                return qname();
            }
        };
    }

    static TypeName fromQualifiedName(String qname) {
        if (qname.indexOf('<') >= 0 || qname.indexOf('>') >= 0) {
            throw new IllegalArgumentException("Not a raw name: " + qname);
        }
        return new TypeName() {
            @Override
            public String qname() {
                return qname;
            }

            @Override
            public String simpleName() {
                return AnnotationUtils.simpleName(qname);
            }

            @Override
            public Library origin() {
                return null;
            }

            @Override
            public String qnameNotouch() {
                return qname();
            }
        };
    }
}
