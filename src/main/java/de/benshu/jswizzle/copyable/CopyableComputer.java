package de.benshu.jswizzle.copyable;

import com.google.common.collect.Maps;
import de.benshu.jswizzle.MixinComputer;
import org.kohsuke.MetaInfServices;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Map;

@MetaInfServices(CopyableComputer.class)
public class CopyableComputer extends MixinComputer {
    @Override
    public Map.Entry<TypeElement, String> computeFor(Element e) {
        return Maps.immutableEntry((TypeElement) e, "trololol");
    }
}
