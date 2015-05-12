package de.benshu.jswizzle.data;

import de.benshu.jswizzle.Swizzle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.FIELD})
@Swizzle(computer = DataComputer.class)
public @interface Data {
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.FIELD, ElementType.METHOD})
    public @interface Exclude {}
}
