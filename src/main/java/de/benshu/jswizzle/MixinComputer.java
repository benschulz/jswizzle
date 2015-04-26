package de.benshu.jswizzle;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Map;

public abstract class MixinComputer {
    // TODO A more meaningful return type is desparetly needed (Map.Entry sucks, String sucks).
    public abstract Map.Entry<TypeElement, String> computeFor(Element e);
}
