package eu.domibus.core.message.attempt;

import eu.domibus.api.model.MSHRole;
import eu.domibus.core.dao.BasicDao;
import eu.domibus.core.message.dictionary.MshRoleDao;
import eu.domibus.core.metrics.Counter;
import eu.domibus.core.metrics.Timer;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 * @author Cosmin Baciu
 * @since 3.3
 */
@Repository
public class MessageAttemptDao extends BasicDao<MessageAttemptEntity> {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(MessageAttemptDao.class);

    @Autowired
    private MshRoleDao mshRoleDao;

    public MessageAttemptDao() {
        super(MessageAttemptEntity.class);
    }

    public List<MessageAttemptEntity> findByMessageId(String messageId, MSHRole mshRole) {
        final TypedQuery<MessageAttemptEntity> query = em.createNamedQuery("MessageAttemptEntity.findAttemptsByMessageIdAndRole", MessageAttemptEntity.class);
        query.setParameter("MESSAGE_ID", messageId);
        query.setParameter("MSH_ROLE", mshRoleDao.findByRole(mshRole));
        return query.getResultList();
    }

    @Override
    public void create(MessageAttemptEntity entity) {
        entity.setError(StringUtils.abbreviate(entity.getError(), 255));
        super.create(entity);
    }

    @Timer(clazz = MessageAttemptDao.class, value = "deleteMessages.deleteAttemptsByMessageIds")
    @Counter(clazz = MessageAttemptDao.class, value = "deleteMessages.deleteAttemptsByMessageIds")
    public int deleteAttemptsByMessageIds(List<Long> ids) {
        final Query deleteQuery = em.createNamedQuery("MessageAttemptEntity.deleteAttemptsByMessageIds");
        deleteQuery.setParameter("IDS", ids);
        int result = deleteQuery.executeUpdate();
        LOG.trace("deleteAttemptsByMessageIds result [{}]", result);
        return result;
    }

}
