package de.benshu.jswizzle.model;

import com.google.common.base.CaseFormat;

public class Identifier {
    public static Identifier from(CharSequence identifier) {
        return from(identifier, CaseFormat.LOWER_CAMEL);
    }

    public static Identifier from(CharSequence identifier, CaseFormat caseFormat) {
        return new Identifier(caseFormat, identifier.toString());
    }

    private final CaseFormat caseFormat;
    private final String identifier;

    private Identifier(CaseFormat caseFormat, String identifier) {
        this.caseFormat = caseFormat;
        this.identifier = identifier;
    }

    public String getCamelCased() {
        return caseFormat.to(CaseFormat.LOWER_CAMEL, identifier);
    }

    public String getPascalCased() {
        return caseFormat.to(CaseFormat.UPPER_CAMEL, identifier);
    }

    public String getScreamingSnakeCased() {
        return caseFormat.to(CaseFormat.UPPER_UNDERSCORE, identifier);
    }

    @Override
    public int hashCode() {
        return getCamelCased().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof Identifier && ((Identifier) obj).getCamelCased().equals(getCamelCased());
    }
}
