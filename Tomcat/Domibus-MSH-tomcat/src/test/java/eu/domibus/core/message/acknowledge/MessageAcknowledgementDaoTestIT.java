package eu.domibus.core.message.acknowledge;

import eu.domibus.test.AbstractIT;
import eu.domibus.api.ebms3.model.Ebms3Messaging;
import eu.domibus.api.ebms3.model.Ebms3SignalMessage;
import eu.domibus.api.model.*;
import eu.domibus.common.MessageDaoTestUtil;
import eu.domibus.core.ebms3.mapper.Ebms3Converter;
import eu.domibus.core.message.MessageLogInfo;
import eu.domibus.core.message.MessageStatusDao;
import eu.domibus.core.message.dictionary.MshRoleDao;
import eu.domibus.core.message.signal.SignalMessageLogBuilder;
import eu.domibus.core.message.signal.SignalMessageLogDao;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Cosmin Baciu
 * @since 3.3
 */
@Transactional
public class MessageAcknowledgementDaoTestIT extends AbstractIT {

    public static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(MessageAcknowledgementDao.class);

    @Autowired
    MessageAcknowledgementDao messageAcknowledgementDao;

    @Autowired
    MessageAcknowledgeConverter messageAcknowledgeConverter;

    @Autowired
    private SignalMessageLogDao signalMessageLogDao;

    @Autowired
    Ebms3MessagingDocumentParser ebms3MessagingDocumentParser;

    @Autowired
    Ebms3Converter ebms3Converter;
    @Autowired
    MessageDaoTestUtil messageDaoTestUtil;
    @Autowired
    MessageStatusDao messageStatusDao;
    @Autowired
    MshRoleDao mshRoleDao;

    @Before
    public void setup() {
        LOG.debug("Setting up");
    }

    @Test
    @Transactional
    public void testSaveMessageAcknowledge() {
        String user = "baciuco";
        String messageId = "123";
        Timestamp acknowledgetTimestamp = new Timestamp(System.currentTimeMillis());
        String from = "C3";
        String to = "C4";

        UserMessageLog msg1 = messageDaoTestUtil.createUserMessageLog(messageId, new Date(), MSHRole.RECEIVING, MessageStatus.RECEIVED);

        MessageAcknowledgementEntity entity = messageAcknowledgeConverter.create(user, msg1.getUserMessage(), acknowledgetTimestamp, from, to);
        messageAcknowledgementDao.create(entity);

        final List<MessageAcknowledgementEntity> retrievedEntityList = this.messageAcknowledgementDao.findByMessageId(messageId, MSHRole.RECEIVING);

        assertNotNull(retrievedEntityList);
        assertEquals(1, retrievedEntityList.size());

        final MessageAcknowledgementEntity retrievedEntity = retrievedEntityList.get(0);
        assertEquals(entity.getEntityId(), retrievedEntity.getEntityId());
        assertEquals(entity.getCreatedBy(), retrievedEntity.getCreatedBy());
        assertEquals(entity.getAcknowledgeDate(), retrievedEntity.getAcknowledgeDate());
        assertEquals(entity.getFrom(), retrievedEntity.getFrom());
        assertEquals(entity.getTo(), retrievedEntity.getTo());

        assertNotNull(entity.getCreationTime());
        assertNotNull(entity.getCreatedBy());
        assertNotNull(entity.getModificationTime());
        assertNotNull(entity.getModifiedBy());

        assertEquals(entity.getCreationTime().getTime(), entity.getModificationTime().getTime());
    }

    @Test
    @Transactional
    public void testSaveMessageAcknowledge_notFound() {
        final List<MessageAcknowledgementEntity> retrievedEntityList = this.messageAcknowledgementDao.findByMessageId("notFound", MSHRole.RECEIVING);

        assertNotNull(retrievedEntityList);
        assertEquals(0, retrievedEntityList.size());

    }

    @Test
    @Transactional
    public void testMessaging() throws Exception {
        Ebms3SignalMessage signalMessage = getSignalMessage();

        Ebms3Messaging ebms3Messaging = getMessaging();
        ebms3Messaging.setSignalMessage(signalMessage);
        ebms3Messaging.getUserMessage().setMessageInfo(signalMessage.getMessageInfo());

        UserMessageLog msg1 = messageDaoTestUtil.createUserMessageLog("messageId", new Date(), MSHRole.RECEIVING, MessageStatus.RECEIVED);

        SignalMessageResult messaging = ebms3Converter.convertFromEbms3(ebms3Messaging);
        SignalMessage signalMessage1 = messaging.getSignalMessage();
        signalMessage1.setUserMessage(msg1.getUserMessage());
        signalMessage1.setMshRole(mshRoleDao.findOrCreate(MSHRole.SENDING));
        // Builds the signal message log
        SignalMessageLogBuilder smlBuilder = SignalMessageLogBuilder.create()
                .setSignalMessage(signalMessage1)
                .setMessageStatus(messageStatusDao.findOrCreate(MessageStatus.SEND_IN_PROGRESS))
                .setMshRole(mshRoleDao.findOrCreate(MSHRole.SENDING));
        // Saves an entry of the signal message log
        SignalMessageLog signalMessageLog = smlBuilder.build();
        signalMessageLogDao.create(signalMessageLog);

        List<MessageLogInfo> allInfoPaged = signalMessageLogDao.findAllInfoPaged(1, 100, null, false, new HashMap<>(), Collections.emptyList());
        System.out.println("results:" + allInfoPaged);
    }

    protected Ebms3SignalMessage getSignalMessage() throws Exception {
        String resource = "dataset/as4/validAS4Response.xml";
        InputStream is = getClass().getClassLoader().getResourceAsStream(resource);
        Ebms3Messaging ebms3Messaging = ebms3MessagingDocumentParser.parseMessaging(is, "eb3");
        Ebms3SignalMessage signalMessage = ebms3Messaging.getSignalMessage();
        return signalMessage;
    }

    protected Ebms3Messaging getMessaging() throws Exception {
        String resource = "dataset/as4/blue2redGoodMessage.xml";

        InputStream is = getClass().getClassLoader().getResourceAsStream(resource);
        return ebms3MessagingDocumentParser.parseMessaging(is, "ns");
    }

}

