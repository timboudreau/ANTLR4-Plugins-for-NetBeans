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
package org.nemesis.swing.cell;

import java.awt.Rectangle;
import java.awt.event.ComponentListener;
import java.awt.event.ContainerListener;
import java.awt.event.FocusListener;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyListener;
import java.awt.event.InputMethodListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeListener;
import java.beans.VetoableChangeListener;
import javax.swing.event.AncestorListener;

/**
 * Just overrides a bunch of stuff to do nothing for performance as a cell
 * renderer.  Use inside a ListCellRenderer or whatever.
 *
 * @author Tim Boudreau
 */
public class TextCellCellRenderer extends TextCellLabel {

    protected final boolean isCellRenderer() {
        return true;
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    @Override
    public void doLayout() {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    @Override
    public void invalidate() {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    @Override
    public void revalidate() {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    @Override
    public void repaint(Rectangle r) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    @Override
    public void repaint(long tm, int x, int y, int width, int height) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    @Override
    public void repaint(int x, int y, int width, int height) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    @Override
    public void repaint(long tm) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    @Override
    public void repaint() {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    public @Override
    void addAncestorListener(AncestorListener l) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    public @Override
    void addComponentListener(ComponentListener l) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    public @Override
    void addContainerListener(ContainerListener l) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    public @Override
    void addHierarchyListener(HierarchyListener l) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    public @Override
    void addHierarchyBoundsListener(HierarchyBoundsListener l) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    public @Override
    void addInputMethodListener(InputMethodListener l) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    public @Override
    void addFocusListener(FocusListener fl) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    public @Override
    void addMouseListener(MouseListener ml) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    public @Override
    void addMouseWheelListener(MouseWheelListener ml) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    public @Override
    void addMouseMotionListener(MouseMotionListener ml) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons
     */
    public @Override
    void addVetoableChangeListener(VetoableChangeListener vl) {
        // do nothing
    }

    /**
     * Overridden to do nothing for performance reasons, unless using standard
     * swing rendering
     */
    public @Override
    void addPropertyChangeListener(String s, PropertyChangeListener l) {
        // do nothing
    }

    public @Override
    void addPropertyChangeListener(PropertyChangeListener l) {
        // do nothing
    }

}
