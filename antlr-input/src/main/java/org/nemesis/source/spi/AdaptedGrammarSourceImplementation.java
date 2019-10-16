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
package org.nemesis.source.spi;

import java.io.IOException;
import java.util.function.Function;
import org.antlr.v4.runtime.CharStream;

/**
 *
 * @author Tim Boudreau
 */
final class AdaptedGrammarSourceImplementation<F, T> extends GrammarSourceImplementation<T> {

    private final Function<? super F, ? extends T> convertTo;
    private final GrammarSourceImplementation<F> toAdapt;

    AdaptedGrammarSourceImplementation(Function<? super F, ? extends T> convertTo, GrammarSourceImplementation<F> toAdapt, Class<T> type) {
        super(type);
        this.convertTo = convertTo;
        this.toAdapt = toAdapt;
    }

    @Override
    protected <R> R lookupImpl(Class<R> type) {
        if (type() == type) {
            F origSrc = toAdapt.source();
            return type.cast(convertTo.apply(origSrc));
        } else if (type() == toAdapt.type()) {
            return type.cast(toAdapt.source());
        }
        return super.lookupImpl(type);
    }

    @Override
    public String toString() {
        return "Adapter<" + type.getSimpleName() + ">{" + toAdapt + "}";
    }

    @Override
    public long lastModified() throws IOException {
        return toAdapt.lastModified();
    }

    @Override
    public String name() {
        return toAdapt.name();
    }

    @Override
    public CharStream stream() throws IOException {
        return toAdapt.stream();
    }

    @Override
    public GrammarSourceImplementation<?> resolveImport(String name) {
        return toAdapt.resolveImport(name);
    }

    @Override
    public T source() {
        return convertTo.apply(toAdapt.source());
    }

}
