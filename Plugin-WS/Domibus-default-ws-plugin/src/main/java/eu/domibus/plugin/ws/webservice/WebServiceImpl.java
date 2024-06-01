package eu.domibus.plugin.ws.webservice;

import eu.domibus.common.ErrorResult;
import eu.domibus.common.MSHRole;
import eu.domibus.ext.domain.DomainDTO;
import eu.domibus.ext.exceptions.AuthenticationExtException;
import eu.domibus.ext.exceptions.MessageAcknowledgeExtException;
import eu.domibus.ext.services.*;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.logging.MDCKey;
import eu.domibus.messaging.DuplicateMessageException;
import eu.domibus.messaging.MessageNotFoundException;
import eu.domibus.messaging.MessagingProcessingException;
import eu.domibus.plugin.ws.backend.WSBackendMessageLogEntity;
import eu.domibus.plugin.ws.backend.WSBackendMessageLogService;
import eu.domibus.plugin.ws.connector.WSPluginImpl;
import eu.domibus.plugin.ws.exception.WSMessageLogNotFoundException;
import eu.domibus.plugin.ws.exception.WSPluginException;
import eu.domibus.plugin.ws.generated.*;
import eu.domibus.plugin.ws.generated.body.*;
import eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.ObjectFactory;
import eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.*;
import eu.domibus.plugin.ws.message.WSMessageLogEntity;
import eu.domibus.plugin.ws.message.WSMessageLogService;
import eu.domibus.plugin.ws.property.WSPluginPropertyManager;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;
import javax.xml.ws.BindingType;
import javax.xml.ws.Holder;
import javax.xml.ws.soap.SOAPBinding;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static eu.domibus.logging.DomibusMessageCode.BUS_MSG_NOT_FOUND;
import static eu.domibus.logging.DomibusMessageCode.DUPLICATE_MESSAGEID;
import static eu.domibus.messaging.MessageConstants.PAYLOAD_PROPERTY_FILE_PATH;
import static eu.domibus.plugin.ws.property.WSPluginPropertyManager.PROP_LIST_REPUSH_MESSAGES_MAXCOUNT;
import static org.apache.commons.lang3.BooleanUtils.toBooleanDefaultIfNull;
import static org.apache.commons.lang3.BooleanUtils.toBooleanObject;

@SuppressWarnings("ValidExternallyBoundObject")
@javax.jws.WebService(
        serviceName = "WebServicePlugin",
        portName = "WEBSERVICEPLUGIN_PORT",
        targetNamespace = "http://eu.domibus.wsplugin/",
        endpointInterface = "eu.domibus.plugin.ws.generated.WebServicePluginInterface")
@BindingType(SOAPBinding.SOAP12HTTP_BINDING)
public class WebServiceImpl implements WebServicePluginInterface {

    public static final String MESSAGE_SUBMISSION_FAILED = "Message submission failed";
    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(WebServiceImpl.class);

    public static final eu.domibus.plugin.ws.generated.body.ObjectFactory WEBSERVICE_OF = new eu.domibus.plugin.ws.generated.body.ObjectFactory();

    public static final ObjectFactory EBMS_OBJECT_FACTORY = new ObjectFactory();

    private static final String MIME_TYPE = "MimeType";

    private static final String DEFAULT_CONTENT_TYPE = "text/xml";

    private static final String MESSAGE_ID_EMPTY = "Message ID is empty";

    private static final String INVALID_ACCESS_POINT_ROLE = "Access point role is invalid";

    public static final String MESSAGE_NOT_FOUND_ID = "Message not found, id [";
    public static final String INVALID_REQUEST = "Invalid request";
    public static final String DUPLICATE_MESSAGE_ID = "Duplicated message found, id [";
    public static final String ERROR_MESSAGE_ID = "Error message, id [";

    private MessageAcknowledgeExtService messageAcknowledgeExtService;

    protected WebServiceExceptionFactory webServicePluginExceptionFactory;

    protected WSMessageLogService wsMessageLogService;
    protected WSBackendMessageLogService wsBackendMessageLogService;

    private DomainContextExtService domainContextExtService;

    protected WSPluginPropertyManager wsPluginPropertyManager;

    private AuthenticationExtService authenticationExtService;

    protected MessageExtService messageExtService;

    private WSPluginImpl wsPlugin;

    private DateExtService dateUtil;

    public WebServiceImpl(MessageAcknowledgeExtService messageAcknowledgeExtService,
                          WebServiceExceptionFactory webServicePluginExceptionFactory,
                          WSMessageLogService wsMessageLogService,
                          WSBackendMessageLogService wsBackendMessageLogService,
                          DomainContextExtService domainContextExtService,
                          WSPluginPropertyManager wsPluginPropertyManager,
                          AuthenticationExtService authenticationExtService,
                          MessageExtService messageExtService,
                          WSPluginImpl wsPlugin,
                          DateExtService dateUtil) {
        this.messageAcknowledgeExtService = messageAcknowledgeExtService;
        this.webServicePluginExceptionFactory = webServicePluginExceptionFactory;
        this.wsMessageLogService = wsMessageLogService;
        this.wsBackendMessageLogService = wsBackendMessageLogService;
        this.domainContextExtService = domainContextExtService;
        this.wsPluginPropertyManager = wsPluginPropertyManager;
        this.authenticationExtService = authenticationExtService;
        this.messageExtService = messageExtService;
        this.wsPlugin = wsPlugin;
        this.dateUtil = dateUtil;
    }

    /**
     * Add support for large files using DataHandler instead of byte[]
     *
     * @param submitRequest
     * @param ebMSHeaderInfo
     * @return {@link SubmitResponse} object
     * @throws SubmitMessageFault
     */
    @SuppressWarnings("ValidExternallyBoundObject")
    @Override
    public SubmitResponse submitMessage(SubmitRequest submitRequest, Messaging ebMSHeaderInfo) throws SubmitMessageFault {
        LOG.debug("Received message");

        addPartInfos(submitRequest, ebMSHeaderInfo);
        if (ebMSHeaderInfo.getUserMessage().getMessageInfo() == null) {
            MessageInfo messageInfo = new MessageInfo();
            messageInfo.setTimestamp(LocalDateTime.now());
            ebMSHeaderInfo.getUserMessage().setMessageInfo(messageInfo);
        } else {
            final String submittedMessageId = ebMSHeaderInfo.getUserMessage().getMessageInfo().getMessageId();
            if (StringUtils.isNotEmpty(submittedMessageId)) {
                //if there is a submitted messageId we trim it
                LOG.debug("Submitted messageId=[{}]", submittedMessageId);
                String trimmedMessageId = messageExtService.cleanMessageIdentifier(submittedMessageId);
                ebMSHeaderInfo.getUserMessage().getMessageInfo().setMessageId(trimmedMessageId);
            }
        }

        final String messageId;
        try {
            messageId = wsPlugin.submit(ebMSHeaderInfo);
        } catch (final MessagingProcessingException mpEx) {
            LOG.error(MESSAGE_SUBMISSION_FAILED, mpEx);
            throw new SubmitMessageFault(MESSAGE_SUBMISSION_FAILED, generateFaultDetail(mpEx));
        }
        LOG.info("Received message from backend with messageID [{}]", messageId);
        final SubmitResponse response = WEBSERVICE_OF.createSubmitResponse();
        response.getMessageID().add(messageId);
        return response;
    }

    public UserMessage downloadUserMessage(String messageId) {
        UserMessage userMessage;
        try {
            userMessage = wsPlugin.downloadMessage(messageId, null);
        } catch (final WSMessageLogNotFoundException wsmlnfEx) {
            LOG.warn("WSMessageLog not found for messageId [{}]", messageId);
            throw new WSPluginException(MESSAGE_NOT_FOUND_ID + messageId + "]", wsmlnfEx);
        } catch (final MessageNotFoundException mnfEx) {
            throw new WSPluginException(MESSAGE_NOT_FOUND_ID + messageId + "]");
        }
        return userMessage;
    }

    private void addPartInfos(SubmitRequest submitRequest, Messaging ebMSHeaderInfo) throws SubmitMessageFault {

        if (getPayloadInfo(ebMSHeaderInfo) == null) {
            return;
        }

        validateSubmitRequest(submitRequest);

        List<PartInfo> partInfoList = getPartInfo(ebMSHeaderInfo);
        List<ExtendedPartInfo> partInfosToAdd = new ArrayList<>();

        for (Iterator<PartInfo> i = partInfoList.iterator(); i.hasNext(); ) {
            ExtendedPartInfo extendedPartInfo = new ExtendedPartInfo(i.next());
            partInfosToAdd.add(extendedPartInfo);
            i.remove();

            initPartInfoPayLoad(submitRequest, extendedPartInfo);
        }
        partInfoList.addAll(partInfosToAdd);
    }

    private void initPartInfoPayLoad(SubmitRequest submitRequest, ExtendedPartInfo extendedPartInfo) throws SubmitMessageFault {
        boolean foundPayload = false;
        final String href = extendedPartInfo.getHref();
        LOG.debug("Looking for payload: {}", href);
        for (final LargePayloadType payload : submitRequest.getPayload()) {
            LOG.debug("comparing with payload id: " + payload.getPayloadId());
            if (StringUtils.equalsIgnoreCase(payload.getPayloadId(), href)) {
                this.copyPartProperties(payload.getContentType(), extendedPartInfo);
                extendedPartInfo.setInBody(false);
                LOG.debug("sendMessage - payload Content Type: " + payload.getContentType());
                extendedPartInfo.setPayloadDatahandler(payload.getValue());
                foundPayload = true;
                break;
            }
        }
        Optional<Property> filepathProperty = extendedPartInfo.getPartProperties().getProperty().stream()
                .filter(property -> PAYLOAD_PROPERTY_FILE_PATH.equalsIgnoreCase(property.getName()))
                .findFirst();
        if (filepathProperty.isPresent()) {
            if (foundPayload) {
                LOG.warn("An unexpected payload found in the body (payloadId='{}') will be ignored because in the header a filepath is provided", extendedPartInfo.getHref());
            }

            String filepath = filepathProperty.get().getValue();
            DataHandler dataHandler;
            try {
                dataHandler = new DataHandler(new URLDataSource(new URL(filepath)));
            } catch (MalformedURLException e) {
                throw new SubmitMessageFault("Invalid filepath property", generateDefaultFaultDetail(ErrorCode.WS_PLUGIN_0005, filepath), e);
            }
            final PartProperties partProperties = extendedPartInfo.getPartProperties();

            Property prop = new Property();
            prop.setName(PAYLOAD_PROPERTY_FILE_PATH);
            prop.setValue(filepath);
            partProperties.getProperty().add(prop);
            extendedPartInfo.setPartProperties(partProperties);
            extendedPartInfo.setPayloadDatahandler(dataHandler);
        }

        if (!foundPayload && !filepathProperty.isPresent()) {
            initPayloadInBody(submitRequest, extendedPartInfo, href);
        }
    }

    private void initPayloadInBody(SubmitRequest submitRequest, ExtendedPartInfo extendedPartInfo, String href) throws SubmitMessageFault {
        final LargePayloadType bodyload = submitRequest.getBodyload();
        if (bodyload == null) {
            // in this case the payload referenced in the partInfo was neither an external payload nor a bodyload
            throw new SubmitMessageFault("No Payload or Bodyload found for PartInfo with href: " + extendedPartInfo.getHref(), generateDefaultFaultDetail(ErrorCode.WS_PLUGIN_0001, extendedPartInfo.getHref()));
        }
        // It can only be in body load, href MAY be null!
        if (href == null && bodyload.getPayloadId() == null || href != null && StringUtils.equalsIgnoreCase(href, bodyload.getPayloadId())) {
            this.copyPartProperties(bodyload.getContentType(), extendedPartInfo);
            extendedPartInfo.setInBody(true);
            LOG.debug("sendMessage - bodyload Content Type: " + bodyload.getContentType());
            extendedPartInfo.setPayloadDatahandler(bodyload.getValue());
        } else {
            throw new SubmitMessageFault("No payload found for PartInfo with href: " + extendedPartInfo.getHref(), generateDefaultFaultDetail(ErrorCode.WS_PLUGIN_0005, extendedPartInfo.getHref()));
        }
    }

    protected void validateSubmitRequest(SubmitRequest submitRequest) throws SubmitMessageFault {
        for (final LargePayloadType payload : submitRequest.getPayload()) {
            if (StringUtils.isBlank(payload.getPayloadId())) {
                throw new SubmitMessageFault(INVALID_REQUEST, generateDefaultFaultDetail(ErrorCode.WS_PLUGIN_0005, "Attribute 'payloadId' of the 'payload' element must not be empty"));
            }
        }
        final LargePayloadType bodyload = submitRequest.getBodyload();
        if (bodyload != null && StringUtils.isNotBlank(bodyload.getPayloadId())) {
            throw new SubmitMessageFault(INVALID_REQUEST, generateDefaultFaultDetail(ErrorCode.WS_PLUGIN_0005, "Attribute 'payloadId' must not appear on element 'bodyload'"));
        }
    }

    private FaultDetail generateFaultDetail(MessagingProcessingException mpEx) {
        FaultDetail fd = WEBSERVICE_OF.createFaultDetail();
        fd.setCode(mpEx.getEbms3ErrorCode().getErrorCodeName());
        fd.setMessage(mpEx.getMessage());
        return fd;
    }

    private FaultDetail generateDefaultFaultDetail(ErrorCode errorCode, String message) {
        FaultDetail fd = WEBSERVICE_OF.createFaultDetail();
        fd.setCode(errorCode.name());
        fd.setMessage(message);
        return fd;
    }

    private void copyPartProperties(final String payloadContentType, final ExtendedPartInfo partInfo) throws SubmitMessageFault {
        final PartProperties partProperties = new PartProperties();

        // Add mimetype property if there are no part properties
        if (partInfo.getPartProperties() == null) {
            setMimeTypeProperty(payloadContentType, partProperties);

        } else {
            //throw exception for part properties without any property
            if (CollectionUtils.isEmpty(partInfo.getPartProperties().getProperty())) {
                throw new SubmitMessageFault(INVALID_REQUEST, generateDefaultFaultDetail(ErrorCode.WS_PLUGIN_0005, "PartProperties should not be empty."));
            }

            // add all partproperties WEBSERVICE_OF the backend message
            for (final Property property : partInfo.getPartProperties().getProperty()) {
                Property prop = new Property();
                prop.setName(property.getName());
                prop.setValue(property.getValue());
                prop.setType(property.getType());
                partProperties.getProperty().add(prop);
            }

            boolean mimeTypePropFound = false;
            for (final Property property : partProperties.getProperty()) {
                if (MIME_TYPE.equalsIgnoreCase(property.getName())) {
                    mimeTypePropFound = true;
                    break;
                }
            }
            // in case there was no property with name {@value Property.MIME_TYPE} and xmime:contentType attribute was set noinspection SuspiciousMethodCalls
            if (!mimeTypePropFound) {
                setMimeTypeProperty(payloadContentType, partProperties);
            }
        }
        partInfo.setPartProperties(partProperties);
    }

    private void setMimeTypeProperty(String payloadContentType, PartProperties partProperties) {
        Property prop = new Property();
        prop.setName(MIME_TYPE);
        if (payloadContentType == null) {
            prop.setValue(DEFAULT_CONTENT_TYPE);
        } else {
            prop.setValue(payloadContentType);
        }
        partProperties.getProperty().add(prop);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 300) // 5 minutes
    public ListPendingMessagesResponse listPendingMessages(ListPendingMessagesRequest listPendingMessagesRequest) {
        DomainDTO domainDTO = domainContextExtService.getCurrentDomainSafely();
        LOG.info("ListPendingMessages for domain [{}]", domainDTO);

        final ListPendingMessagesResponse response = WEBSERVICE_OF.createListPendingMessagesResponse();
        final int intMaxPendingMessagesRetrieveCount = wsPluginPropertyManager.getKnownIntegerPropertyValue(WSPluginPropertyManager.PROP_LIST_PENDING_MESSAGES_MAXCOUNT);
        LOG.debug("maxPendingMessagesRetrieveCount [{}]", intMaxPendingMessagesRetrieveCount);

        String finalRecipient = listPendingMessagesRequest.getFinalRecipient();
        if (!authenticationExtService.isUnsecureLoginAllowed()) {
            String originalUser = authenticationExtService.getOriginalUser();
            if (StringUtils.isNotEmpty(finalRecipient)) {
                LOG.warn("finalRecipient [{}] provided in listPendingMessagesRequest is overridden by authenticated user [{}]", finalRecipient, originalUser);
            }
            finalRecipient = originalUser;
        }
        LOG.info("Final Recipient is [{}]", finalRecipient);

        List<WSMessageLogEntity> pending = wsMessageLogService.findAllWithFilter(
                listPendingMessagesRequest.getMessageId(),
                listPendingMessagesRequest.getFromPartyId(),
                listPendingMessagesRequest.getConversationId(),
                listPendingMessagesRequest.getRefToMessageId(),
                listPendingMessagesRequest.getOriginalSender(),
                finalRecipient,
                listPendingMessagesRequest.getReceivedFrom(),
                listPendingMessagesRequest.getReceivedTo(),
                intMaxPendingMessagesRetrieveCount);

        final Collection<String> ids = pending.stream()
                .map(WSMessageLogEntity::getMessageId).collect(Collectors.toList());
        response.getMessageID().addAll(ids);
        return response;
    }

    @Override
    public ListPushFailedMessagesResponse listPushFailedMessages(ListPushFailedMessagesRequest listPushFailedMessagesRequest) throws ListPushFailedMessagesFault {
        DomainDTO domainDTO = domainContextExtService.getCurrentDomainSafely();
        LOG.info("ListPendingMessages for domain [{}]", domainDTO);

        ListPushFailedMessagesRequest validListPushFailedMessagesRequest = getValidListPushFailedMessagesRequest(listPushFailedMessagesRequest);

        final ListPushFailedMessagesResponse response = WEBSERVICE_OF.createListPushFailedMessagesResponse();
        final int intMaxPendingMessagesRetrieveCount = wsPluginPropertyManager.getKnownIntegerPropertyValue(WSPluginPropertyManager.PROP_LIST_PUSH_FAILED_MESSAGES_MAXCOUNT);
        LOG.debug("maxPushFailedMessagesRetrieveCount [{}]", intMaxPendingMessagesRetrieveCount);

        String finalRecipient = validListPushFailedMessagesRequest.getFinalRecipient();
        if (!authenticationExtService.isUnsecureLoginAllowed()) {
            String originalUser = authenticationExtService.getOriginalUser();
            if (StringUtils.isNotEmpty(finalRecipient)) {
                LOG.warn("finalRecipient [{}] provided in listPendingMessagesRequest is overridden by authenticated user [{}]", finalRecipient, originalUser);
            }
            finalRecipient = originalUser;
        }
        LOG.info("Final Recipient is [{}]", finalRecipient);

        List<WSBackendMessageLogEntity> pending = wsBackendMessageLogService.findAllWithFilter(
                validListPushFailedMessagesRequest.getMessageId(),
                validListPushFailedMessagesRequest.getOriginalSender(),
                finalRecipient,
                validListPushFailedMessagesRequest.getReceivedFrom(),
                validListPushFailedMessagesRequest.getReceivedTo(),
                intMaxPendingMessagesRetrieveCount);

        final Collection<String> ids = pending.stream()
                .map(WSBackendMessageLogEntity::getMessageId).collect(Collectors.toList());
        response.getMessageID().addAll(ids);
        return response;
    }

    protected ListPushFailedMessagesRequest getValidListPushFailedMessagesRequest(ListPushFailedMessagesRequest listPushFailedMessagesRequest) throws ListPushFailedMessagesFault {
        String messageId = listPushFailedMessagesRequest.getMessageId();
        ListPushFailedMessagesRequest pushFailedMessagesRequest = new ListPushFailedMessagesRequest();

        //Since message id is an optional parameter in the request the exception throws only when the message id exists but it is empty.
        if (messageId != null && StringUtils.isBlank(messageId)) {
            LOG.error(MESSAGE_ID_EMPTY);
            throw new ListPushFailedMessagesFault(MESSAGE_ID_EMPTY, webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, MESSAGE_ID_EMPTY));
        }

        if (messageExtService.isTrimmedStringLengthLongerThanDefaultMaxLength(messageId)) {
            throw new ListPushFailedMessagesFault("Invalid Message Id. ", webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, "Value of messageId [" + messageId + "] is too long (over 255 characters)."));
        }

        pushFailedMessagesRequest.setMessageId(StringUtils.trim(messageId));
        pushFailedMessagesRequest.setFinalRecipient(StringUtils.trim(listPushFailedMessagesRequest.getFinalRecipient()));
        pushFailedMessagesRequest.setOriginalSender(StringUtils.trim(listPushFailedMessagesRequest.getOriginalSender()));
        if (listPushFailedMessagesRequest.getReceivedFrom() != null) {
            pushFailedMessagesRequest.setReceivedFrom(dateUtil.getUtcLocalDateTime(listPushFailedMessagesRequest.getReceivedFrom()));
        }
        if (listPushFailedMessagesRequest.getReceivedTo() != null) {
            pushFailedMessagesRequest.setReceivedTo(dateUtil.getUtcLocalDateTime(listPushFailedMessagesRequest.getReceivedTo()));
        }
        return pushFailedMessagesRequest;
    }

    @Override
    @Transactional
    public void rePushFailedMessages(RePushFailedMessagesRequest rePushFailedMessagesRequest) throws RePushFailedMessagesFault {
        DomainDTO domainDTO = domainContextExtService.getCurrentDomainSafely();
        LOG.info("rePushFailedMessages for domain [{}]", domainDTO);

        final int repushMaxCount = wsPluginPropertyManager.getKnownIntegerPropertyValue(PROP_LIST_REPUSH_MESSAGES_MAXCOUNT);

        int nbrMessagesToRepush = CollectionUtils.size(rePushFailedMessagesRequest.getMessageID());
        if (nbrMessagesToRepush > repushMaxCount) {
            throw new RePushFailedMessagesFault("Invalid argument", webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, "Too many messages. the limit is [" + PROP_LIST_REPUSH_MESSAGES_MAXCOUNT + "]:" + repushMaxCount + ". Actual is: " + nbrMessagesToRepush));
        }
        List<String> validMessageIds = getValidMessageIds(rePushFailedMessagesRequest);
        try {
            wsBackendMessageLogService.updateForRetry(validMessageIds);
        } catch (WSPluginException e) {
            throw new RePushFailedMessagesFault("RePush has failed", webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0009, "At least one message was not found"));
        }

        LOG.info("Messages updated for retry successfully");
    }


    protected List<String> getValidMessageIds(RePushFailedMessagesRequest rePushFailedMessagesRequest) throws RePushFailedMessagesFault {
        List<String> messageIds = rePushFailedMessagesRequest.getMessageID();
        List<String> trimmedMessageIds = new ArrayList<>();
        ListPushFailedMessagesRequest pushFailedMessagesRequest = new ListPushFailedMessagesRequest();
        for (String messageId : messageIds) {
            String trimmedMessageId = messageExtService.cleanMessageIdentifier(messageId);

            if (StringUtils.isEmpty(trimmedMessageId)) {
                LOG.error(MESSAGE_ID_EMPTY);
                throw new RePushFailedMessagesFault(MESSAGE_ID_EMPTY, webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, MESSAGE_ID_EMPTY));
            }

            if (messageExtService.isTrimmedStringLengthLongerThanDefaultMaxLength(messageId)) {
                throw new RePushFailedMessagesFault("Invalid Message Id. ", webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, "Value of messageId [" + messageId + "] is too long (over 255 characters)."));
            }
            pushFailedMessagesRequest.setMessageId(trimmedMessageId);
            ListPushFailedMessagesResponse response;
            try {
                response = listPushFailedMessages(pushFailedMessagesRequest);
            } catch (ListPushFailedMessagesFault ex) {
                throw new RePushFailedMessagesFault(" List Push Failed Messages has failed", webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, ex.getMessage()));
            }
            if (!response.getMessageID().contains(trimmedMessageId)) {
                throw new RePushFailedMessagesFault("Invalid Message Id. ", webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, "The message [" + messageId + "] is not in the list of push failed messages"));
            }

            trimmedMessageIds.add(trimmedMessageId);
        }
        return trimmedMessageIds;
    }

    /**
     * The message status is updated to downloaded (the message is also removed from the plugin table ws_plugin_tb_message_log containing the pending messages)
     *
     * @param markMessageAsDownloadedRequest
     * @param markMessageAsDownloadedResponse
     * @param ebMSHeaderInfo
     * @throws eu.domibus.plugin.ws.generated.MarkMessageAsDownloadedFault
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 300, rollbackFor = RetrieveMessageFault.class)
    @MDCKey(value = {DomibusLogger.MDC_MESSAGE_ID, DomibusLogger.MDC_MESSAGE_ROLE, DomibusLogger.MDC_MESSAGE_ENTITY_ID}, cleanOnStart = true)
    public void markMessageAsDownloaded(MarkMessageAsDownloadedRequest markMessageAsDownloadedRequest,
                                        Holder<MarkMessageAsDownloadedResponse> markMessageAsDownloadedResponse,
                                        Holder<Messaging> ebMSHeaderInfo) throws MarkMessageAsDownloadedFault {

        String messageID = markMessageAsDownloadedRequest.getMessageID();
        if (StringUtils.isEmpty(messageID)) {
            LOG.error(MESSAGE_ID_EMPTY);
            throw new MarkMessageAsDownloadedFault(MESSAGE_ID_EMPTY, webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, MESSAGE_ID_EMPTY));
        }

        String trimmedMessageId = messageExtService.cleanMessageIdentifier(messageID);
        WSMessageLogEntity wsMessageLogEntity = wsMessageLogService.findByMessageId(trimmedMessageId);
        if (wsMessageLogEntity == null) {
            LOG.businessError(BUS_MSG_NOT_FOUND, trimmedMessageId);
            throw new MarkMessageAsDownloadedFault(MESSAGE_NOT_FOUND_ID + trimmedMessageId + "]", webServicePluginExceptionFactory.createFaultMessageIdNotFound(trimmedMessageId));
        }
        markMessageAsDownloaded(trimmedMessageId);

        markMessageAsDownloadedResponse.value = WEBSERVICE_OF.createMarkMessageAsDownloadedResponse();
        markMessageAsDownloadedResponse.value.setMessageID(trimmedMessageId);

        wsMessageLogService.delete(wsMessageLogEntity);
    }

    /**
     * Add support for large files using DataHandler instead of byte[]
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 300, rollbackFor = RetrieveMessageFault.class)
    @MDCKey(value = {DomibusLogger.MDC_MESSAGE_ID, DomibusLogger.MDC_MESSAGE_ROLE, DomibusLogger.MDC_MESSAGE_ENTITY_ID}, cleanOnStart = true)
    public void retrieveMessage(RetrieveMessageRequest retrieveMessageRequest,
                                Holder<RetrieveMessageResponse> retrieveMessageResponse,
                                Holder<Messaging> ebMSHeaderInfo) throws RetrieveMessageFault {
        UserMessage userMessage;
        boolean messageIdNotEmpty = StringUtils.isNotEmpty(retrieveMessageRequest.getMessageID());

        if (!messageIdNotEmpty) {
            LOG.error(MESSAGE_ID_EMPTY);
            throw new RetrieveMessageFault(MESSAGE_ID_EMPTY, webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, "MessageId is empty"));
        }

        String trimmedMessageId = messageExtService.cleanMessageIdentifier(retrieveMessageRequest.getMessageID());
        boolean markAsDownloaded = toBooleanDefaultIfNull(toBooleanObject(retrieveMessageRequest.getMarkAsDownloaded()), true);  //workaround jaxws bug
        userMessage = downloadUserMessage(trimmedMessageId, markAsDownloaded);

        // To avoid blocking errors during the Header's response validation
        if (StringUtils.isEmpty(userMessage.getCollaborationInfo().getAgreementRef().getValue())) {
            userMessage.getCollaborationInfo().setAgreementRef(null);
        }
        Messaging messaging = EBMS_OBJECT_FACTORY.createMessaging();
        messaging.setUserMessage(userMessage);
        ebMSHeaderInfo.value = messaging;
        retrieveMessageResponse.value = WEBSERVICE_OF.createRetrieveMessageResponse();

        fillInfoPartsForLargeFiles(retrieveMessageResponse, messaging);

        try {
            messageAcknowledgeExtService.acknowledgeMessageDeliveredWithUnsecureLoginAllowed(trimmedMessageId, new Timestamp(System.currentTimeMillis()), markAsDownloaded);
        } catch (AuthenticationExtException | MessageAcknowledgeExtException e) {
            //if an error occurs related to the message acknowledgement do not block the download message operation
            LOG.error("Error acknowledging message [" + retrieveMessageRequest.getMessageID() + "]", e);
        }
    }

    private UserMessage downloadUserMessage(String trimmedMessageId, boolean markAsAcknowledged) throws
            RetrieveMessageFault {
        UserMessage userMessage;
        try {
            userMessage = wsPlugin.downloadMessage(trimmedMessageId, null, markAsAcknowledged);
        } catch (WSMessageLogNotFoundException wsmlnfEx) {
            LOG.businessError(BUS_MSG_NOT_FOUND, trimmedMessageId);
            throw new RetrieveMessageFault(MESSAGE_NOT_FOUND_ID + trimmedMessageId + "]", webServicePluginExceptionFactory.createFaultMessageIdNotFound(trimmedMessageId));
        } catch (final MessageNotFoundException mnfEx) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(MESSAGE_NOT_FOUND_ID + trimmedMessageId + "]", mnfEx);
            }
            LOG.error(MESSAGE_NOT_FOUND_ID + trimmedMessageId + "]");
            throw new RetrieveMessageFault(MESSAGE_NOT_FOUND_ID + trimmedMessageId + "]", webServicePluginExceptionFactory.createFaultMessageIdNotFound(trimmedMessageId));
        }

        if (userMessage == null) {
            LOG.error(MESSAGE_NOT_FOUND_ID + trimmedMessageId + "]");
            throw new RetrieveMessageFault(MESSAGE_NOT_FOUND_ID + trimmedMessageId + "]", webServicePluginExceptionFactory.createFaultMessageIdNotFound(trimmedMessageId));
        }
        return userMessage;
    }

    private void markMessageAsDownloaded(String trimmedMessageId) throws MarkMessageAsDownloadedFault {
        try {
            wsPlugin.markMessageAsDownloaded(trimmedMessageId);
        } catch (final MessageNotFoundException mnfEx) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(MESSAGE_NOT_FOUND_ID + trimmedMessageId + "]", mnfEx);
            }
            LOG.businessError(BUS_MSG_NOT_FOUND, trimmedMessageId);
            throw new MarkMessageAsDownloadedFault(MESSAGE_NOT_FOUND_ID + trimmedMessageId + "]", webServicePluginExceptionFactory.createDownloadMessageFault(mnfEx));
        }
    }

    private void fillInfoPartsForLargeFiles(Holder<RetrieveMessageResponse> retrieveMessageResponse, Messaging
            messaging) {
        if (getPayloadInfo(messaging) == null || CollectionUtils.isEmpty(getPartInfo(messaging))) {
            LOG.info("No payload found for message [{}]", messaging.getUserMessage().getMessageInfo().getMessageId());
            return;
        }

        for (final PartInfo partInfo : getPartInfo(messaging)) {
            ExtendedPartInfo extPartInfo = (ExtendedPartInfo) partInfo;
            LargePayloadType payloadType = WEBSERVICE_OF.createLargePayloadType();
            DataHandler payloadDatahandler = extPartInfo.getPayloadDatahandler();
            if (payloadDatahandler != null) {
                String contentType = payloadDatahandler.getContentType();
                LOG.debug("payloadDatahandler Content Type: [{}]", contentType);
                payloadType.setContentType(contentType);
                payloadType.setValue(payloadDatahandler);
            }
            if (extPartInfo.isInBody()) {
                retrieveMessageResponse.value.setBodyload(payloadType);
            } else {
                payloadType.setPayloadId(partInfo.getHref());
                retrieveMessageResponse.value.getPayload().add(payloadType);
            }
        }
    }

    private PayloadInfo getPayloadInfo(Messaging messaging) {
        if (messaging.getUserMessage() == null) {
            return null;
        }
        return messaging.getUserMessage().getPayloadInfo();
    }

    private List<PartInfo> getPartInfo(Messaging messaging) {
        PayloadInfo payloadInfo = getPayloadInfo(messaging);
        if (payloadInfo == null) {
            return new ArrayList<>();
        }
        return payloadInfo.getPartInfo();
    }

    /**
     * @param statusRequest
     * @return returns eu.domibus.plugin.ws.generated.body.MessageStatus
     * @throws StatusFault
     * @deprecated since 5.1 Use instead {@link #getStatusWithAccessPointRole(StatusRequestWithAccessPointRole statusRequestWithAccessPointRole)}
     */
    @Deprecated
    @Override
    public MessageStatus getStatus(final StatusRequest statusRequest) throws StatusFault {
        String trimmedMessageId = messageExtService.cleanMessageIdentifier(statusRequest.getMessageID());
        try {
            validateMessageId(trimmedMessageId);
            return MessageStatus.fromValue(wsPlugin.getMessageRetriever().getStatus(trimmedMessageId).name());
        } catch (final DuplicateMessageException exception) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(DUPLICATE_MESSAGE_ID + trimmedMessageId + "]", exception);
            }
            LOG.error(DUPLICATE_MESSAGE_ID + trimmedMessageId + "]");
            throw new StatusFault(DUPLICATE_MESSAGE_ID + trimmedMessageId + "]", webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, exception.getMessage()));
        }
    }

    /**
     * @param statusRequestWithAccessPointRole
     * @return returns eu.domibus.plugin.ws.generated.body.MessageStatus
     * @throws StatusFault
     */
    @Override
    public MessageStatus getStatusWithAccessPointRole(StatusRequestWithAccessPointRole statusRequestWithAccessPointRole) throws StatusFault {
        String trimmedMessageId = messageExtService.cleanMessageIdentifier(statusRequestWithAccessPointRole.getMessageID());
        validateMessageId(trimmedMessageId);
        validateAccessPointRole(statusRequestWithAccessPointRole.getAccessPointRole());
        MSHRole role = MSHRole.valueOf(statusRequestWithAccessPointRole.getAccessPointRole().name());
        return MessageStatus.fromValue(wsPlugin.getMessageRetriever().getStatus(trimmedMessageId, role).name());
    }

    protected void validateMessageId(String messageId) throws StatusFault {
        boolean isMessageIdEmpty = StringUtils.isEmpty(messageId);
        if (isMessageIdEmpty) {
            LOG.error(MESSAGE_ID_EMPTY);
            throw new StatusFault(MESSAGE_ID_EMPTY, webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, "MessageId is empty"));
        }
        if (messageExtService.isTrimmedStringLengthLongerThanDefaultMaxLength(messageId)) {
            throw new StatusFault("Invalid Message Id. ", webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, "Value of messageId [" + messageId + "] is too long (over 255 characters)."));
        }
    }

    protected void validateAccessPointRole(MshRole role) throws StatusFault {
        if (role == null) {
            LOG.error(INVALID_ACCESS_POINT_ROLE);
            throw new StatusFault(INVALID_ACCESS_POINT_ROLE, webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, "Access point role is invalid"));
        }
    }

    @Override
    public ErrorResultImplArray getMessageErrors(final GetErrorsRequest messageErrorsRequest) throws
            GetMessageErrorsFault {
        List<? extends ErrorResult> errorsForMessage;
        String messageId = messageExtService.cleanMessageIdentifier(messageErrorsRequest.getMessageID());
        validateMessageIdForGetMessageErrors(messageId);
        try {
            errorsForMessage = wsPlugin.getMessageRetriever().getErrorsForMessage(messageId);
        } catch (MessageNotFoundException exception) {
            LOG.businessError(BUS_MSG_NOT_FOUND, messageId);
            LOG.debug(MESSAGE_NOT_FOUND_ID + messageId + "]", exception);
            throw new GetMessageErrorsFault(MESSAGE_NOT_FOUND_ID + messageId + "]", webServicePluginExceptionFactory.createFaultMessageIdNotFound(messageId));
        } catch (DuplicateMessageException e) {
            LOG.businessError(DUPLICATE_MESSAGEID, messageId);
            LOG.debug(DUPLICATE_MESSAGE_ID + messageId + "]", e);
            throw new GetMessageErrorsFault("Duplicate message found with Id [" + messageId + "].", webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_00010, String.format(ErrorCode.WS_PLUGIN_00010.getMessage(), messageId)));
        } catch (Exception e) {
            LOG.businessError(BUS_MSG_NOT_FOUND, messageId);
            LOG.debug(ERROR_MESSAGE_ID + messageId + "]", e);
            throw new GetMessageErrorsFault(MESSAGE_NOT_FOUND_ID + messageId + "]", webServicePluginExceptionFactory.createFaultMessageIdNotFound(messageId));
        }
        return transformFromErrorResults(errorsForMessage);
    }

    /**
     * @param messageErrorsRequestWithAccessPointRole
     * @return returns eu.domibus.plugin.ws.generated.body.ErrorResultImplArray
     * @throws GetMessageErrorsFault
     */
    @Override
    public ErrorResultImplArray getMessageErrorsWithAccessPointRole(GetErrorsRequestWithAccessPointRole messageErrorsRequestWithAccessPointRole) throws GetMessageErrorsFault {
        List<? extends ErrorResult> errorsForMessage;
        String messageId = messageExtService.cleanMessageIdentifier(messageErrorsRequestWithAccessPointRole.getMessageID());
        validateMessageIdForGetMessageErrors(messageId);

        if (messageErrorsRequestWithAccessPointRole.getAccessPointRole() == null) {
            LOG.error(INVALID_ACCESS_POINT_ROLE);
            throw new GetMessageErrorsFault(INVALID_ACCESS_POINT_ROLE, webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, "Access point role is invalid"));
        }

        MSHRole role = MSHRole.valueOf(messageErrorsRequestWithAccessPointRole.getAccessPointRole().name());
        try {
            errorsForMessage = wsPlugin.getMessageRetriever().getErrorsForMessage(messageId, role);
        } catch (MessageNotFoundException exception) {
            LOG.businessError(BUS_MSG_NOT_FOUND, messageId);
            throw new GetMessageErrorsFault(MESSAGE_NOT_FOUND_ID + messageId + "]", webServicePluginExceptionFactory.createFaultMessageIdNotFound(messageId));
        } catch (Exception e) {
            throw new GetMessageErrorsFault("Couldn't find message errors [" + messageId + "]", webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0001, String.format(ErrorCode.WS_PLUGIN_0001.getMessage(), messageId)));
        }
        return transformFromErrorResults(errorsForMessage);
    }

    protected void validateMessageIdForGetMessageErrors(String messageId) throws GetMessageErrorsFault {
        if (StringUtils.isEmpty(messageId)) {
            LOG.error(MESSAGE_ID_EMPTY);
            throw new GetMessageErrorsFault(MESSAGE_ID_EMPTY, webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, MESSAGE_ID_EMPTY));
        }

        if (messageExtService.isTrimmedStringLengthLongerThanDefaultMaxLength(messageId)) {
            throw new GetMessageErrorsFault("Invalid Message Id. ", webServicePluginExceptionFactory.createFault(ErrorCode.WS_PLUGIN_0007, "Value of messageId [" + messageId + "] is too long (over 255 characters)."));
        }
    }

    public ErrorResultImplArray transformFromErrorResults(List<? extends ErrorResult> errors) {
        ErrorResultImplArray errorList = new ErrorResultImplArray();
        for (ErrorResult errorResult : errors) {
            ErrorResultImpl errorResultImpl = new ErrorResultImpl();
            errorResultImpl.setDomibusErrorCode(eu.domibus.plugin.ws.generated.body.DomibusErrorCode.fromValue(errorResult.getErrorCode().name()));
            errorResultImpl.setErrorDetail(errorResult.getErrorDetail());
            errorResultImpl.setMshRole(MshRole.fromValue(errorResult.getMshRole().name()));
            errorResultImpl.setMessageInErrorId(errorResult.getMessageInErrorId());
            LocalDateTime dateTime = LocalDateTime.now(ZoneOffset.UTC);

            if (errorResult.getNotified() != null) {
                dateTime = errorResult.getNotified().toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
            }
            errorResultImpl.setNotified(dateTime);
            if (errorResult.getTimestamp() != null) {
                dateTime = errorResult.getTimestamp().toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
            }
            errorResultImpl.setTimestamp(dateTime);
            errorList.getItem().add(errorResultImpl);
        }
        return errorList;
    }
}
