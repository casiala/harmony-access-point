package eu.domibus.core.message;

import eu.domibus.test.AbstractIT;
import eu.domibus.api.ebms3.Ebms3Constants;
import eu.domibus.api.model.*;
import eu.domibus.common.MessageDaoTestUtil;
import eu.domibus.core.message.dictionary.*;
import eu.domibus.core.message.signal.SignalMessageDao;
import eu.domibus.test.common.MessageTestUtility;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static eu.domibus.common.model.configuration.Identifier_.PARTY_ID_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Transactional
public class UserMessageDaoTestIT extends AbstractIT {

    private static final String STRING_TYPE = "string";

    @Autowired
    UserMessageDao userMessageDao;

    @Autowired
    SignalMessageDao signalMessageDao;

    @Autowired
    protected MpcDao mpcDao;

    @Autowired
    protected MshRoleDao mshRoleDao;

    @Autowired
    protected PartyIdDao partyIdDao;

    @Autowired
    protected PartyRoleDao partyRoleDao;

    @Autowired
    protected ActionDao actionDao;

    @Autowired
    protected ServiceDao serviceDao;

    @Autowired
    protected AgreementDao agreementDao;

    @Autowired
    MessagePropertyDao propertyDao;

    @Autowired
    MessageDaoTestUtil messageDaoTestUtil;

    @Test
    @Transactional
    public void testSaveUserMessage() {
        final MessageTestUtility messageTestUtility = new MessageTestUtility();
        final UserMessage userMessage = messageTestUtility.createSampleUserMessage();

        MessageProperty messageProperty1 = propertyDao.findOrCreateProperty("originalSender", "urn:oasis:names:tc:ebcore:partyid-type:unregistered:C1", STRING_TYPE);
        MessageProperty messageProperty2 = propertyDao.findOrCreateProperty("finalRecipient", "urn:oasis:names:tc:ebcore:partyid-type:unregistered:C4", STRING_TYPE);
        userMessage.setMessageProperties(new HashSet<>(Arrays.asList(messageProperty1, messageProperty2)));

        PartyId senderPartyId = messageTestUtility.createSenderPartyId();
        partyIdDao.create(senderPartyId);
        userMessage.getPartyInfo().getFrom().setFromPartyId(senderPartyId);

        final PartyRole senderPartyRole = messageTestUtility.createSenderPartyRole();
        partyRoleDao.create(senderPartyRole);
        userMessage.getPartyInfo().getFrom().setFromRole(senderPartyRole);

        final PartyId receiverPartyId = messageTestUtility.createReceiverPartyId();
        partyIdDao.create(receiverPartyId);
        userMessage.getPartyInfo().getTo().setToPartyId(receiverPartyId);

        final PartyRole receiverPartyRole = messageTestUtility.createReceiverPartyRole();
        partyRoleDao.create(receiverPartyRole);
        userMessage.getPartyInfo().getTo().setToRole(receiverPartyRole);

        final ActionEntity actionEntity = messageTestUtility.createActionEntity();
        actionDao.create(actionEntity);
        userMessage.setAction(actionEntity);

        final ServiceEntity serviceEntity = messageTestUtility.createServiceEntity();
        serviceDao.create(serviceEntity);
        userMessage.setService(serviceEntity);

        final AgreementRefEntity agreementRefEntity = messageTestUtility.createAgreementRefEntity();
        agreementDao.create(agreementRefEntity);
        userMessage.setAgreementRef(agreementRefEntity);

        final MpcEntity mpcEntity = messageTestUtility.createMpcEntity();
        mpcDao.create(mpcEntity);
        userMessage.setMpc(mpcEntity);

        userMessageDao.create(userMessage);

        final UserMessage dbUserMessage = userMessageDao.findByEntityId(userMessage.getEntityId());
        assertNotNull(dbUserMessage);
        final Set<MessageProperty> msgProperties = dbUserMessage.getMessageProperties();
        msgProperties.forEach(messageProperty -> assertNotNull(messageProperty.getValue()));

        assertNotNull( userMessage.getPartyInfo().getFrom().getFromRole().getValue());
    }

    @Test
    @Transactional
    public void testFindLastTestMessageId() {
        UserMessageLog testMessageOlder = messageDaoTestUtil.createTestMessage(this.getClass().getName() + "msg-test-0");
        String msgId = this.getClass().getName() + "msg-test-1";
        UserMessageLog testMessage = messageDaoTestUtil.createTestMessage(msgId);

        String testParty = testMessage.getUserMessage().getPartyInfo().getToParty(); // "domibus-red"
        String senderPartyId = "domibus-blue";
        String type = "urn:oasis:names:tc:ebcore:partyid-type:unregistered";
        PartyId fromPartyId = partyIdDao.findExistingPartyId(senderPartyId, type);
        PartyId toPartyId = partyIdDao.findExistingPartyId(testParty, type);
        UserMessage userMessage = userMessageDao.findLastTestMessageFromPartyToParty(fromPartyId, toPartyId);
        assertNotNull(userMessage);
        assertEquals(msgId, userMessage.getMessageId());

        SignalMessage signalMessage = signalMessageDao.findLastTestMessage(fromPartyId, toPartyId);
        assertNotNull(signalMessage);
        assertEquals(msgId, signalMessage.getRefToMessageId());
        assertEquals(msgId, signalMessage.getUserMessage().getMessageId());

        messageDaoTestUtil.deleteMessages(Arrays.asList(testMessageOlder.getEntityId(), testMessage.getEntityId()));
    }

}
