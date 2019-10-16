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
package org.nemesis.extraction.key;

/**
 * Base interface for extraction key.  Each data structure that can be extracted
 * allows the extractor to associate some data with each extracted element.  The
 * type provided by this interface is what allows type-safe lookup of data, returning
 * the exact type expected.
 *
 * @author Tim Boudreau
 */
public interface ExtractionKey<T> {

    Class<T> type();

    String name();

}
