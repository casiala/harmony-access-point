package eu.domibus.api.model;

import eu.domibus.api.cache.CacheConstants;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

/**
 * @author Cosmin Baciu
 * @since 5.0
 */
@Entity
@Table(name = "TB_D_AGREEMENT")
@NamedQueries({
        @NamedQuery(name = "AgreementRef.findByValueAndType",
                hints = {
                        @QueryHint(name = "org.hibernate.cacheRegion", value = CacheConstants.DICTIONARY_QUERIES),
                        @QueryHint(name = "org.hibernate.cacheable", value = "true")
                },
                // NOTE: the domain parameter is added to the query to ensure hibernate includes the domain in the cache key
                query = "select serv from AgreementRefEntity serv where serv.value=:VALUE and serv.type=:TYPE and :DOMAIN=:DOMAIN"),
        @NamedQuery(name = "AgreementRef.findByValue",
                hints = {
                        @QueryHint(name = "org.hibernate.cacheRegion", value = CacheConstants.DICTIONARY_QUERIES),
                        @QueryHint(name = "org.hibernate.cacheable", value = "true")
                },
                // NOTE: the domain parameter is added to the query to ensure hibernate includes the domain in the cache key
                query = "select serv from AgreementRefEntity serv where serv.value=:VALUE and serv.type is null and :DOMAIN=:DOMAIN")
})
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class AgreementRefEntity extends AbstractBaseEntity {

    @Column(name = "VALUE", unique = true)
    protected String value;

    @Column(name = "TYPE")
    protected String type;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        AgreementRefEntity that = (AgreementRefEntity) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(value, that.value)
                .append(type, that.type)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .appendSuper(super.hashCode())
                .append(value)
                .append(type)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("value", value)
                .append("type", type)
                .toString();
    }
}
