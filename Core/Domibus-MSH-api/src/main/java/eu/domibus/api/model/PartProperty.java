package eu.domibus.api.model;

import eu.domibus.api.cache.CacheConstants;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

/**
 * @author Cosmin Baciu
 * @since 5.0
 */
@Entity
@Table(name = "TB_D_PART_PROPERTY")
@NamedQueries({
        @NamedQuery(name = "PartProperty.findByIDs", hints = {
                @QueryHint(name = "org.hibernate.cacheRegion", value = CacheConstants.DICTIONARY_QUERIES), @QueryHint(name = "org.hibernate.cacheable", value = "true")},
                query = "select prop from PartProperty prop where prop.entityId IN :IDS"),
        @NamedQuery(name = "PartProperty.findByNameValueAndType",
                hints = {
                        @QueryHint(name = "org.hibernate.cacheRegion", value = CacheConstants.DICTIONARY_QUERIES),
                        @QueryHint(name = "org.hibernate.cacheable", value = "true")
                },
                // NOTE: the domain parameter is added to the query to ensure hibernate includes the domain in the cache key
                query = "select prop from PartProperty prop where prop.name=:NAME and prop.value=:VALUE and prop.type=:TYPE and :DOMAIN=:DOMAIN"),
        @NamedQuery(name = "PartProperty.findByNameAndValue",
                hints = {
                        @QueryHint(name = "org.hibernate.cacheRegion", value = CacheConstants.DICTIONARY_QUERIES),
                        @QueryHint(name = "org.hibernate.cacheable", value = "true")
                },
                // NOTE: the domain parameter is added to the query to ensure hibernate includes the domain in the cache key
                query = "select prop from PartProperty prop where prop.name=:NAME and prop.value=:VALUE and prop.type is null and :DOMAIN=:DOMAIN"),
})
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class PartProperty extends Property {


}
