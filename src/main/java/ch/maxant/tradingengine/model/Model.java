package ch.maxant.tradingengine.model;

import java.util.Collection;
import java.util.Collections;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public abstract class Model {

    @Override
    public boolean equals(Object o) {
	return EqualsBuilder.reflectionEquals(this, o, getIgnoredFields());
    }

    protected Collection<String> getIgnoredFields() {
	return Collections.emptySet();
    }

    @Override
    public int hashCode() {
	return HashCodeBuilder.reflectionHashCode(this, getIgnoredFields());
    }

    @Override
    public String toString() {
	return ToStringBuilder.reflectionToString(this,
		ToStringStyle.SHORT_PREFIX_STYLE);
    }

}
