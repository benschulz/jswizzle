package de.benshu.jswizzle.copyable;

import de.benshu.jswizzle.Swizzle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Swizzle(computer = CopyableComputer.class)
public @interface Copyable {
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.FIELD, ElementType.METHOD})
    public @interface Exclude {}

    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.FIELD, ElementType.METHOD})
    public @interface Include {}
}
