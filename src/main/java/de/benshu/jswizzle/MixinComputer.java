package de.benshu.jswizzle;

import de.benshu.jswizzle.model.MixinComponent;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;

public abstract class MixinComputer {
    public abstract MixinComponent computeFor(ProcessingEnvironment processingEnvironment, Element e);
}
