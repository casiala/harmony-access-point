package eu.domibus.core.ebms3.receiver.leg;

import eu.domibus.api.ebms3.model.Ebms3Messaging;
import eu.domibus.api.exceptions.DomibusCoreErrorCode;
import eu.domibus.api.message.acknowledge.MessageAcknowledgeException;
import eu.domibus.api.model.MSHRole;
import eu.domibus.common.model.configuration.LegConfiguration;
import eu.domibus.core.ebms3.EbMS3Exception;
import eu.domibus.core.message.MessageExchangeService;
import eu.domibus.core.message.UserMessageDao;
import eu.domibus.core.pmode.provider.PModeProvider;
import eu.domibus.api.model.UserMessage;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.apache.cxf.binding.soap.SoapMessage;

/**
 * @author Thomas Dussart
 * @since 3.3
 */

public class ReceiptLegConfigurationExtractor extends AbstractSignalLegConfigurationExtractor {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(ReceiptLegConfigurationExtractor.class);

    private UserMessageDao userMessageDao;
    private PModeProvider pModeProvider;
    private MessageExchangeService messageExchangeService;

    public ReceiptLegConfigurationExtractor(SoapMessage message, Ebms3Messaging messaging) {
        super(message, messaging);
    }

    @Override
    protected String getMessageId() {
        return ebms3Messaging.getSignalMessage().getMessageInfo().getMessageId();
    }

    @Override
    public LegConfiguration process() throws EbMS3Exception {
        LOG.debug("Extracting configuration for receipt");
        String messageId = ebms3Messaging.getSignalMessage().getMessageInfo().getRefToMessageId();
        //@thom check it the MessageAcknolegde service is not a better choice here. The getMessage is not exposed via the api.
        final UserMessage userMessage = userMessageDao.findByMessageId(messageId, MSHRole.SENDING);
        if (userMessage == null) {
            throw new MessageAcknowledgeException(DomibusCoreErrorCode.DOM_001, "Message with ID [" + messageId + "] does not exist");
        }
        String pModeKey;
        if(userMessage.isSplitAndJoin()) {
            pModeKey = pModeProvider.findUserMessageExchangeContext(userMessage, MSHRole.SENDING).getPmodeKey();
            LOG.debug("Exchange context (split and join context), pModeKey [{}]", pModeKey);
        } else {
            pModeKey = pModeProvider.findUserMessageExchangeContext(userMessage, MSHRole.SENDING, true).getPmodeKey();
            LOG.debug("Exchange context (pull exchange), pModeKey [{}]", pModeKey);
        }
        setUpMessage(pModeKey);
        return pModeProvider.getLegConfiguration(pModeKey);
    }

    @Override
    public void accept(MessageLegConfigurationVisitor visitor) {
        visitor.visit(this);
    }


    public void setUserMessageDao(UserMessageDao userMessageDao) {
        this.userMessageDao = userMessageDao;
    }

    public void setpModeProvider(PModeProvider pModeProvider) {
        this.pModeProvider = pModeProvider;
    }

    public void setMessageExchangeService(MessageExchangeService messageExchangeService) {
        this.messageExchangeService = messageExchangeService;
    }
}
