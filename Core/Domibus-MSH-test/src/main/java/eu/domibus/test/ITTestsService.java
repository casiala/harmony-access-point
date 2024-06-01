package eu.domibus.test;

import eu.domibus.api.model.*;
import eu.domibus.common.model.configuration.Mpc;
import eu.domibus.common.model.configuration.ReceptionAwareness;
import eu.domibus.core.ebms3.receiver.MSHWebservice;
import eu.domibus.core.message.UserMessageDao;
import eu.domibus.core.message.UserMessageDefaultService;
import eu.domibus.core.message.UserMessageDefaultServiceHelper;
import eu.domibus.core.message.UserMessageLogDao;
import eu.domibus.core.message.dictionary.*;
import eu.domibus.core.plugin.handler.MessageSubmitterImpl;
import eu.domibus.core.pmode.multitenancy.MultiDomainPModeProvider;
import eu.domibus.core.pmode.provider.CachingPModeProvider;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.messaging.MessagingProcessingException;
import eu.domibus.plugin.Submission;
import eu.domibus.test.common.MessageTestUtility;
import eu.domibus.test.common.SoapSampleUtil;
import eu.domibus.test.common.SubmissionUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.soap.SOAPMessage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * @author François Gautier
 * @since 5.0
 */
@Service
public class ITTestsService {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(ITTestsService.class);

    @Autowired
    protected UserMessageLogDao userMessageLogDao;

    @Autowired
    protected UserMessageDefaultService userMessageDefaultService;

    @Autowired
    protected UserMessageDefaultServiceHelper userMessageDefaultServiceHelper;

    @Autowired
    protected SubmissionUtil submissionUtil;

    @Autowired
    protected MessageSubmitterImpl messageSubmitter;

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
    protected UserMessageDao userMessageDao;

    @Autowired
    protected MessagePropertyDao messagePropertyDao;

    @Autowired
    SoapSampleUtil soapSampleUtil;

    @Autowired
    MSHWebservice mshWebserviceTest;

    @Autowired
    MultiDomainPModeProvider pModeProvider;

    @Transactional
    public String sendMessageWithStatus(MessageStatus endStatus) throws MessagingProcessingException {

        UserMessageLog userMessageLog = sendMessageWithStatus(endStatus, null);
        return userMessageLog.getUserMessage().getMessageId();
    }

    @Transactional
    public UserMessageLog sendMessageWithStatus(MessageStatus endStatus, String messageId) throws MessagingProcessingException {


        Submission submission = submissionUtil.createSubmission(messageId);
        final String dbMessageId = messageSubmitter.submit(submission, "mybackend");

        final UserMessageLog userMessageLog = userMessageLogDao.findByMessageId(dbMessageId, MSHRole.SENDING);
        userMessageLogDao.setMessageStatus(userMessageLog, endStatus);

        return userMessageLog;
    }

    @Transactional
    public UserMessage getUserMessage() {
        final MessageTestUtility messageTestUtility = new MessageTestUtility();
        final UserMessage userMessage = messageTestUtility.createSampleUserMessage();
        userMessage.setMshRole(mshRoleDao.findOrCreate(MSHRole.SENDING));
        final List<PartInfo> partInfoList = messageTestUtility.createPartInfoList(userMessage);

        PartyId senderPartyId = messageTestUtility.createSenderPartyId();
        userMessage.getPartyInfo().getFrom().setFromPartyId(partyIdDao.findOrCreateParty(senderPartyId.getValue(), senderPartyId.getType()));

        userMessage.getPartyInfo().getFrom().setFromRole(partyRoleDao.findOrCreateRole(messageTestUtility.createSenderPartyRole().getValue()));

        final PartyId receiverPartyId = messageTestUtility.createReceiverPartyId();
        userMessage.getPartyInfo().getTo().setToPartyId(partyIdDao.findOrCreateParty(receiverPartyId.getValue(), receiverPartyId.getType()));

        userMessage.getPartyInfo().getTo().setToRole(partyRoleDao.findOrCreateRole(messageTestUtility.createReceiverPartyRole().getValue()));

        userMessage.setAction(actionDao.findOrCreateAction(messageTestUtility.createActionEntity().getValue()));

        final ServiceEntity serviceEntity = messageTestUtility.createServiceEntity();
        userMessage.setService(serviceDao.findOrCreateService(serviceEntity.getValue(), serviceEntity.getType()));

        final AgreementRefEntity agreementRefEntity = messageTestUtility.createAgreementRefEntity();
        userMessage.setAgreementRef(agreementDao.findOrCreateAgreement(agreementRefEntity.getValue(), agreementRefEntity.getType()));

        userMessage.setMpc(mpcDao.findOrCreateMpc(messageTestUtility.createMpcEntity().getValue()));

        HashSet<MessageProperty> messageProperties = new HashSet<>();
        for (MessageProperty messageProperty : userMessage.getMessageProperties()) {
            messageProperties.add(messagePropertyDao.findOrCreateProperty(messageProperty.getName(), messageProperty.getValue(), messageProperty.getType()));
        }

        userMessage.setMessageProperties(messageProperties);

        userMessageDao.create(userMessage);
        return userMessage;
    }

    @Transactional
    public void receiveMessage(String messageId) throws Exception {
        String filename = "SOAPMessage2.xml";
        SOAPMessage soapMessage = soapSampleUtil.createSOAPMessage(filename, messageId);
        mshWebserviceTest.invoke(soapMessage);
    }

    @Transactional
    public void deleteAllMessages(String... messageIds) {
        List<UserMessageLogDto> allMessages = new ArrayList<>();
        for (String messageId : messageIds) {
            if (StringUtils.isNotBlank(messageId)) {
                UserMessageLog byMessageId = userMessageLogDao.findByMessageId(messageId);
                if (byMessageId != null) {

                    UserMessageLogDto userMessageLogDto = new UserMessageLogDto(byMessageId.getUserMessage().getEntityId(), byMessageId.getUserMessage().getMessageId(), byMessageId.getBackend());
                    userMessageLogDto.setProperties(userMessageDefaultServiceHelper.getProperties(byMessageId.getUserMessage()));
                    allMessages.add(userMessageLogDto);
                } else {
                    LOG.warn("MessageId [{}] not found", messageId);
                }
            }
        }
        if (allMessages.size() > 0) {
            userMessageDefaultService.deleteMessages(allMessages);
        }
    }

    public void modifyPmodeRetryParameters(int retryTimeout, int retryCount) {
        //we modify the retry policy so that the message fails immediately and not goes in WAITING_FOR_RETRY
        final CachingPModeProvider currentPModeProvider = (CachingPModeProvider) pModeProvider.getCurrentPModeProvider();
        final ReceptionAwareness receptionAwareness = currentPModeProvider.getConfiguration().getBusinessProcesses().getAs4ConfigReceptionAwareness().stream().findFirst().orElse(null);
        receptionAwareness.setRetryCount(retryTimeout);
        receptionAwareness.setRetryTimeout(retryCount);
    }

    public void modifyPmodeRetentionParameters(Integer retentionDownloaded, Integer retentionUnDownloaded) {
        //we modify the retry policy so that the message fails immediately and not goes in WAITING_FOR_RETRY
        final CachingPModeProvider currentPModeProvider = (CachingPModeProvider) pModeProvider.getCurrentPModeProvider();
        final Mpc mpc = currentPModeProvider.getConfiguration().getMpcs().iterator().next();
        mpc.setRetentionUndownloaded(retentionUnDownloaded);
        mpc.setRetentionDownloaded(retentionDownloaded);
    }
}
