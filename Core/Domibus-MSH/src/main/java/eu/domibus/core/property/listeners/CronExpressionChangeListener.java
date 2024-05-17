package eu.domibus.core.property.listeners;

import eu.domibus.api.exceptions.DomibusCoreErrorCode;
import eu.domibus.api.exceptions.DomibusCoreException;
import eu.domibus.api.multitenancy.Domain;
import eu.domibus.api.multitenancy.DomainService;
import eu.domibus.api.property.DomibusPropertyChangeListener;
import eu.domibus.api.scheduler.DomibusScheduler;
import eu.domibus.api.scheduler.DomibusSchedulerException;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eu.domibus.api.property.DomibusPropertyMetadataManagerSPI.*;
import static eu.domibus.core.scheduler.DomainSchedulerFactoryConfiguration.*;

/**
 * @author Ion Perpegel
 * @since 4.1.1
 * <p>
 * Handles the change of cron expression properties
 */
@Service
public class CronExpressionChangeListener implements DomibusPropertyChangeListener {

    private static final DomibusLogger LOGGER = DomibusLoggerFactory.getLogger(CronExpressionChangeListener.class);

    @Autowired
    protected DomainService domainService;

    @Autowired
    protected ApplicationContext applicationContext;

    //Lazy loading of DomibusScheduler and DomainService to avoid the circular dependency and the that jobs are not created in the database
    @Lazy
    @Autowired
    DomibusScheduler domibusScheduler;

    Map<String, String> propertyToJobMap = Stream.of(new String[][]{
            {DOMIBUS_CERTIFICATE_CHECK_CRON, "saveCertificateAndLogRevocationJob"},
            {DOMIBUS_PLUGIN_ACCOUNT_UNLOCK_CRON, "activateSuspendedPluginUsersJob"},
            {DOMIBUS_PLUGIN_PASSWORD_POLICIES_CHECK_CRON, "pluginUserPasswordPolicyAlertJob"},
            {DOMIBUS_PAYLOAD_TEMP_JOB_RETENTION_CRON, "temporaryPayloadRetentionJob"},
            {DOMIBUS_MSH_RETRY_CRON, "retryWorkerJob"},
            {DOMIBUS_RETENTION_WORKER_CRON_EXPRESSION, "retentionWorkerJob"},
            {DOMIBUS_ONGOING_MESSAGES_SANITIZING_WORKER_CRON, "ongoingMessagesSanitizingWorkerJob"},
            {DOMIBUS_MSH_PULL_CRON, "pullRequestWorkerJob"},
            {DOMIBUS_PULL_RETRY_CRON, "pullRetryWorkerJob"},
            {DOMIBUS_SPLIT_AND_JOIN_RECEIVE_EXPIRATION_CRON, "splitAndJoinExpirationJob"},
            {DOMIBUS_MONITORING_CONNECTION_CRON, "connectionMonitoringJob"},
            {DOMIBUS_MONITORING_CONNECTION_SELF_CRON, "connectionMonitoringSelfJob"},
            {DOMIBUS_ERRORLOG_CLEANER_CRON, "errorLogCleanerJob"},
            {DOMIBUS_EARCHIVE_CRON, EARCHIVE_CONTINUOUS_JOB},
            {DOMIBUS_EARCHIVE_SANITY_CRON,  EARCHIVE_SANITIZER_JOB},
            {DOMIBUS_EARCHIVE_RETENTION_CRON, EARCHIVE_CLEANUP_JOB},
            {DOMIBUS_MESSAGES_STUCK_CRON, "stuckMessagesJob"},
            {DOMIBUS_EARCHIVE_STUCK_CRON, "eArchivingStuckJob"},
    }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

    @Override
    public boolean handlesProperty(String propertyName) {
        return propertyToJobMap.containsKey(propertyName);
    }

    @Override
    public void propertyValueChanged(String domainCode, String propertyName, String propertyValue) {
        String jobName = propertyToJobMap.get(propertyName);
        if (jobName == null) {
            LOGGER.warn("Could not find the corresponding job for the property [{}]", propertyName);
            return;
        }

        final Domain domain = domainCode == null ? null : domainService.getDomain(domainCode);

        try {
            domibusScheduler.rescheduleJob(domain, jobName, propertyValue);
        } catch (DomibusSchedulerException ex) {
            LOGGER.error("Could not reschedule [{}] ", propertyName, ex);
            throw new DomibusCoreException(DomibusCoreErrorCode.DOM_001, "Could not reschedule job: " + jobName, ex);
        }
    }
}
