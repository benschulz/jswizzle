/*
 *  (c) tolina GmbH, 2014
 */
package de.benshu.jswizzle.processor;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import de.benshu.jswizzle.MixinComputer;
import de.benshu.jswizzle.Swizzle;
import org.kohsuke.MetaInfServices;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;

@MetaInfServices(Processor.class)
@SupportedAnnotationTypes("*")
public class SwizzleProcessor extends AbstractProcessor {
    @Override
    public final boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnvironment) {
        try {
            if (roundEnvironment.processingOver() || annotations.isEmpty())
                return false;

            final ImmutableSetMultimap<TypeElement, String> mixins = annotations.stream()
                    .filter(a -> a.getAnnotation(Swizzle.class) != null)
                    .map(this::loadAnnotation)
                    .flatMap(a -> compute(roundEnvironment, a))
                    .collect(setMultimap());

            mixins.asMap().entrySet().stream().parallel().forEach(m -> {
                final ImmutableSet<TypeMirror> candidates = m.getKey().getInterfaces().stream()
                        .filter(ErrorType.class::isInstance)
                        .collect(immutableSet());

                if (candidates.size() == 1)
                    generateMixin(m.getKey(), candidates.iterator().next(), ImmutableSet.copyOf(m.getValue()));
            });

            return false;
        } catch (Exception e) {
            final StringWriter stringWriter = new StringWriter();
            final PrintWriter pw = new PrintWriter(stringWriter);
            pw.println(e.getMessage());
            e.printStackTrace(pw);

            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, stringWriter.toString());
            return false;
        }
    }

    private void generateMixin(TypeElement mix, TypeMirror mixin, ImmutableSet<String> mixinComponents) {
        final String mixFqn = mix.getQualifiedName().toString();
        final String mixinLocalName = determineLocalName(mixin);
        final String mixinFqn = (mixFqn.contains(".") ? mixFqn.substring(0, mixFqn.lastIndexOf(".") + 1) : "") + mixinLocalName;

        try {
            final JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(mixinFqn, mix);

            try (Writer w = sourceFile.openWriter()) {
                w.write(joinMixins(mix, mixin, mixinComponents));
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private String joinMixins(TypeElement mix, TypeMirror mixin, ImmutableSet<String> mixinComponents) {
        return "package " + processingEnv.getElementUtils().getPackageOf(mix).getQualifiedName().toString() + ";\n" +
                "interface " + determineLocalName(mixin) + " {}";
    }

    private String determineLocalName(TypeMirror mixin) {
        return processingEnv.getElementUtils().getBinaryName((TypeElement) processingEnv.getTypeUtils().asElement(mixin)).toString();
    }

    private Class<? extends Annotation> loadAnnotation(TypeElement annotation) {
        try {
            return Class.forName(annotation.getQualifiedName().toString()).asSubclass(Annotation.class);
        } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }

    private Stream<Map.Entry<TypeElement, String>> compute(RoundEnvironment roundEnvironment, Class<? extends Annotation> annotation) {
        final Class<? extends MixinComputer> computerClass = annotation.getAnnotation(Swizzle.class).computer();
        final MixinComputer computer = Iterables.getOnlyElement(ServiceLoader.load(computerClass, computerClass.getClassLoader()));

        return roundEnvironment.getElementsAnnotatedWith(annotation).stream().map(computer::computeFor);
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
                ImmutableSetMultimap.Builder::build);
    }

    public static <E> Collector<E, ?, ImmutableSet<E>> immutableSet() {
        return Collector.of(
                ImmutableSet::<E>builder,
                ImmutableSet.Builder::add,
                (left, right) -> left.addAll(right.build()),
                ImmutableSet.Builder::build);
    }
}