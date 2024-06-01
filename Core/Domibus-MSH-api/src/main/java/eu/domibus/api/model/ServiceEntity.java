package eu.domibus.api.model;

import eu.domibus.api.cache.CacheConstants;
import eu.domibus.api.ebms3.Ebms3Constants;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

/**
 * @author Cosmin Baciu
 * @since 5.0
 */
@Entity
@Table(name = "TB_D_SERVICE")
@NamedQueries({
        @NamedQuery(name = "Service.findByValueAndType",
                hints = {
                        @QueryHint(name = "org.hibernate.cacheRegion", value = CacheConstants.DICTIONARY_QUERIES),
                        @QueryHint(name = "org.hibernate.cacheable", value = "true")},
                // NOTE: the domain parameter is added to the query to ensure hibernate includes the domain in the cache key
                query = "select serv from ServiceEntity serv where serv.value=:VALUE and serv.type=:TYPE and :DOMAIN=:DOMAIN"),
        @NamedQuery(name = "Service.findByValue",
                hints = {
                        @QueryHint(name = "org.hibernate.cacheRegion", value = CacheConstants.DICTIONARY_QUERIES),
                        @QueryHint(name = "org.hibernate.cacheable", value = "true")},
                // NOTE: the domain parameter is added to the query to ensure hibernate includes the domain in the cache key
                query = "select serv from ServiceEntity serv where serv.value=:VALUE and serv.type is null and :DOMAIN=:DOMAIN"),
        @NamedQuery(name = "Service.searchByType",
                hints = {
                        @QueryHint(name = "org.hibernate.cacheRegion", value = CacheConstants.DICTIONARY_QUERIES),
                        @QueryHint(name = "org.hibernate.cacheable", value = "true")},
                // NOTE: the domain parameter is added to the query to ensure hibernate includes the domain in the cache key
                query = "select serv from ServiceEntity serv where serv.type=:TYPE and :DOMAIN=:DOMAIN"),
        @NamedQuery(name = "Service.searchByValue",
                hints = {
                        @QueryHint(name = "org.hibernate.cacheRegion", value = CacheConstants.DICTIONARY_QUERIES),
                        @QueryHint(name = "org.hibernate.cacheable", value = "true")},
                // NOTE: the domain parameter is added to the query to ensure hibernate includes the domain in the cache key
                query = "select serv from ServiceEntity serv where serv.value=:VALUE and :DOMAIN=:DOMAIN")
})
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ServiceEntity extends AbstractBaseEntity {

    @Column(name = "VALUE", unique = true)
    protected String value = Ebms3Constants.TEST_SERVICE;

    @Column(name = "TYPE")
    protected String type;

    public String getValue() {
        return this.value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public String getType() {
        return this.type;
    }

    public void setType(final String value) {
        this.type = value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceEntity)) return false;

        final ServiceEntity service = (ServiceEntity) o;

        if (this.type != null ? !this.type.equalsIgnoreCase(service.type) : service.type != null) return false;
        return this.value.equalsIgnoreCase(service.value);

    }

    @Override
    public int hashCode() {
        int result = this.value.hashCode();
        result = 31 * result + (this.type != null ? this.type.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("value", value)
                .append("type", type)
                .toString();
    }
}
