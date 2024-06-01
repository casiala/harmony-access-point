package eu.domibus.plugin.ws.webservice.deprecated;

import eu.domibus.common.ErrorResult;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.MessageInfo;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging;
import eu.domibus.ext.domain.DomainDTO;
import eu.domibus.ext.exceptions.AuthenticationExtException;
import eu.domibus.ext.exceptions.MessageAcknowledgeExtException;
import eu.domibus.ext.services.AuthenticationExtService;
import eu.domibus.ext.services.DomainContextExtService;
import eu.domibus.ext.services.MessageAcknowledgeExtService;
import eu.domibus.ext.services.MessageExtService;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.logging.DomibusMessageCode;
import eu.domibus.logging.MDCKey;
import eu.domibus.messaging.DuplicateMessageException;
import eu.domibus.messaging.MessageConstants;
import eu.domibus.messaging.MessageNotFoundException;
import eu.domibus.messaging.MessagingProcessingException;
import eu.domibus.plugin.webService.generated.*;
import eu.domibus.plugin.ws.connector.WSPluginImpl;
import eu.domibus.plugin.ws.exception.WSMessageLogNotFoundException;
import eu.domibus.plugin.ws.message.WSMessageLogDao;
import eu.domibus.plugin.ws.message.WSMessageLogEntity;
import eu.domibus.plugin.ws.property.WSPluginPropertyManager;
import eu.domibus.plugin.ws.webservice.WebServiceImpl;
import eu.domibus.plugin.ws.webservice.deprecated.mapper.WSPluginMessagingMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.common.util.CollectionUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.ws.BindingType;
import javax.xml.ws.Holder;
import javax.xml.ws.soap.SOAPBinding;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @deprecated since 5.0 Use instead {@link eu.domibus.plugin.ws.webservice.WebServiceImpl}
 */
@SuppressWarnings("ValidExternallyBoundObject")
@Deprecated
@javax.jws.WebService(
        serviceName = "BackendService_1_1",
        portName = "BACKEND_PORT",
        targetNamespace = "http://org.ecodex.backend/1_1/",
        endpointInterface = "eu.domibus.plugin.webService.generated.BackendInterface")
@BindingType(SOAPBinding.SOAP12HTTP_BINDING)
public class WebServicePluginImpl implements BackendInterface {

    public static final String MESSAGE_SUBMISSION_FAILED = "Message submission failed";
    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(WebServicePluginImpl.class);

    public static final eu.domibus.plugin.webService.generated.ObjectFactory WEBSERVICE_OF = new eu.domibus.plugin.webService.generated.ObjectFactory();

    private static final String MIME_TYPE = "MimeType";

    private static final String MESSAGE_ID_EMPTY = "Message ID is empty";

    private static final String MESSAGE_NOT_FOUND_ID = "Message not found, id [";

    private static final String DUPLICATE_MESSAGE_ID = "Duplicated message found, id [";

    private static final String ERROR_MESSAGE_ID = "Error message, id [";

    private MessageAcknowledgeExtService messageAcknowledgeExtService;

    protected WebServicePluginExceptionFactory webServicePluginExceptionFactory;

    protected WSMessageLogDao wsMessageLogDao;

    private DomainContextExtService domainContextExtService;

    protected WSPluginPropertyManager wsPluginPropertyManager;

    private AuthenticationExtService authenticationExtService;

    protected MessageExtService messageExtService;

    private WSPluginImpl wsPlugin;
    private WSPluginMessagingMapper messagingMapper;

    public WebServicePluginImpl(MessageAcknowledgeExtService messageAcknowledgeExtService,
                                WebServicePluginExceptionFactory webServicePluginExceptionFactory,
                                WSMessageLogDao wsMessageLogDao,
                                DomainContextExtService domainContextExtService,
                                WSPluginPropertyManager wsPluginPropertyManager,
                                AuthenticationExtService authenticationExtService,
                                MessageExtService messageExtService,
                                WSPluginImpl wsPlugin,
                                WSPluginMessagingMapper messagingMapper) {
        this.messageAcknowledgeExtService = messageAcknowledgeExtService;
        this.webServicePluginExceptionFactory = webServicePluginExceptionFactory;
        this.wsMessageLogDao = wsMessageLogDao;
        this.domainContextExtService = domainContextExtService;
        this.wsPluginPropertyManager = wsPluginPropertyManager;
        this.authenticationExtService = authenticationExtService;
        this.messageExtService = messageExtService;
        this.wsPlugin = wsPlugin;
        this.messagingMapper = messagingMapper;
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
        eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging message = messagingMapper.messagingToEntity(ebMSHeaderInfo);

        addPartInfos(submitRequest, message);

        final String messageId;
        try {
            messageId = wsPlugin.submitFromOldPlugin(message);
        } catch (final MessagingProcessingException mpEx) {
            LOG.error(MESSAGE_SUBMISSION_FAILED, mpEx);
            throw new SubmitMessageFault(MESSAGE_SUBMISSION_FAILED, generateFaultDetail(mpEx));
        }
        LOG.info("Received message from backend with messageID [{}]", messageId);
        final SubmitResponse response = WEBSERVICE_OF.createSubmitResponse();
        response.getMessageID().add(messageId);
        return response;
    }

    private void addPartInfos(SubmitRequest submitRequest, eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging ebMSHeaderInfo) throws SubmitMessageFault {

        if (getPayloadInfo(ebMSHeaderInfo) == null) {
            return;
        }

        validateSubmitRequest(submitRequest);

        List<eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.PartInfo> partInfoList = getPartInfo(ebMSHeaderInfo);
        List<eu.domibus.plugin.ws.webservice.ExtendedPartInfo> partInfosToAdd = new ArrayList<>();

        for (Iterator<eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.PartInfo> i = partInfoList.iterator(); i.hasNext(); ) {
            eu.domibus.plugin.ws.webservice.ExtendedPartInfo extendedPartInfo = new eu.domibus.plugin.ws.webservice.ExtendedPartInfo(i.next());
            partInfosToAdd.add(extendedPartInfo);
            i.remove();

            initPartInfoPayLoad(submitRequest, extendedPartInfo);
        }
        partInfoList.addAll(partInfosToAdd);
    }

    private void initPartInfoPayLoad(SubmitRequest submitRequest, eu.domibus.plugin.ws.webservice.ExtendedPartInfo extendedPartInfo) throws SubmitMessageFault {
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

        if (!foundPayload) {
            initPayloadInBody(submitRequest, extendedPartInfo, href);
        }
    }

    private void initPayloadInBody(SubmitRequest submitRequest, eu.domibus.plugin.ws.webservice.ExtendedPartInfo extendedPartInfo, String href) throws SubmitMessageFault {
        final LargePayloadType bodyload = submitRequest.getBodyload();
        if (bodyload == null) {
            // in this case the payload referenced in the partInfo was neither an external payload nor a bodyload
            throw new SubmitMessageFault("No Payload or Bodyload found for PartInfo with href: " + extendedPartInfo.getHref(), generateDefaultFaultDetail(extendedPartInfo.getHref()));
        }
        // It can only be in body load, href MAY be null!
        if (href == null && bodyload.getPayloadId() == null || href != null && StringUtils.equalsIgnoreCase(href, bodyload.getPayloadId())) {
            this.copyPartProperties(bodyload.getContentType(), extendedPartInfo);
            extendedPartInfo.setInBody(true);
            LOG.debug("sendMessage - bodyload Content Type: " + bodyload.getContentType());
            extendedPartInfo.setPayloadDatahandler(bodyload.getValue());
        } else {
            throw new SubmitMessageFault("No payload found for PartInfo with href: " + extendedPartInfo.getHref(), generateDefaultFaultDetail(extendedPartInfo.getHref()));
        }
    }

    protected void validateSubmitRequest(SubmitRequest submitRequest) throws SubmitMessageFault {
        for (final LargePayloadType payload : submitRequest.getPayload()) {
            if (StringUtils.isBlank(payload.getPayloadId())) {
                throw new SubmitMessageFault("Invalid request", generateDefaultFaultDetail("Attribute 'payloadId' of the 'payload' element must not be empty"));
            }
        }
        final LargePayloadType bodyload = submitRequest.getBodyload();
        if (bodyload != null && StringUtils.isNotBlank(bodyload.getPayloadId())) {
            throw new SubmitMessageFault("Invalid request", generateDefaultFaultDetail("Attribute 'payloadId' must not appear on element 'bodyload'"));
        }
    }

    private FaultDetail generateFaultDetail(MessagingProcessingException mpEx) {
        FaultDetail fd = WEBSERVICE_OF.createFaultDetail();
        fd.setCode(mpEx.getEbms3ErrorCode().getErrorCodeName());
        fd.setMessage(mpEx.getMessage());
        return fd;
    }

    private FaultDetail generateDefaultFaultDetail(String message) {
        FaultDetail fd = WEBSERVICE_OF.createFaultDetail();
        fd.setCode(ErrorCode.EBMS_0004.name());
        fd.setMessage(message);
        return fd;
    }

    private void copyPartProperties(final String payloadContentType, final eu.domibus.plugin.ws.webservice.ExtendedPartInfo partInfo) {
        final eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.PartProperties partProperties = new eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.PartProperties();
        eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Property prop;

        // add all partproperties WEBSERVICE_OF the backend message
        if (partInfo.getPartProperties() != null) {
            for (final eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Property property : partInfo.getPartProperties().getProperty()) {
                prop = new eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Property();

                prop.setName(property.getName());
                prop.setValue(property.getValue());
                prop.setType(property.getType());
                partProperties.getProperty().add(prop);
            }
        }

        boolean mimeTypePropFound = false;
        for (final eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Property property : partProperties.getProperty()) {
            if (MIME_TYPE.equals(property.getName())) {
                mimeTypePropFound = true;
                break;
            }
        }
        // in case there was no property with name {@value Property.MIME_TYPE} and xmime:contentType attribute was set noinspection SuspiciousMethodCalls
        if (!mimeTypePropFound && payloadContentType != null) {
            prop = new eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Property();
            prop.setName(MIME_TYPE);
            prop.setValue(payloadContentType);
            partProperties.getProperty().add(prop);
        }
        partInfo.setPartProperties(partProperties);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 300) // 5 minutes
    public ListPendingMessagesResponse listPendingMessages(final Object listPendingMessagesRequest) {
        DomainDTO domainDTO = domainContextExtService.getCurrentDomainSafely();
        LOG.info("ListPendingMessages for domain [{}]", domainDTO);

        final ListPendingMessagesResponse response = WEBSERVICE_OF.createListPendingMessagesResponse();
        final int intMaxPendingMessagesRetrieveCount = wsPluginPropertyManager.getKnownIntegerPropertyValue(WSPluginPropertyManager.PROP_LIST_PENDING_MESSAGES_MAXCOUNT);
        LOG.debug("maxPendingMessagesRetrieveCount [{}]", intMaxPendingMessagesRetrieveCount);

        String originalUser = null;
        if (!authenticationExtService.isUnsecureLoginAllowed()) {
            originalUser = authenticationExtService.getOriginalUser();
            LOG.info("Original user is [{}]", originalUser);
        }

        List<WSMessageLogEntity> pending;
        if (originalUser != null) {
            pending = wsMessageLogDao.findAllByFinalRecipient(intMaxPendingMessagesRetrieveCount, originalUser);
        } else {
            pending = wsMessageLogDao.findAll(intMaxPendingMessagesRetrieveCount);
        }

        final Collection<String> ids = pending.stream()
                .map(WSMessageLogEntity::getMessageId).collect(Collectors.toList());
        response.getMessageID().addAll(ids);
        return response;
    }

    /**
     * Add support for large files using DataHandler instead of byte[]
     *
     * @param retrieveMessageRequest
     * @param retrieveMessageResponse
     * @param ebMSHeaderInfo
     * @throws RetrieveMessageFault
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 300, rollbackFor = RetrieveMessageFault.class)
    @MDCKey(value = {DomibusLogger.MDC_MESSAGE_ID, DomibusLogger.MDC_MESSAGE_ROLE, DomibusLogger.MDC_MESSAGE_ENTITY_ID}, cleanOnStart = true)
    public void retrieveMessage(RetrieveMessageRequest retrieveMessageRequest,
                                Holder<RetrieveMessageResponse> retrieveMessageResponse,
                                Holder<Messaging> ebMSHeaderInfo) throws RetrieveMessageFault {
        boolean isMessageIdNotEmpty = StringUtils.isNotEmpty(retrieveMessageRequest.getMessageID());

        if (!isMessageIdNotEmpty) {
            LOG.error(MESSAGE_ID_EMPTY);
            throw new RetrieveMessageFault(MESSAGE_ID_EMPTY, webServicePluginExceptionFactory.createFault("MessageId is empty"));
        }

        String trimmedMessageId = messageExtService.cleanMessageIdentifier(retrieveMessageRequest.getMessageID());
        eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage userMessage =
                getUserMessage(retrieveMessageRequest, trimmedMessageId);
        eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging messagingWs =
                WebServiceImpl.EBMS_OBJECT_FACTORY.createMessaging();
        messagingWs.setUserMessage(userMessage);
        retrieveMessageResponse.value = WEBSERVICE_OF.createRetrieveMessageResponse();
        fillInfoPartsForLargeFilesWs(retrieveMessageResponse, messagingWs);
        // To avoid blocking errors during the Header's response validation
        if (StringUtils.isEmpty(userMessage.getCollaborationInfo().getAgreementRef().getValue())) {
            userMessage.getCollaborationInfo().setAgreementRef(null);
        }
        ebMSHeaderInfo.value = messagingMapper.messagingFromEntity(messagingWs);

        try {
            messageAcknowledgeExtService.acknowledgeMessageDeliveredWithUnsecureLoginAllowed(trimmedMessageId, new Timestamp(System.currentTimeMillis()));
        } catch (AuthenticationExtException | MessageAcknowledgeExtException e) {
            //if an error occurs related to the message acknowledgement do not block the download message operation
            LOG.error("Error acknowledging message [" + retrieveMessageRequest.getMessageID() + "]", e);
        }
    }

    private eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage getUserMessage(RetrieveMessageRequest retrieveMessageRequest, String trimmedMessageId) throws RetrieveMessageFault {
        eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage userMessage;
        try {
            userMessage = wsPlugin.downloadMessage(trimmedMessageId, null);
        } catch (final WSMessageLogNotFoundException wsmlnfEx) {
            LOG.businessError(DomibusMessageCode.BUS_MSG_NOT_FOUND, wsmlnfEx, trimmedMessageId);
            throw new RetrieveMessageFault(MESSAGE_NOT_FOUND_ID + trimmedMessageId + "]", webServicePluginExceptionFactory.createFault("No message with id [" + trimmedMessageId + "] pending for download"));
        } catch (final MessageNotFoundException mnfEx) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(MESSAGE_NOT_FOUND_ID + retrieveMessageRequest.getMessageID() + "]", mnfEx);
            }
            LOG.error(MESSAGE_NOT_FOUND_ID + retrieveMessageRequest.getMessageID() + "]");
            throw new RetrieveMessageFault(MESSAGE_NOT_FOUND_ID + trimmedMessageId + "]", webServicePluginExceptionFactory.createDownloadMessageFault(mnfEx));
        }

        if (userMessage == null) {
            LOG.error(MESSAGE_NOT_FOUND_ID + retrieveMessageRequest.getMessageID() + "]");
            throw new RetrieveMessageFault(MESSAGE_NOT_FOUND_ID + trimmedMessageId + "]", webServicePluginExceptionFactory.createFault("UserMessage not found"));
        }
        return userMessage;
    }

    private void fillInfoPartsForLargeFilesWs(Holder<RetrieveMessageResponse> retrieveMessageResponse, eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging messaging) {
        if (getPayloadInfo(messaging) == null || CollectionUtils.isEmpty(getPartInfo(messaging))) {
            LOG.info("No payload found for message [{}]", messaging.getUserMessage().getMessageInfo().getMessageId());
            return;
        }

        for (final eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.PartInfo partInfo : getPartInfo(messaging)) {
            eu.domibus.plugin.ws.webservice.ExtendedPartInfo extPartInfo = (eu.domibus.plugin.ws.webservice.ExtendedPartInfo) partInfo;
            boolean isPayloadSentAsReference = extPartInfo.getPartProperties().getProperty().stream()
                    .anyMatch(property -> MessageConstants.PAYLOAD_PROPERTY_FILE_PATH.equals(property.getName()));
            if (isPayloadSentAsReference) {
                LOG.debug("Payload won't include the file contents because the file was sent as reference");
                continue;
            }
            LargePayloadType payloadType = WEBSERVICE_OF.createLargePayloadType();
            if (extPartInfo.getPayloadDatahandler() != null) {
                LOG.debug("payloadDatahandler Content Type: [{}]", extPartInfo.getPayloadDatahandler().getContentType());
                payloadType.setValue(extPartInfo.getPayloadDatahandler());
            }
            if (extPartInfo.isInBody()) {
                retrieveMessageResponse.value.setBodyload(payloadType);
            } else {
                payloadType.setPayloadId(partInfo.getHref());
                retrieveMessageResponse.value.getPayload().add(payloadType);
            }
        }
    }

    private eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.PayloadInfo getPayloadInfo(eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging messaging) {
        if (messaging.getUserMessage() == null) {
            return null;
        }
        return messaging.getUserMessage().getPayloadInfo();
    }

    private List<eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.PartInfo> getPartInfo(eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging messaging) {
        eu.domibus.plugin.ws.generated.header.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.PayloadInfo payloadInfo = getPayloadInfo(messaging);
        if (payloadInfo == null) {
            return new ArrayList<>();
        }
        return payloadInfo.getPartInfo();
    }


    @Override
    public MessageStatus getStatus(final StatusRequest statusRequest) throws StatusFault {
        boolean isMessageIdNotEmpty = StringUtils.isNotEmpty(statusRequest.getMessageID());

        if (!isMessageIdNotEmpty) {
            LOG.error(MESSAGE_ID_EMPTY);
            throw new StatusFault(MESSAGE_ID_EMPTY, webServicePluginExceptionFactory.createFault("MessageId is empty"));
        }
        String trimmedMessageId = messageExtService.cleanMessageIdentifier(statusRequest.getMessageID());
        // cannot know the msh role unless we add it on StatusRequest class
        try {
            return MessageStatus.fromValue(wsPlugin.getMessageRetriever().getStatus(trimmedMessageId).name());
        } catch (final DuplicateMessageException exception) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(DUPLICATE_MESSAGE_ID + trimmedMessageId + "]", exception);
            }
            LOG.error(DUPLICATE_MESSAGE_ID + trimmedMessageId + "]");
            throw new StatusFault(DUPLICATE_MESSAGE_ID + trimmedMessageId + "]", webServicePluginExceptionFactory.createFault(exception.getMessage()));
        }
    }

    @Override
    public ErrorResultImplArray getMessageErrors(final GetErrorsRequest messageErrorsRequest) throws eu.domibus.plugin.webService.generated.GetMessageErrorsFault {

        String messageId = messageExtService.cleanMessageIdentifier(messageErrorsRequest.getMessageID());

        if (StringUtils.isEmpty(messageId)) {
            LOG.error(MESSAGE_ID_EMPTY);
            throw new eu.domibus.plugin.webService.generated.GetMessageErrorsFault(MESSAGE_ID_EMPTY, webServicePluginExceptionFactory.createFault("MessageId is empty"));
        }

        try {
            return transformFromErrorResults(wsPlugin.getMessageRetriever().getErrorsForMessage(messageErrorsRequest.getMessageID()));
        } catch (final MessageNotFoundException mnfEx) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(MESSAGE_NOT_FOUND_ID + messageId + "]", mnfEx);
            }
            throw new eu.domibus.plugin.webService.generated.GetMessageErrorsFault(MESSAGE_NOT_FOUND_ID + messageId + "].", webServicePluginExceptionFactory.createFault(mnfEx.getMessage()));
        } catch (final DuplicateMessageException exception) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(DUPLICATE_MESSAGE_ID + messageId + "]", exception);
            }
            throw new eu.domibus.plugin.webService.generated.GetMessageErrorsFault(DUPLICATE_MESSAGE_ID + messageId + "].", webServicePluginExceptionFactory.createFault(exception.getMessage()));
        } catch (Exception ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(ERROR_MESSAGE_ID + messageId + "]", ex);
            }
            throw new eu.domibus.plugin.webService.generated.GetMessageErrorsFault("Couldn't find message errors [" + messageId + "]", webServicePluginExceptionFactory.createFault(ex.getMessage()));
        }
    }

    public ErrorResultImplArray transformFromErrorResults(List<? extends ErrorResult> errors) {
        ErrorResultImplArray errorList = new ErrorResultImplArray();
        for (ErrorResult errorResult : errors) {
            ErrorResultImpl errorResultImpl = new ErrorResultImpl();
            errorResultImpl.setErrorCode(ErrorCode.fromValue(errorResult.getErrorCode().name()));
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
