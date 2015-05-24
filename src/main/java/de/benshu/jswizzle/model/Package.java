package de.benshu.jswizzle.model;

import javax.lang.model.element.PackageElement;

public class Package {
    private final Reflection reflection;
    private final PackageElement pakkage;

    public Package(Reflection reflection, PackageElement pakkage) {
        this.reflection = reflection;
        this.pakkage = pakkage;
    }

    public FullyQualifiedName getQualifiedName() {
        return FullyQualifiedName.create(pakkage.getQualifiedName().toString().split("\\."));
    }
}
