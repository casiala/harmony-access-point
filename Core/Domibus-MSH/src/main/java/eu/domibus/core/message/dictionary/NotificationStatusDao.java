package eu.domibus.core.message.dictionary;

import eu.domibus.api.model.NotificationStatus;
import eu.domibus.api.model.NotificationStatusEntity;
import eu.domibus.api.model.UserMessageLog;
import eu.domibus.api.multitenancy.DomainContextProvider;
import eu.domibus.core.dao.SingleValueDictionaryDao;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.stereotype.Service;

import javax.persistence.TypedQuery;

/**
 * @author Cosmin Baciu
 * @implNote This DAO class works with {@link NotificationStatusEntity}, which is a static dictionary
 * based on the {@link NotificationStatus} enum: no new values are expected to be added at runtime;
 * therefore, {@code NotificationStatusDao} can be used directly, without subclassing {@link AbstractDictionaryService}.
 * @since 5.0
 */
@Service
public class NotificationStatusDao extends SingleValueDictionaryDao<NotificationStatusEntity> {
    @Autowired
    protected DomainContextProvider domainProvider;

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(UserMessageLog.class);

    public NotificationStatusDao() {
        super(NotificationStatusEntity.class);
    }

    public NotificationStatusEntity findOrCreate(NotificationStatus status) {
        if(status == null) {
            return null;
        }

        NotificationStatusEntity messageStatusEntity = findByStatus(status);
        if (messageStatusEntity != null) {
            return messageStatusEntity;
        }
        NotificationStatusEntity entity = new NotificationStatusEntity();
        entity.setStatus(status);
        create(entity);
        return entity;
    }

    public NotificationStatusEntity findByStatus(final NotificationStatus notificationStatus) {
        return getEntity(notificationStatus);
    }

    @Override
    public NotificationStatusEntity findByValue(Object value) {
        return getEntity(value);
    }

    private NotificationStatusEntity getEntity(Object notificationStatus) {
        TypedQuery<NotificationStatusEntity> query = em.createNamedQuery("NotificationStatusEntity.findByStatus", NotificationStatusEntity.class);
        query.setParameter("NOTIFICATION_STATUS", notificationStatus);
        query.setParameter("DOMAIN", domainProvider.getCurrentDomain().getCode());
        return DataAccessUtils.singleResult(query.getResultList());
    }

}
