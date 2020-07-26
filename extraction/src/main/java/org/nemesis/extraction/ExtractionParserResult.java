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
package org.nemesis.extraction;

/**
 *
 * @author Tim Boudreau
 */
public interface ExtractionParserResult {

    public Extraction extraction();

    /**
     * Convenience method which takes a parser result and if it is an
     * ExtractionParserResult, returns its extraction.
     *
     * @param netbeansParserResult A NetBeans Parser API Parser.Result
     * @since 2.0.50
     * @return An extraction, if any
     */
    static Extraction extraction(Object netbeansParserResult) {
        if (netbeansParserResult instanceof ExtractionParserResult) {
            return ((ExtractionParserResult) netbeansParserResult).extraction();
        }
        return null;
    }

}
