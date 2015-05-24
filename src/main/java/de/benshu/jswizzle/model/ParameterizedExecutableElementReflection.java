package de.benshu.jswizzle.model;

import com.google.common.collect.ImmutableList;

public interface ParameterizedExecutableElementReflection extends ExecutableElementReflection {
    ImmutableList<LocalVariableDeclaration> getParameters();
}
