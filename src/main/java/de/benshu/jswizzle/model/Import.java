package de.benshu.jswizzle.model;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

public abstract class Import implements CharSequence, JavaSourceConvertible {
    private final String importString;

    Import(String importString) {
        this.importString = importString;
    }

    @Override
    public int length() {
        return importString.length();
    }

    @Override
    public char charAt(int index) {
        return importString.charAt(index);
    }

    @Override
    public String toString() {
        return importString;
    }

    @Override
    public int hashCode() {
        return importString.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Import && ((Import) obj).importString.equals(importString);
    }

    @Override
    public String asJavaSource(ImmutableSet<AsJavaSourceOptions> options) {
        return "import " + importString + ";";
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return importString.subSequence(start, end);
    }


    public static Import ofPackage(FullyQualifiedName fqn) {
        return new Import(FluentIterable.from(fqn).append("*").join(Joiner.on("."))) {};
    }

    public static Import of(FullyQualifiedName fqn) {
        return new Import(FluentIterable.from(fqn).join(Joiner.on("."))) {};
    }
}
