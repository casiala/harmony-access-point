package eu.domibus.api.model;

import eu.domibus.api.cache.CacheConstants;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;

/**
 * @author Cosmin Baciu
 * @since 5.0
 */

@Entity
@Table(name = "TB_D_ROLE")
@NamedQuery(name = "PartyRole.findByValue",
        hints = {
                @QueryHint(name = "org.hibernate.cacheRegion", value = CacheConstants.DICTIONARY_QUERIES),
                @QueryHint(name = "org.hibernate.cacheable", value = "true")},
        // NOTE: the domain parameter is added to the query to ensure hibernate includes the domain in the cache key
        query = "select prop from PartyRole prop where prop.value=:VALUE and :DOMAIN=:DOMAIN")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class PartyRole extends AbstractBaseEntity implements Serializable {

    @Column(name = "ROLE")
    protected String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        PartyRole partyRole = (PartyRole) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(value, partyRole.value)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .appendSuper(super.hashCode())
                .append(value)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("value", value)
                .toString();
    }
}
