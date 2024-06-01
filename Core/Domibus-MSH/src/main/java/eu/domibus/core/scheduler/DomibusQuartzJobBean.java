package eu.domibus.core.scheduler;

import eu.domibus.api.multitenancy.Domain;
import eu.domibus.api.multitenancy.DomainContextProvider;
import eu.domibus.api.multitenancy.DomainService;
import eu.domibus.api.security.AuthUtils;
import eu.domibus.api.util.DatabaseUtil;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * @author Cosmin Baciu
 * @since 4.0
 */
public abstract class DomibusQuartzJobBean extends QuartzJobBean {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(DomibusQuartzJobBean.class);

    public static final String DOMIBUS_QUARTZ_USER = "domibus-quartz";

    public static final String DOMIBUS_QUARTZ_PASSWORD = "domibus-quartz"; //NOSONAR

    @Autowired
    protected DomainService domainService;

    @Autowired
    protected DomainContextProvider domainContextProvider;

    @Autowired
    protected DatabaseUtil databaseUtil;

    @Autowired
    protected AuthUtils authUtils;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            LOG.clearCustomKeys();
            final Domain currentDomain = getDomain(context);
            domainContextProvider.setCurrentDomain(currentDomain);
            setQuartzJobSecurityContext();
            LOG.putMDC(DomibusLogger.MDC_USER, databaseUtil.getDatabaseUserName());
            executeJob(context, currentDomain);
        } catch (Exception e) {
            LOG.error("Error exception while executing job [{}]:[{}]", context.getJobDetail().getKey().getName(), context.getJobDetail().getJobClass(), e);
            throw new JobExecutionException(e);
        } finally {
            domainContextProvider.clearCurrentDomain();
            LOG.clearCustomKeys();
            authUtils.clearSecurityContext();
        }
    }

    public void setQuartzJobSecurityContext() {
        authUtils.setAuthenticationToSecurityContext(DOMIBUS_QUARTZ_USER, DOMIBUS_QUARTZ_PASSWORD);
    }

    protected Domain getDomain(JobExecutionContext context) throws JobExecutionException {
        try {
            final String schedulerName = context.getScheduler().getSchedulerName();
            return domainService.getDomainForScheduler(schedulerName);
        } catch (SchedulerException e) {
            throw new JobExecutionException("Could not get Quartz Scheduler", e);
        }
    }

    protected abstract void executeJob(final JobExecutionContext context, final Domain domain) throws JobExecutionException;

}
