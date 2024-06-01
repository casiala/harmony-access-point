package eu.domibus.core.plugin.handler;

import eu.domibus.api.message.UserMessageSecurityService;
import eu.domibus.api.messaging.DuplicateMessageFoundException;
import eu.domibus.api.model.MSHRole;
import eu.domibus.api.model.MessageStatus;
import eu.domibus.api.model.UserMessage;
import eu.domibus.api.model.UserMessageLog;
import eu.domibus.api.security.AuthUtils;
import eu.domibus.api.usermessage.UserMessageDownloadEvent;
import eu.domibus.common.ErrorResult;
import eu.domibus.core.error.ErrorLogEntry;
import eu.domibus.core.error.ErrorLogService;
import eu.domibus.core.message.MessagingService;
import eu.domibus.core.message.UserMessageDefaultService;
import eu.domibus.core.message.UserMessageLogDefaultService;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.messaging.*;
import eu.domibus.plugin.Submission;
import eu.domibus.plugin.handler.MessageRetriever;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service used for retrieving messages (split from DatabaseMessageHandler)
 *
 * @author Ion Perpegel
 * @since 5.0
 */
@Service
public class MessageRetrieverImpl implements MessageRetriever {
    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(MessageRetrieverImpl.class);

    protected final UserMessageDefaultService userMessageService;

    private final MessagingService messagingService;

    private final UserMessageLogDefaultService userMessageLogService;

    private final ErrorLogService errorLogService;

    private final ApplicationEventPublisher applicationEventPublisher;

    private UserMessageSecurityService userMessageSecurityService;

    private final AuthUtils authUtils;

    public MessageRetrieverImpl(UserMessageDefaultService userMessageService, MessagingService messagingService, UserMessageLogDefaultService userMessageLogService,
                                ErrorLogService errorLogService, ApplicationEventPublisher applicationEventPublisher, UserMessageSecurityService userMessageSecurityService, AuthUtils authUtils) {
        this.userMessageService = userMessageService;
        this.messagingService = messagingService;
        this.userMessageLogService = userMessageLogService;
        this.errorLogService = errorLogService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.userMessageSecurityService = userMessageSecurityService;
        this.authUtils = authUtils;
    }

    @Override
    @Transactional
    public Submission downloadMessage(String messageId) throws MessageNotFoundException {
        return downloadMessage(messageId, true);
    }

    @Override
    @Transactional
    public Submission downloadMessage(final String messageId, boolean markAsDownloaded) throws MessageNotFoundException {
        checkMessageAuthorization(messageId, eu.domibus.common.MSHRole.RECEIVING);
        LOG.info("Downloading message with id [{}]", messageId);
        final UserMessage userMessage = userMessageService.getByMessageId(messageId, MSHRole.RECEIVING);
        if (markAsDownloaded) {
            markMessageAsDownloaded(messageId);
        }
        return messagingService.getSubmission(userMessage);
    }

    @Override
    @Transactional
    public Submission downloadMessage(final Long messageEntityId, boolean markAsDownloaded) throws MessageNotFoundException {
        return downloadMessage(messageEntityId);
    }

    @Override
    @Transactional
    public Submission downloadMessage(final Long messageEntityId) throws MessageNotFoundException {
        checkMessageAuthorization(messageEntityId);
        LOG.info("Downloading message with entity id [{}]", messageEntityId);
        final UserMessage userMessage = userMessageService.getByMessageEntityId(messageEntityId);

        checkMessageAuthorization(messageEntityId);

        markMessageAsDownloaded(userMessage);
        return messagingService.getSubmission(userMessage);
    }

    @Override
    public Submission browseMessage(String messageId) {
        checkMessageAuthorization(messageId);
        try {
            return browseMessage(messageId, eu.domibus.common.MSHRole.RECEIVING);
        } catch (eu.domibus.api.messaging.MessageNotFoundException ex) {
            LOG.info("Could not find message with id [{}] and RECEIVING role; trying the SENDING role.", messageId);
            return browseMessage(messageId, eu.domibus.common.MSHRole.SENDING);
        }
    }

    @Override
    public Submission browseMessage(String messageId, eu.domibus.common.MSHRole mshRole) throws eu.domibus.api.messaging.MessageNotFoundException {
        checkMessageAuthorization(messageId, mshRole);

        LOG.info("Browsing message with id [{}] and role [{}]", messageId, mshRole);

        MSHRole role = MSHRole.valueOf(mshRole.name());
        UserMessage userMessage = userMessageService.getByMessageId(messageId, role);

        return messagingService.getSubmission(userMessage);
    }

    @Override
    public Submission browseMessage(final Long messageEntityId) {
        checkMessageAuthorization(messageEntityId);

        LOG.info("Browsing message with entity id [{}]", messageEntityId);

        UserMessage userMessage = userMessageService.getByMessageEntityId(messageEntityId);

        return messagingService.getSubmission(userMessage);
    }

    @Override
    public eu.domibus.common.MessageStatus getStatus(final String messageId) throws DuplicateMessageException {
        try {
            userMessageSecurityService.checkMessageAuthorizationWithUnsecureLoginAllowed(messageId);
            final MessageStatus messageStatus = userMessageLogService.getMessageStatusById(messageId);
            return eu.domibus.common.MessageStatus.valueOf(messageStatus.name());
        } catch (eu.domibus.api.messaging.MessageNotFoundException exception) {
            return eu.domibus.common.MessageStatus.valueOf(MessageStatus.NOT_FOUND.name());
        } catch (DuplicateMessageFoundException exception) {
            throw new DuplicateMessageException(exception.getMessage());
        }
    }

    @Override
    public eu.domibus.common.MessageStatus getStatus(String messageId, eu.domibus.common.MSHRole mshRole) {
        try {
            MSHRole role = MSHRole.valueOf(mshRole.name());
            userMessageSecurityService.checkMessageAuthorizationWithUnsecureLoginAllowed(messageId, role);
            final MessageStatus messageStatus = userMessageLogService.getMessageStatus(messageId, role);
            return eu.domibus.common.MessageStatus.valueOf(messageStatus.name());
        } catch (eu.domibus.api.messaging.MessageNotFoundException exception) {
            return eu.domibus.common.MessageStatus.valueOf(MessageStatus.NOT_FOUND.name());
        }
    }

    @Override
    public eu.domibus.common.MessageStatus getStatus(final Long messageEntityId) {
        try {
            userMessageSecurityService.checkMessageAuthorizationWithUnsecureLoginAllowed(messageEntityId);
            final MessageStatus messageStatus = userMessageLogService.getMessageStatus(messageEntityId);
            return eu.domibus.common.MessageStatus.valueOf(messageStatus.name());
        } catch (eu.domibus.api.messaging.MessageNotFoundException exception) {
            return eu.domibus.common.MessageStatus.valueOf(MessageStatus.NOT_FOUND.name());
        }
    }

    @Override
    public List<? extends ErrorResult> getErrorsForMessage(final String messageId) throws MessageNotFoundException, DuplicateMessageException {
        UserMessageLog userMessageLog = null;
        try {
            userMessageSecurityService.checkMessageAuthorizationWithUnsecureLoginAllowed(messageId);
            userMessageLog = userMessageLogService.findByMessageId(messageId);
        } catch (DuplicateMessageFoundException e) {
            throw new DuplicateMessageException(e.getMessage(), e.getCause());
        }
        if (userMessageLog == null) {
            throw new MessageNotFoundException("Message [" + messageId + "] does not exist");
        }
        List<ErrorLogEntry> errorsForMessage = errorLogService.getErrorsForMessage(messageId);

        return errorsForMessage.stream().map(errorLogService::convert).collect(Collectors.toList());
    }

    @Override
    public List<? extends ErrorResult> getErrorsForMessage(String messageId, eu.domibus.common.MSHRole mshRole) throws MessageNotFoundException {
        MSHRole role = MSHRole.valueOf(mshRole.name());
        try {
            userMessageSecurityService.checkMessageAuthorizationWithUnsecureLoginAllowed(messageId, role);
        } catch (eu.domibus.api.messaging.MessageNotFoundException messageNotFoundException) {
            throw new MessageNotFoundException("Message [" + messageId + "]-[" + role + "] does not exist");
        }
        UserMessageLog userMessageLog = userMessageLogService.findByMessageId(messageId, role);
        List<? extends ErrorResult> errorResults = errorLogService.getErrors(messageId, role);
        if (userMessageLog == null && CollectionUtils.isEmpty(errorResults)) {
            throw new MessageNotFoundException("Message [" + messageId + "] does not exist");
        }
        return errorResults;
    }

    @Override
    public void markMessageAsDownloaded(String messageId) {
        checkMessageAuthorization(messageId, eu.domibus.common.MSHRole.RECEIVING);
        final UserMessage userMessage = userMessageService.getByMessageId(messageId, MSHRole.RECEIVING);
        markMessageAsDownloaded(userMessage);
    }

    protected void markMessageAsDownloaded(final UserMessage userMessage) {
        LOG.info("Setting the status of the message with id [{}] to downloaded", userMessage.getMessageId());
        final UserMessageLog messageLog = userMessageLogService.findById(userMessage.getEntityId());
        if (MessageStatus.DOWNLOADED == messageLog.getMessageStatus()) {
            LOG.debug("Message [{}] is already downloaded", userMessage.getMessageId());
        } else {
            MSHRole mshRole = userMessage.getMshRole().getRole();
            publishDownloadEvent(userMessage.getMessageId(), mshRole);
            userMessageLogService.setMessageAsDownloaded(userMessage, messageLog);
        }
    }

    /**
     * Publishes a download event to be caught in case of transaction rollback
     *
     * @param messageId message id of the message that is being downloaded
     * @param role
     */
    protected void publishDownloadEvent(String messageId, MSHRole role) {
        UserMessageDownloadEvent downloadEvent = new UserMessageDownloadEvent();
        downloadEvent.setMessageId(messageId);
        String roleName = role.name();
        downloadEvent.setMshRole(roleName);
        LOG.debug("Publishing [{}] for message [{}] and role [{}]", downloadEvent.getClass().getName(), messageId, roleName);
        applicationEventPublisher.publishEvent(downloadEvent);
    }

    protected void checkMessageAuthorization(String messageId, eu.domibus.common.MSHRole mshRole) {
        MSHRole role = MSHRole.valueOf(mshRole.name());
        checkMessageAuthorization(() -> userMessageService.getByMessageId(messageId, role));
    }

    protected void checkMessageAuthorization(Long messageEntityId) {
        checkMessageAuthorization(() -> userMessageService.getByMessageEntityId(messageEntityId));
    }

    protected void checkMessageAuthorization(String messageId) {
        checkMessageAuthorization(() -> userMessageService.getByMessageId(messageId));
    }

    protected void checkMessageAuthorization(Supplier<UserMessage> messageGetter) {
        checkUserRoleWithUnsecuredLoginAllowed();

        final UserMessage userMessage = messageGetter.get();
        userMessageSecurityService.checkMessageAuthorizationWithUnsecureLoginAllowed(userMessage, MessageConstants.FINAL_RECIPIENT);
    }

    private void checkUserRoleWithUnsecuredLoginAllowed() {
        if (authUtils.isUnsecureLoginAllowed()) {
            return;
        }
        authUtils.checkHasAdminRoleOrUserRoleWithOriginalUser();
    }
}
