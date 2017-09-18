package com.vortex.compiler.data;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 06/10/2016
 */
public class Dependence {
    public String name;
    private int[] minVersion;

    public Dependence(String name) {
        this.name = name;
        minVersion = new int[0];
    }

    public Dependence(String name, int[] minVersion) {
        this.name = name;
        this.minVersion = minVersion;
    }

    public boolean isValid(Library library) {
        if (library.name.equals(name) && library.version != null) {
            for (int i = 0; i < Math.min(library.version.length, minVersion.length); i++) {
                if (minVersion[i] > library.version[i]) {
                    return false;
                } else if (minVersion[i] < library.version[i]) {
                    return true;
                }
            }
            return library.version.length > minVersion.length;
        }
        return false;
    }
}
