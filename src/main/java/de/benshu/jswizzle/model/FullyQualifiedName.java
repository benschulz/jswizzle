package de.benshu.jswizzle.model;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import javax.lang.model.element.TypeElement;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class FullyQualifiedName implements Iterable<String>, JavaSourceConvertible {
    public static FullyQualifiedName of(TypeElement element) {
        return create(element.getQualifiedName().toString().split("\\."));
    }

    public static FullyQualifiedName create(String... names) {
        return create(ImmutableList.copyOf(names));
    }

    private static FullyQualifiedName create(ImmutableList<String> names) {
        return new FullyQualifiedName(names);
    }

    private final ImmutableList<String> names;

    public FullyQualifiedName(ImmutableList<String> names) {
        this.names = names;
    }

    public String asJavaSource(ImmutableSet<AsJavaSourceOptions> options) {
        return options.contains(AsJavaSourceOptions.SIMPLE_NAMES)
                ? names.get(names.size() - 1)
                : Joiner.on(".").join(names);
    }

    @Override
    public Iterator<String> iterator() {
        return names.iterator();
    }

    @Override
    public Spliterator<String> spliterator() {
        return Spliterators.spliterator(iterator(), names.size(), Spliterator.ORDERED);
    }

    public Stream<String> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    public Stream<String> parallelStream() {
        return StreamSupport.stream(spliterator(), true);
    }

    @Override
    public String toString() {
        return asJavaSource();
    }
}
