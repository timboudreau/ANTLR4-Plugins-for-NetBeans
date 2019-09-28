/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
*/


package org.nemesis.antlr.language.formatting.ui;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

/**
 *
 * @author Tim Boudreau
 */
final class MockPreferences extends Preferences {

    @Override
    public void put(String key, String value) {
        doPut(key, value);
    }

    @Override
    public String get(String key, String def) {
        return map.getOrDefault(key, def);
    }

    @Override
    public void remove(String key) {
        map.remove(key);
    }

    @Override
    public void clear() throws BackingStoreException {
        map.clear();
    }

    @Override
    public void putInt(String key, int value) {
        doPut(key, Integer.toString(value));
    }

    @Override
    public int getInt(String key, int def) {
        String s = map.get(key);
        return s == null ? def : Integer.parseInt(s);
    }

    @Override
    public void putLong(String key, long value) {
        doPut(key, Long.toString(value));
    }

    @Override
    public long getLong(String key, long def) {
        String s = map.get(key);
        return s == null ? def : Long.parseLong(s);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        doPut(key, Boolean.toString(value));
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        String s = map.get(key);
        return s == null ? def : "true".equals(s);
    }

    @Override
    public void putFloat(String key, float value) {
        doPut(key, Float.toString(value));
    }

    @Override
    public float getFloat(String key, float def) {
        String s = map.get(key);
        return s == null ? def : Float.parseFloat(s);
    }

    @Override
    public void putDouble(String key, double value) {
        doPut(key, Double.toString(value));
    }

    @Override
    public double getDouble(String key, double def) {
        String v = map.get(key);
        return v == null ? def : Double.parseDouble(v);
    }

    @Override
    public void putByteArray(String key, byte[] value) {
        doPut(key, Base64.getUrlEncoder().encodeToString(value));
    }

    @Override
    public byte[] getByteArray(String key, byte[] def) {
        String val = map.get(key);
        return val == null ? def : Base64.getUrlDecoder().decode(val);
    }

    @Override
    public String[] keys() throws BackingStoreException {
        return map.keySet().toArray(new String[0]);
    }

    @Override
    public String[] childrenNames() throws BackingStoreException {
        return new String[0];
    }

    @Override
    public Preferences parent() {
        return null;
    }

    @Override
    public Preferences node(String pathName) {
        return this;
    }

    @Override
    public boolean nodeExists(String pathName) throws BackingStoreException {
        return pathName.isEmpty();
    }

    @Override
    public void removeNode() throws BackingStoreException {
    }

    @Override
    public String name() {
        return "";
    }

    @Override
    public String absolutePath() {
        return "";
    }

    @Override
    public boolean isUserNode() {
        return true;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public void flush() throws BackingStoreException {
    }

    @Override
    public void sync() throws BackingStoreException {
    }
    private final Map<String, String> map = new HashMap<>();

    private void doPut(String key, String val) {
        String old = map.put(key, val);
        if (!Objects.equals(old, val)) {
            PreferenceChangeEvent pce = new PreferenceChangeEvent(this, key, val);
            for (PreferenceChangeListener l : listeners) {
                l.preferenceChange(pce);
            }
        }
    }
    private final List<PreferenceChangeListener> listeners = new ArrayList<>();

    @Override
    public void addPreferenceChangeListener(PreferenceChangeListener pcl) {
        listeners.add(pcl);
    }

    @Override
    public void removePreferenceChangeListener(PreferenceChangeListener pcl) {
        listeners.remove(pcl);
    }

    @Override
    public void addNodeChangeListener(NodeChangeListener ncl) {
    }

    @Override
    public void removeNodeChangeListener(NodeChangeListener ncl) {
    }

    @Override
    public void exportNode(OutputStream os) throws IOException, BackingStoreException {
    }

    @Override
    public void exportSubtree(OutputStream os) throws IOException, BackingStoreException {
    }

}
