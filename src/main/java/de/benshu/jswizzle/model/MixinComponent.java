package de.benshu.jswizzle.model;

import com.google.common.collect.ImmutableSet;

public interface MixinComponent {
    TypeDeclaration getMix();

    ImmutableSet<Import> getRequiredImports();

    String getBody();
}
