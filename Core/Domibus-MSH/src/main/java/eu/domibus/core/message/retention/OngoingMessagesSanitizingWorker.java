package eu.domibus.core.message.retention;

import eu.domibus.api.alerts.AlertEvent;
import eu.domibus.api.alerts.AlertLevel;
import eu.domibus.api.alerts.PluginEventService;
import eu.domibus.api.multitenancy.Domain;
import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.api.security.AuthUtils;
import eu.domibus.api.util.DateUtil;
import eu.domibus.core.earchive.EArchiveBatchUserMessage;
import eu.domibus.core.message.UserMessageLogDao;
import eu.domibus.core.pmode.ConfigurationDAO;
import eu.domibus.core.scheduler.DomibusQuartzJobBean;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.apache.commons.lang3.time.DateUtils;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static eu.domibus.api.model.DomibusDatePrefixedSequenceIdGeneratorGenerator.MIN;
import static eu.domibus.api.property.DomibusPropertyMetadataManagerSPI.*;

/**
 * @author maierga
 * @since 5.0.5
 */

@DisallowConcurrentExecution
public class OngoingMessagesSanitizingWorker  extends DomibusQuartzJobBean {
    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(OngoingMessagesSanitizingWorker.class);
    public static final String MESSAGES = "messages";

    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private ConfigurationDAO configurationDAO;

    @Autowired
    private PluginEventService pluginEventService;

    @Autowired
    private DomibusPropertyProvider domibusPropertyProvider;

    @Autowired
    OngoingMessageSanitizingService ongoingMessageSanitizingService;

    @Override
    protected void executeJob(JobExecutionContext context, Domain domain) throws JobExecutionException {
        LOG.debug("OngoingMessagesSanitizingWorker to be executed");
        authUtils.runWithSecurityContext(this::executeJob, "retention_user", "retention_password");
    }

    protected void executeJob() {
        if (!configurationDAO.configurationExists()) {
            LOG.debug("Missing pMode configuration.");
            return;
        }

        List<EArchiveBatchUserMessage> messagesNotFinalAsc = ongoingMessageSanitizingService.findOngoingMessagesWhichAreNotProcessedAnymore();
        if(messagesNotFinalAsc.isEmpty()){
            LOG.debug("No ongoing messages found that are not still being processed.");
            return;
        }

        String messageIds = messagesNotFinalAsc.stream()
                .map(message -> String.format("%s (%s)", message.getMessageId(), message.getMessageStatus()))
                .collect(Collectors.joining(","));
        LOG.debug("Found these ongoing messages that are no longer being processed: {}", messageIds);

        String alertLevel = domibusPropertyProvider.getProperty(DOMIBUS_ONGOING_MESSAGES_SANITIZING_ALERT_LEVEL);
        String subject = domibusPropertyProvider.getProperty(DOMIBUS_ONGOING_MESSAGES_SANITIZING_ALERT_SUBJECT);
        String body = domibusPropertyProvider.getProperty(DOMIBUS_ONGOING_MESSAGES_SANITIZING_ALERT_BODY);

        AlertEvent alertEvent = new AlertEvent();
        alertEvent.setAlertLevel(AlertLevel.valueOf(alertLevel));
        alertEvent.setEmailBody(body.replace("{" + MESSAGES + "}", messageIds));
        alertEvent.setEmailSubject(subject);
        alertEvent.setName(subject);
        //alertEvent.setEventType(EventType.OLD_ONGOING_MESSAGES); // TODO Francois GAUTIER 21/06/2023 EDELIVERY-11342

        LOG.info("Sending ongoing message alert [{}]", alertEvent);

        pluginEventService.enqueueMessageEvent(alertEvent);
    }
}
