package eu.domibus.plugin.ws.backend.dispatch;

import eu.domibus.ext.domain.metrics.Counter;
import eu.domibus.ext.domain.metrics.Timer;
import eu.domibus.ext.services.DomibusPropertyExtService;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.plugin.ws.backend.WSBackendMessageLogEntity;
import eu.domibus.plugin.ws.backend.WSBackendMessageStatus;
import eu.domibus.plugin.ws.backend.WSBackendMessageType;
import eu.domibus.plugin.ws.backend.reliability.WSPluginBackendReliabilityService;
import eu.domibus.plugin.ws.backend.rules.WSPluginDispatchRule;
import eu.domibus.plugin.ws.backend.rules.WSPluginDispatchRulesService;
import eu.domibus.plugin.ws.connector.WSPluginImpl;
import eu.domibus.plugin.ws.exception.WSMessageLogNotFoundException;
import eu.domibus.plugin.ws.exception.WSPluginException;
import eu.domibus.plugin.ws.message.WSMessageLogService;
import org.springframework.stereotype.Service;

import static eu.domibus.plugin.ws.property.WSPluginPropertyManager.PUSH_MARK_AS_DOWNLOADED;

/**
 * Common logic for sending messages to C1/C4 from WS Plugin
 *
 * @author François Gautier
 * @since 5.0
 */
@Service
public class WSPluginMessageSender {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(WSPluginMessageSender.class);

    protected final WSPluginBackendReliabilityService reliabilityService;

    protected final WSPluginDispatchRulesService rulesService;

    protected final WSPluginMessageBuilder messageBuilder;

    protected final WSPluginDispatcher dispatcher;

    protected final WSPluginImpl wsPlugin;

    private final DomibusPropertyExtService domibusPropertyExtService;
    private WSMessageLogService wsMessageLogService;

    public WSPluginMessageSender(WSPluginBackendReliabilityService reliabilityService,
                                 WSPluginDispatchRulesService rulesService,
                                 WSPluginMessageBuilder messageBuilder,
                                 WSPluginDispatcher dispatcher,
                                 WSPluginImpl wsPlugin, DomibusPropertyExtService domibusPropertyExtService,
                                 WSMessageLogService wsMessageLogService) {
        this.reliabilityService = reliabilityService;
        this.rulesService = rulesService;
        this.messageBuilder = messageBuilder;
        this.dispatcher = dispatcher;
        this.wsPlugin = wsPlugin;
        this.domibusPropertyExtService = domibusPropertyExtService;
        this.wsMessageLogService = wsMessageLogService;
    }

    /**
     * Send notification to the backend service with reliability feature
     *
     * @param backendMessage persisted message
     */
    @Timer(clazz = WSPluginMessageSender.class, value = "wsplugin_outgoing_backend_message_notification")
    @Counter(clazz = WSPluginMessageSender.class, value = "wsplugin_outgoing_backend_message_notification")
    public void sendNotification(final WSBackendMessageLogEntity backendMessage) {
        LOG.debug("Rule [{}] Send backend notification [{}] for backend message entity id [{}]",
                backendMessage.getRuleName(),
                backendMessage.getType(),
                backendMessage.getEntityId());
        WSPluginDispatchRule dispatchRule = null;
        String messageId = null;
        try {
            dispatchRule = rulesService.getRule(backendMessage.getRuleName());
            String endpoint = dispatchRule.getEndpoint();
            LOG.debug("Endpoint identified: [{}]", endpoint);
            dispatcher.dispatch(messageBuilder.buildSOAPMessage(backendMessage), endpoint);
            backendMessage.setBackendMessageStatus(WSBackendMessageStatus.SENT);
            messageId = backendMessage.getMessageId();
            LOG.info("Backend notification [{}] for domibus id [{}] sent to [{}] successfully",
                    backendMessage.getType(),
                    messageId,
                    endpoint);

            if (backendMessage.getType() == WSBackendMessageType.SUBMIT_MESSAGE) {
                boolean markAsDownloaded = domibusPropertyExtService.getBooleanProperty(PUSH_MARK_AS_DOWNLOADED);
                LOG.debug("Found the property [{}] set to [{}]", PUSH_MARK_AS_DOWNLOADED, markAsDownloaded);
                wsPlugin.downloadMessage(messageId, null, markAsDownloaded);
            }
        } catch (final WSMessageLogNotFoundException wsmlnfEx) {
            LOG.warn("WSMessageLogEntity not found for message id [" + messageId + "]", wsmlnfEx);
        } catch (Throwable t) {//NOSONAR: Catching Throwable is done on purpose in order to even catch out of memory exceptions.
            LOG.error("Error occurred when sending backend message with ID [{}]", backendMessage.getEntityId(), t);
            if (dispatchRule == null) {
                throw new WSPluginException("No dispatch rule found for backend message id [" + backendMessage.getEntityId() + "]");
            }
            reliabilityService.handleReliability(backendMessage, dispatchRule);
        }
    }

}
