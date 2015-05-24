package de.benshu.jswizzle.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public class Reflection {
    public static Reflection reflectionFor(ProcessingEnvironment processingEnvironment) {
        return new Reflection(processingEnvironment);
    }

    private final ProcessingEnvironment processingEnvironment;

    private Reflection(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
    }

    public ProcessingEnvironment getProcessingEnvironment() {
        return processingEnvironment;
    }

    public Type of(TypeMirror typeMirror) {
        return new Type(this, typeMirror);
    }

    public TypeDeclaration of(TypeElement typeElement) {
        return new TypeDeclaration(this, typeElement, of(typeElement.asType()), substitutions().of(typeElement.getTypeParameters()).throughVariables());
    }

    public LocalVariableDeclaration of(VariableElement variableElement) {
        return new LocalVariableDeclaration(this, variableElement, Substitutions.none());
    }

    public TypeParameters ofTypeParametersOf(TypeElement typeElement) {
        return ofTypeParametersOf(typeElement, Substitutions.none());
    }

    TypeParameters ofTypeParametersOf(TypeElement typeElement, Substitutions substitutions) {
        return new TypeParameters(this, ImmutableList.copyOf(typeElement.getTypeParameters()), substitutions);
    }

    public TypeParameters ofTypeParametersOf(ExecutableElement executableElement) {
        final Substitutions substitutions = Substitutions.none();
        return ofTypeParametersOf(executableElement, substitutions);
    }

    TypeParameters ofTypeParametersOf(ExecutableElement executableElement, Substitutions substitutions) {
        return new TypeParameters(this, ImmutableList.copyOf(executableElement.getTypeParameters()), substitutions);
    }

    public Substitutions.InParametersPreparation substitutions() {
        return new Substitutions.InParametersPreparation(this);
    }
}
