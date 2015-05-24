package de.benshu.jswizzle.model;

import com.google.common.collect.ImmutableSet;

import javax.lang.model.type.DeclaredType;

public class Mixin {
    private final DeclaredType reference;
    private final Identifier name;
    private final TypeDeclaration mix;
    private final ImmutableSet<MixinComponent> components;

    public Mixin(DeclaredType reference, Identifier name, TypeDeclaration mix, ImmutableSet<MixinComponent> components) {
        this.reference = reference;
        this.name = name;
        this.mix = mix;
        this.components = components;
    }

    public DeclaredType getReference() {
        return reference;
    }

    public Identifier getName() {
        return name;
    }

    public TypeDeclaration getMix() {
        return mix;
    }

    public ImmutableSet<MixinComponent> getComponents() {
        return components;
    }
}
