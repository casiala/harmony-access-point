package eu.domibus.core.message;

import eu.domibus.api.ebms3.model.Ebms3Messaging;
import eu.domibus.api.model.MSHRole;
import eu.domibus.api.model.MessageStatus;
import eu.domibus.api.model.*;
import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.common.*;
import eu.domibus.core.ebms3.EbMS3Exception;
import eu.domibus.core.ebms3.receiver.MSHWebservice;
import eu.domibus.core.ebms3.sender.MessageSenderErrorHandler;
import eu.domibus.core.ebms3.sender.ResponseHandler;
import eu.domibus.core.ebms3.sender.client.MSHDispatcher;
import eu.domibus.core.message.dictionary.NotificationStatusDao;
import eu.domibus.core.message.reliability.ReliabilityChecker;
import eu.domibus.core.plugin.BackendConnectorHelper;
import eu.domibus.core.plugin.BackendConnectorProvider;
import eu.domibus.core.plugin.handler.MessageSubmitterImpl;
import eu.domibus.core.util.MessageUtil;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.messaging.MessagingProcessingException;
import eu.domibus.messaging.XmlProcessingException;
import eu.domibus.plugin.BackendConnector;
import eu.domibus.plugin.Submission;
import eu.domibus.plugin.notification.PluginAsyncNotificationConfiguration;
import eu.domibus.test.common.BackendConnectorMock;
import eu.domibus.test.common.SoapSampleUtil;
import eu.domibus.test.common.SubmissionUtil;
import eu.domibus.web.rest.ro.MessageLogResultRO;
import org.junit.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.SAXException;

import javax.jms.Queue;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.util.*;

import static eu.domibus.common.NotificationType.DEFAULT_PUSH_NOTIFICATIONS;
import static eu.domibus.jms.spi.InternalJMSConstants.UNKNOWN_RECEIVER_QUEUE;
import static eu.domibus.messaging.MessageConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Transactional
@DirtiesContext
public class BackendNotificationServiceIT extends DeleteMessageAbstractIT {

    @Autowired
    protected MessageExchangeService messageExchangeService;

    @Autowired
    BackendConnectorProvider backendConnectorProvider;

    @Autowired
    SoapSampleUtil soapSampleUtil;

    @Autowired
    MSHWebservice mshWebserviceTest;

    @Autowired
    MessageUtil messageUtil;

    @Autowired
    protected BackendConnectorHelper backendConnectorHelper;

    @Autowired
    PluginAsyncNotificationConfiguration pluginAsyncNotificationConfiguration;

    @Autowired
    Queue notifyBackendWebServiceQueue;

    @Autowired
    @Qualifier(UNKNOWN_RECEIVER_QUEUE)
    protected Queue unknownReceiverQueue;

    @Autowired
    protected SubmissionUtil submissionUtil;

    @Autowired
    MessageSubmitterImpl messageSubmitter;

    @Autowired
    MessagesLogServiceImpl messagesLogService;

    @Autowired
    private UserMessageDao userMessageDao;

    @Autowired
    protected MSHDispatcher mshDispatcher;

    @Autowired
    protected ResponseHandler responseHandler;

    @Autowired
    protected ReliabilityChecker reliabilityChecker;

    @Autowired
    protected MessageStatusDao messageStatusDao;

    @Autowired
    protected DomibusPropertyProvider domibusPropertyProvider;

    @Autowired
    NotificationStatusDao notificationStatusDao;

    @Autowired
    @Qualifier("messageSenderErrorHandler")
    protected MessageSenderErrorHandler messageSenderErrorHandler;

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(BackendNotificationServiceIT.class);

    BackendConnectorMock backendConnector;
    String messageId, filename;

    @Transactional
    @Before
    public void before() throws XmlProcessingException, IOException {
        messageId = BackendConnectorMock.MESSAGE_ID;
        filename = "SOAPMessage2.xml";

        uploadPmode();

        backendConnector = new BackendConnectorMock("wsPlugin");
        Mockito.when(backendConnectorProvider.getBackendConnector(Mockito.any(String.class)))
                .thenReturn(backendConnector);
    }

    @Transactional
    @After
    public void after() {
        backendConnector.clear();
        List<MessageLogInfo> list = userMessageLogDao.findAllInfoPaged(0, 100, "ID_PK", true, new HashMap<>(), Collections.emptyList());
        if (list.size() > 0) {
            list.forEach(el -> {
                UserMessageLog res = userMessageLogDao.findByMessageId(el.getMessageId(), el.getMshRole());
                userMessageLogDao.deleteMessageLogs(Arrays.asList(res.getEntityId()));
            });
        }
    }

    @Test
    @Transactional
    public void testNotifyMessageReceivedSync() throws SOAPException, IOException, ParserConfigurationException, SAXException, EbMS3Exception {
        Mockito.when(backendConnectorHelper.getRequiredNotificationTypeList(Mockito.any(BackendConnector.class)))
                .thenReturn(DEFAULT_PUSH_NOTIFICATIONS);

        SOAPMessage soapMessage = soapSampleUtil.createSOAPMessage(filename, messageId);
        final SOAPMessage soapResponse = mshWebserviceTest.invoke(soapMessage);

        assertEquals(backendConnector.getDeliverMessageEvent().getMessageId(), messageId);

        final Ebms3Messaging ebms3Messaging = messageUtil.getMessagingWithDom(soapResponse);
        assertNotNull(ebms3Messaging);

        assertEquals(backendConnector.getDeliverMessageEvent().getMessageId(), messageId);
    }

    @Test
    @Transactional
    public void testValidateAndNotifyAsync() throws SOAPException, IOException, ParserConfigurationException, SAXException, EbMS3Exception {

        Mockito.when(pluginAsyncNotificationConfiguration.getBackendConnector()).thenReturn(backendConnector);
        Mockito.when(pluginAsyncNotificationConfiguration.getBackendNotificationQueue()).thenReturn(notifyBackendWebServiceQueue);

        Mockito.when(backendConnectorHelper.getRequiredNotificationTypeList(Mockito.any(BackendConnector.class)))
                .thenReturn(DEFAULT_PUSH_NOTIFICATIONS);

        SOAPMessage soapMessage = soapSampleUtil.createSOAPMessage(filename, messageId);
        final SOAPMessage soapResponse = mshWebserviceTest.invoke(soapMessage);

        waitUntilMessageHasStatus(messageId,MSHRole.SENDING, MessageStatus.NOT_FOUND);

        final Ebms3Messaging ebms3Messaging = messageUtil.getMessagingWithDom(soapResponse);
        assertNotNull(ebms3Messaging);
    }

    @Test(expected = WebServiceException.class)
    @Transactional
    public void testValidateAndNotifyReceivedFailure() throws SOAPException, IOException, ParserConfigurationException, SAXException, EbMS3Exception {

        Mockito.when(backendConnectorHelper.getRequiredNotificationTypeList(Mockito.any(BackendConnector.class)))
                .thenReturn(DEFAULT_PUSH_NOTIFICATIONS);

        filename = "InvalidBodyloadCidSOAPMessage.xml";
        SOAPMessage soapMessage = soapSampleUtil.createSOAPMessage(filename, messageId);
        final SOAPMessage soapResponse = mshWebserviceTest.invoke(soapMessage);

        waitUntilMessageHasStatus(messageId, MSHRole.RECEIVING, MessageStatus.NOT_FOUND);

        final Ebms3Messaging ebms3Messaging = messageUtil.getMessagingWithDom(soapResponse);
        assertNotNull(ebms3Messaging);

        assertEquals(backendConnector.getMessageReceiveFailureEvent().getMessageId(), messageId);
    }

    @Test
    @Transactional
    public void notifyPayloadEvent() throws MessagingProcessingException {
        Submission submission = submissionUtil.createSubmission();
        messageId = messageSubmitter.submit(submission, backendConnector.getName());

        final UserMessageLog userMessageLog = userMessageLogDao.findByMessageId(messageId, MSHRole.SENDING);
        assertNotNull(userMessageLog);

        final HashMap<String, Object> filters = new HashMap<>();
        filters.put("receivedTo", new Date());
        MessageLogResultRO result = messagesLogService.countAndFindPaged(MessageType.USER_MESSAGE, 0, 10, "received", false, filters, Collections.emptyList());
        assertNotNull(result);
        assertEquals(1, result.getMessageLogEntries().size());
        assertEquals(messageId, result.getMessageLogEntries().get(0).getMessageId());
    }

    @Test
    public void testNotifyMessageDeleted() throws MessagingProcessingException {
        String messageId = itTestsService.sendMessageWithStatus(MessageStatus.ACKNOWLEDGED);

        deleteAllMessages();

        MessageDeletedBatchEvent messageDeletedBatchEvent = backendConnector.getMessageDeletedBatchEvent();
        assertEquals(1, messageDeletedBatchEvent.getMessageDeletedEvents().size());
        MessageDeletedEvent singleMessageDeletedEvent = messageDeletedBatchEvent.getMessageDeletedEvents().get(0);
        assertEquals(singleMessageDeletedEvent.getMessageId(), messageId);
        assertNotNull(singleMessageDeletedEvent.getMessageEntityId());

        assertNotNull(singleMessageDeletedEvent.getProps().get(ORIGINAL_SENDER));
        assertNotNull(singleMessageDeletedEvent.getProps().get(FINAL_RECIPIENT));

        Assert.assertNull(userMessageDao.findByMessageId(messageId));
        Assert.assertNull(userMessageLogDao.findByMessageId(messageId, MSHRole.SENDING));


    }

    @Test
    @Transactional
    public void testEventProps_NotifyMessageReceived() throws SOAPException, IOException, ParserConfigurationException, SAXException {
        Mockito.when(backendConnectorHelper.getRequiredNotificationTypeList(Mockito.any(BackendConnector.class)))
                .thenReturn(DEFAULT_PUSH_NOTIFICATIONS);

        SOAPMessage soapMessage = soapSampleUtil.createSOAPMessage(filename, messageId);
        mshWebserviceTest.invoke(soapMessage);

        DeliverMessageEvent messageReceivedEvent = backendConnector.getDeliverMessageEvent();
        assertEquals(messageReceivedEvent.getMessageId(), messageId);
        assertNotNull(messageReceivedEvent.getMessageEntityId());
        assertEquals(messageReceivedEvent.getProps().get(MSH_ROLE), eu.domibus.common.MSHRole.RECEIVING.name());

        assertPropertiesPresentInEvent(messageReceivedEvent);
    }

    @Test
    @Transactional
    public void testEventProps_NotifyReceivedFailure() throws SOAPException, IOException, ParserConfigurationException, SAXException {
        Mockito.when(backendConnectorHelper.getRequiredNotificationTypeList(Mockito.any(BackendConnector.class)))
                .thenReturn(DEFAULT_PUSH_NOTIFICATIONS);

        filename = "InvalidBodyloadCidSOAPMessage.xml";
        SOAPMessage soapMessage = soapSampleUtil.createSOAPMessage(filename, messageId);
        try {
            mshWebserviceTest.invoke(soapMessage);
        } catch (WebServiceException wse) {
            //do nothing here, make the assertions below
        }

        MessageReceiveFailureEvent messageReceivedFailure = backendConnector.getMessageReceiveFailureEvent();
        assertEquals(messageReceivedFailure.getMessageId(), messageId);
        assertNotNull(messageReceivedFailure.getMessageEntityId());
        assertEquals(messageReceivedFailure.getProps().get(MSH_ROLE), eu.domibus.common.MSHRole.RECEIVING.name());

        assertPropertiesPresentInEvent(messageReceivedFailure);
    }



    private void assertPropertiesPresentInEvent(MessageEvent messageEvent) {
        Map<String,String> properties = messageEvent.getProps();

        assertNotNull(properties.get(CONVERSATION_ID));
        assertNotNull(properties.get(FROM_PARTY_ID));
        assertNotNull(properties.get(TO_PARTY_ID));
        assertNotNull(properties.get(ORIGINAL_SENDER));
        assertNotNull(properties.get(FINAL_RECIPIENT));
        assertNotNull(properties.get(SERVICE));
        assertNotNull(properties.get(SERVICE_TYPE));

        assertNotNull(properties.get(ACTION));
    }

}
