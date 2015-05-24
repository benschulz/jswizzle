package de.benshu.jswizzle.model;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import javax.lang.model.element.Modifier;

public interface HasModifiersMixin extends ElementReflection {
    default ImmutableSet<Modifier> getModifiers() {
        return Sets.immutableEnumSet(getMirror().getModifiers());
    }

    default boolean isAbstract() {
        return getModifiers().contains(Modifier.ABSTRACT);
    }

    default boolean isPrivate() {
        return getModifiers().contains(Modifier.PRIVATE);
    }

    default boolean isProtected() {
        return getModifiers().contains(Modifier.PROTECTED);
    }

    default boolean isPublic() {
        return getModifiers().contains(Modifier.PUBLIC);
    }
}
