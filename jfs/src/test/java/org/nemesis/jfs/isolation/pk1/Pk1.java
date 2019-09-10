/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.nemesis.jfs.isolation.pk1;

import org.nemesis.jfs.isolation.pk1.pk1a.Pk1a;

/**
 *
 * @author Tim Boudreau
 */
public class Pk1 {

    private final Pk1a pk1a;

    public Pk1() {
        pk1a = new Pk1a("Stuff");
    }
}
