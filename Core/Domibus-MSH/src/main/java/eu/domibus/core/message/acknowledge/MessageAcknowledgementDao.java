package eu.domibus.core.message.acknowledge;

import eu.domibus.api.model.MSHRole;
import eu.domibus.core.dao.BasicDao;
import eu.domibus.core.message.dictionary.MshRoleDao;
import eu.domibus.core.metrics.Counter;
import eu.domibus.core.metrics.Timer;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 * @author migueti, Cosmin Baciu
 * @since 3.3
 */
@Repository
public class MessageAcknowledgementDao extends BasicDao<MessageAcknowledgementEntity> {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(MessageAcknowledgementDao.class);

    @Autowired
    private MshRoleDao mshRoleDao;

    public MessageAcknowledgementDao() {
        super(MessageAcknowledgementEntity.class);
    }

    public List<MessageAcknowledgementEntity> findByMessageId(String messageId, MSHRole mshRole) {
        try {
            final TypedQuery<MessageAcknowledgementEntity> query = em.createNamedQuery("MessageAcknowledgement.findMessageAcknowledgementByMessageIdAndRole",
                    MessageAcknowledgementEntity.class);
            query.setParameter("MESSAGE_ID", messageId);
            query.setParameter("MSH_ROLE", mshRoleDao.findByRole(mshRole));
            return query.getResultList();
        } catch (NoResultException e) {
            LOG.debug("Could not find any message acknowledge for message id[" + messageId + "]");
            return null;
        }
    }

    @Timer(clazz = MessageAcknowledgementDao.class,value = "deleteMessages.deleteMessageAcknowledgementsByMessageIds")
    @Counter(clazz = MessageAcknowledgementDao.class,value = "deleteMessages.deleteMessageAcknowledgementsByMessageIds")
    public int deleteMessageAcknowledgementsByMessageIds(List<Long> messageIds) {
        final Query deleteQuery = em.createNamedQuery("MessageAcknowledgement.deleteMessageAcknowledgementsByMessageIds");
        deleteQuery.setParameter("IDS", messageIds);
        int result = deleteQuery.executeUpdate();
        LOG.trace("deleteMessageAcknowledgementsByMessageIds result [{}]", result);
        return result;
    }

}
