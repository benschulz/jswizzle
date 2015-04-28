package de.benshu.jswizzle.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;

public class SwizzleCollectors {
    public static <E extends Map.Entry<? extends K, ? extends V>, K, V> Collector<E, ?, ImmutableMap<K, V>> orderedMap() {
        return orderedMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public static <E, K, V> Collector<E, ?, ImmutableMap<K, V>> orderedMap(
            Function<? super E, ? extends K> keyFunction,
            Function<? super E, ? extends V> valueFunction) {
        return Collector.of(
                ImmutableMap::<K, V>builder,
                (b, e) -> b.put(keyFunction.apply(e), valueFunction.apply(e)),
                (left, right) -> left.putAll(right.build()),
                ImmutableMap.Builder::build);
    }

    public static <E extends Map.Entry<? extends K, ? extends V>, K, V> Collector<E, ?, ImmutableSetMultimap<K, V>> setMultimap() {
        return setMultimap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public static <E, K, V> Collector<E, ?, ImmutableSetMultimap<K, V>> setMultimap(
            Function<? super E, ? extends K> keyFunction,
            Function<? super E, ? extends V> valueFunction) {
        return Collector.of(
                ImmutableSetMultimap::<K, V>builder,
                (b, e) -> b.put(keyFunction.apply(e), valueFunction.apply(e)),
                (left, right) -> left.putAll(right.build()),
                ImmutableSetMultimap.Builder::build,
                Collector.Characteristics.UNORDERED);
    }

    public static <E> Collector<E, ?, ImmutableSet<E>> set() {
        return Collector.of(
                ImmutableSet::<E>builder,
                ImmutableSet.Builder::add,
                (left, right) -> left.addAll(right.build()),
                ImmutableSet.Builder::build,
                Collector.Characteristics.UNORDERED);
    }

    public static <E> Collector<E, ?, ImmutableSet<E>> orderedSet() {
        return Collector.of(
                ImmutableSet::<E>builder,
                ImmutableSet.Builder::add,
                (left, right) -> left.addAll(right.build()),
                ImmutableSet.Builder::build);
    }

    public static <E> Collector<E, ?, ImmutableList<E>> immutableList() {
        return Collector.of(
                ImmutableList::<E>builder,
                ImmutableList.Builder::add,
                (left, right) -> left.addAll(right.build()),
                ImmutableList.Builder::build);
    }
}
