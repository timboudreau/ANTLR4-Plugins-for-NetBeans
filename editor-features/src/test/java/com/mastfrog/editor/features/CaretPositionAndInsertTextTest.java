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
package com.mastfrog.editor.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class CaretPositionAndInsertTextTest {

    @Test
    public void testUncomplicated() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse(" : ");
        assertEquals(" : ", cpait.insertText);
        assertEquals(0, cpait.caretBackup);
    }

    @Test
    public void testParse() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse(" : ^;");
        assertEquals(" : ;", cpait.insertText);
        assertEquals(1, cpait.caretBackup);
    }

    @Test
    public void testParse2() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse("hello^ worlds");
        assertEquals("hello worlds", cpait.insertText);
        assertEquals(7, cpait.caretBackup);
    }

    @Test
    public void testMultiCustom() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse("%\\%%hello% worlds");
        assertEquals("%%hello worlds", cpait.insertText);
        assertEquals(7, cpait.caretBackup);
    }

    @Test
    public void testParseCustom() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse("=\\hello= worlds");
        assertEquals("hello worlds", cpait.insertText);
        assertEquals(7, cpait.caretBackup);
    }

    @Test
    public void testMultiple() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse(" ^^:^^ ^;");
        assertEquals(" ^^:^^ ;", cpait.insertText);
        assertEquals(1, cpait.caretBackup);
    }

    @Test
    public void testCustomNotPresent() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse("$\\ :hoog;");
        assertEquals(" :hoog;", cpait.insertText);
        assertEquals(0, cpait.caretBackup);
    }

    @Test
    public void testCustomPresentAtEnd() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse("$\\ :hoog;$");
        assertEquals(" :hoog;", cpait.insertText);
        assertEquals(0, cpait.caretBackup);
    }

    @Test
    public void testCustomPresentNotAtEnd() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse("$\\ :ho$og;");
        assertEquals(" :hoog;", cpait.insertText);
        assertEquals(3, cpait.caretBackup);
    }

    @Test
    public void testCustomChar() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse("$\\ : $;");
        assertEquals(" : ;", cpait.insertText);
        assertEquals(1, cpait.caretBackup);
    }

    @Test
    public void testPathological1() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse("");
        assertEquals("", cpait.insertText);
        assertEquals(0, cpait.caretBackup);
    }

    @Test
    public void testPathological2() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse(" ");
        assertEquals(" ", cpait.insertText);
        assertEquals(0, cpait.caretBackup);
    }

    @Test
    public void testPathological3() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse("^^^^^");
        assertEquals("^^^^", cpait.insertText);
        assertEquals(0, cpait.caretBackup);
    }

    @Test
    public void testPathological4() {
        CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse("^");
        assertEquals("", cpait.insertText, cpait.toString());
        assertEquals(0, cpait.caretBackup, cpait.toString());
    }
}
