/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.registration;

/**
 *
 * @author Tim Boudreau
 */
 enum FontStyle {
    BOLD, ITALIC;

    public String toString() {
        return "<font style=\"" + name().toLowerCase() + "\"/>";
    }

}
