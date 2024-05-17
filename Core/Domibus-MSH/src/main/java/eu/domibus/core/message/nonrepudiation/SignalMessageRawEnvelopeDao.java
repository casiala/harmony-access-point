package eu.domibus.core.message.nonrepudiation;

import eu.domibus.api.model.MSHRole;
import eu.domibus.api.model.RawEnvelopeDto;
import eu.domibus.api.model.SignalMessageRaw;
import eu.domibus.core.dao.BasicDao;
import eu.domibus.core.message.dictionary.MshRoleDao;
import eu.domibus.core.metrics.Counter;
import eu.domibus.core.metrics.Timer;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.stereotype.Repository;

import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 * @author Cosmin Baciu
 * @since 5.0
 */
@Repository
public class SignalMessageRawEnvelopeDao extends BasicDao<SignalMessageRaw> {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(SignalMessageRawEnvelopeDao.class);

    @Autowired
    private MshRoleDao mshRoleDao;

    public SignalMessageRawEnvelopeDao() {
        super(SignalMessageRaw.class);
    }

    public RawEnvelopeDto findSignalMessageByUserMessageId(final String userMessageId, MSHRole mshRole) {
        TypedQuery<RawEnvelopeDto> namedQuery = em.createNamedQuery("SignalMessageRaw.findByUserMessageId", RawEnvelopeDto.class);
        namedQuery.setParameter("MESSAGE_ID", userMessageId);
        namedQuery.setParameter("MSH_ROLE", mshRoleDao.findByRole(mshRole));
        return DataAccessUtils.singleResult(namedQuery.getResultList());
    }

    @Timer(clazz = SignalMessageRawEnvelopeDao.class, value = "deleteMessages")
    @Counter(clazz = SignalMessageRawEnvelopeDao.class, value = "deleteMessages")
    public int deleteMessages(List<Long> ids) {
        LOG.debug("deleteMessages [{}]", ids.size());
        final Query deleteQuery = em.createNamedQuery("SignalMessageRaw.deleteMessages");
        deleteQuery.setParameter("IDS", ids);
        int result = deleteQuery.executeUpdate();
        LOG.debug("deleteMessages result [{}]", result);
        return result;
    }
}
