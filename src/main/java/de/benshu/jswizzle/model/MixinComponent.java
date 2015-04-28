package de.benshu.jswizzle.model;

import com.google.common.collect.ImmutableSet;

import javax.lang.model.element.TypeElement;

public interface MixinComponent {
    TypeElement getMix();

    ImmutableSet<Import> getRequiredImports();

    String getBody();
}
