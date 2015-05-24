package de.benshu.jswizzle;

import de.benshu.jswizzle.model.MixinComponent;
import de.benshu.jswizzle.model.Reflection;

import javax.lang.model.element.Element;

public abstract class MixinComputer {
    public abstract MixinComponent computeFor(Reflection reflection, Element e);
}
