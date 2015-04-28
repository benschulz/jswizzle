package de.benshu.jswizzle.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static de.benshu.jswizzle.utils.SwizzleCollectors.immutableList;
import static de.benshu.jswizzle.utils.SwizzleCollectors.set;
import static java.util.stream.Collectors.joining;

public class TypeParameters implements Iterable<TypeParameter>, JavaSourceConvertible {
    public static TypeParameters of(TypeElement typeElement) {
        return new TypeParameters(ImmutableList.copyOf(typeElement.getTypeParameters()));
    }

    private final ImmutableList<TypeParameterElement> byOrder;
    private final ImmutableMap<String, TypeParameterElement> byName;

    public TypeParameters(ImmutableList<TypeParameterElement> byOrder) {
        this.byOrder = byOrder;
        this.byName = Maps.uniqueIndex(byOrder, p -> p.getSimpleName().toString());
    }

    public TypeParameters select(ImmutableList<String> names) {
        return new TypeParameters(names.stream().map(byName::get).collect(immutableList()));
    }

    public String asJavaSource(ImmutableSet<AsJavaSourceOptions> options) {
        return byOrder.isEmpty() ? ""
                : "<" + stream().map(p -> p.asJavaSource(options)).collect(joining(", ")) + ">";
    }

    public ImmutableSet<FullyQualifiedName> extractReferencedTypes() {
        return stream()
                .flatMap(p -> p.extractReferencedTypes().stream())
                .collect(set());
    }

    @Override
    public Iterator<TypeParameter> iterator() {
        return byOrder.stream().map(TypeParameter::new).iterator();
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
