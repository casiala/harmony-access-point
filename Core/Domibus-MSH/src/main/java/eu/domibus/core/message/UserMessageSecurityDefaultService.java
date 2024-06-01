package eu.domibus.core.message;

import eu.domibus.api.message.UserMessageSecurityService;
import eu.domibus.api.messaging.MessageNotFoundException;
import eu.domibus.api.model.MSHRole;
import eu.domibus.api.model.UserMessage;
import eu.domibus.api.security.AuthUtils;
import eu.domibus.api.security.AuthenticationException;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.messaging.MessageConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Cosmin Baciu
 * @since 3.3
 */
@Service
public class UserMessageSecurityDefaultService implements UserMessageSecurityService {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(UserMessageSecurityDefaultService.class);

    protected final AuthUtils authUtils;
    protected final UserMessageServiceHelper userMessageServiceHelper;
    protected final UserMessageDao userMessageDao;

    public UserMessageSecurityDefaultService(AuthUtils authUtils, UserMessageServiceHelper userMessageServiceHelper, UserMessageDao userMessageDao) {
        this.authUtils = authUtils;
        this.userMessageServiceHelper = userMessageServiceHelper;
        this.userMessageDao = userMessageDao;
    }

    @Override
    public void checkMessageAuthorization(UserMessage userMessage) throws AuthenticationException {
        doCheckMessageAuthorization(userMessage);
    }

    /**
     * @param userMessage with set of {@link eu.domibus.api.model.MessageProperty}
     * @throws AuthenticationException if the authOriginalUser is not ORIGINAL_SENDER or FINAL_RECIPIENT of the {@link UserMessage}
     */
    @Override
    public void checkMessageAuthorizationWithUnsecureLoginAllowed(UserMessage userMessage) throws AuthenticationException {
        /* unsecured login allowed */
        if (authUtils.isUnsecureLoginAllowed()) {
            LOG.debug("Unsecured login is allowed");
            return;
        }

        doCheckMessageAuthorization(userMessage);
    }

    @Override
    public void checkMessageAuthorizationWithUnsecureLoginAllowed(UserMessage userMessage, String propertyName) {
        /* unsecured login allowed */
        if (authUtils.isUnsecureLoginAllowed()) {
            LOG.debug("Unsecured login is allowed");
            return;
        }

        String authOriginalUser = authUtils.getOriginalUserWithUnsecureLoginAllowed();
        LOG.debug("Check authorization as [{}]", authOriginalUser == null ? "super user" : authOriginalUser);

        if (StringUtils.isBlank(authOriginalUser)) {
            return;
        }
        LOG.trace("OriginalUser is [{}] not admin", authOriginalUser);
        /* check the message belongs to the authenticated user */
        String originalUser = userMessageServiceHelper.getPropertyValue(userMessage, propertyName);
        if (!StringUtils.equalsIgnoreCase(originalUser, authOriginalUser)) {
            LOG.debug("User [{}] is trying to submit/access a message having as final recipient: [{}]", authOriginalUser, originalUser);
            throw new AuthenticationException("You are not allowed to handle this message. You are authorized as [" + authOriginalUser + "]");
        }
    }

    @Override
    public void checkMessageAuthorizationWithUnsecureLoginAllowed(final Long messageEntityId) {
        UserMessage userMessage = userMessageDao.findByEntityId(messageEntityId);
        if (userMessage == null) {
            throw new MessageNotFoundException(messageEntityId);
        }
        checkMessageAuthorizationWithUnsecureLoginAllowed(userMessage);
    }

    @Override
    public void checkMessageAuthorization(String messageId, MSHRole mshRole) {
        UserMessage userMessage = userMessageDao.findByMessageId(messageId, mshRole);
        if (userMessage == null) {
            throw new MessageNotFoundException(messageId, mshRole);
        }
        doCheckMessageAuthorization(userMessage);
    }

    // we keep this for back-ward compatibility for ext services
    @Override
    public void checkMessageAuthorizationWithUnsecureLoginAllowed(String messageId) {
        UserMessage userMessage = userMessageDao.findByMessageId(messageId);
        if (userMessage == null) {
            throw new MessageNotFoundException(messageId);
        }
        checkMessageAuthorizationWithUnsecureLoginAllowed(userMessage);
    }

    public void checkMessageAuthorizationWithUnsecureLoginAllowed(String messageId, MSHRole mshRole) {
        UserMessage userMessage = userMessageDao.findByMessageId(messageId, mshRole);
        if (userMessage == null) {
            throw new MessageNotFoundException(messageId, mshRole);
        }
        checkMessageAuthorizationWithUnsecureLoginAllowed(userMessage);
    }

    // we keep this for back-ward compatibility for ext services
    @Override
    public void checkMessageAuthorization(String messageId) {
        UserMessage userMessage = userMessageDao.findByMessageId(messageId);
        if (userMessage == null) {
            throw new MessageNotFoundException(messageId);
        }

        doCheckMessageAuthorization(userMessage);
    }

    protected void doCheckMessageAuthorization(UserMessage userMessage) {
        String authOriginalUser = authUtils.getOriginalUserOrNullIfAdmin();
        List<String> propertyNames = new ArrayList<>();
        propertyNames.add(MessageConstants.ORIGINAL_SENDER);
        propertyNames.add(MessageConstants.FINAL_RECIPIENT);

        if (StringUtils.isBlank(authOriginalUser)) {
            LOG.trace("OriginalUser is [{}] is admin", authOriginalUser);
            return;
        }

        LOG.trace("OriginalUser is [{}] not admin", authOriginalUser);

        /* check the message belongs to the authenticated user */
        boolean found = false;
        for (String propertyName : propertyNames) {
            String originalUser = userMessageServiceHelper.getPropertyValue(userMessage, propertyName);
            if (StringUtils.equalsIgnoreCase(originalUser, authOriginalUser)) {
                found = true;
                break;
            }
        }
        if (!found) {
            LOG.debug("Could not validate originalUser for [{}]", authOriginalUser);
            throw new AuthenticationException("You are not allowed to handle this message [" + userMessage.getMessageId() + "]. You are authorized as [" + authOriginalUser + "]");
        }
        LOG.trace("Could validate originalUser for [{}]", authOriginalUser);
    }
}
