package de.benshu.jswizzle.model;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public interface JavaSourceConvertible {
    default String asJavaSource() {
        return asJavaSource(ImmutableSet.of());
    }

    default String asJavaSource(AsJavaSourceOptions option, AsJavaSourceOptions... furtherOptions) {
        return asJavaSource(Sets.immutableEnumSet(option, furtherOptions));
    }

    String asJavaSource(ImmutableSet<AsJavaSourceOptions> options);
}
