package de.benshu.jswizzle.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import javax.lang.model.element.TypeParameterElement;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static de.benshu.commons.core.streams.Collectors.list;
import static java.util.stream.Collectors.joining;

public class TypeParameters implements Iterable<TypeParameter>, JavaSourceConvertible {
    private final Reflection reflection;
    private final ImmutableList<TypeParameterElement> byOrder;
    private final ImmutableMap<String, TypeParameterElement> byName;
    private final Substitutions substitutions;

    TypeParameters(Reflection reflection, ImmutableList<TypeParameterElement> byOrder, Substitutions substitutions) {
        this.reflection = reflection;
        this.byOrder = byOrder;
        this.byName = Maps.uniqueIndex(byOrder, p -> p.getSimpleName().toString());
        this.substitutions = substitutions;
    }

    public TypeParameter get(int index) {
        return new TypeParameter(reflection, byOrder.get(index), substitutions);
    }

    public int size() {
        return byOrder.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public TypeParameters select(ImmutableList<String> names) {
        return new TypeParameters(reflection, names.stream().map(byName::get).collect(list()), substitutions);
    }

    public String asJavaSource(ImmutableSet<AsJavaSourceOptions> options) {
        return byOrder.isEmpty() ? ""
                : "<" + stream().map(p -> p.asJavaSource(options)).collect(joining(", ")) + ">";
    }

    public Stream<FullyQualifiedName> referencedTypes() {
        return stream().flatMap(TypeParameter::referencedTypes);
    }

    @Override
    public Iterator<TypeParameter> iterator() {
        return byOrder.stream().map(p -> new TypeParameter(reflection, p, substitutions)).iterator();
    }

    @Override
    public Spliterator<TypeParameter> spliterator() {
        return Spliterators.spliterator(iterator(), byOrder.size(), Spliterator.ORDERED);
    }

    public Stream<TypeParameter> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    public Stream<TypeParameter> parallelStream() {
        return StreamSupport.stream(spliterator(), true);
    }
}
