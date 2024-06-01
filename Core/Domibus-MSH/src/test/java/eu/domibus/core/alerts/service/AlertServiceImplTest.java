package eu.domibus.core.alerts.service;

import com.google.common.collect.Lists;
import eu.domibus.api.alerts.AlertLevel;
import eu.domibus.api.jms.JMSManager;
import eu.domibus.api.model.MessageStatus;
import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.api.server.ServerInfoService;
import eu.domibus.api.util.DateUtil;
import eu.domibus.core.alerts.configuration.common.AlertConfigurationService;
import eu.domibus.core.alerts.configuration.common.AlertConfigurationServiceImpl;
import eu.domibus.core.alerts.configuration.common.AlertModuleConfiguration;
import eu.domibus.core.alerts.configuration.global.CommonConfigurationManager;
import eu.domibus.core.alerts.dao.AlertDao;
import eu.domibus.core.alerts.dao.EventDao;
import eu.domibus.core.alerts.model.common.AlertCriteria;
import eu.domibus.core.alerts.model.common.AlertStatus;
import eu.domibus.core.alerts.model.common.AlertType;
import eu.domibus.core.alerts.model.common.EventType;
import eu.domibus.core.alerts.model.persist.AbstractEventProperty;
import eu.domibus.core.alerts.model.persist.StringEventProperty;
import eu.domibus.core.alerts.model.service.Alert;
import eu.domibus.core.alerts.model.service.Event;
import eu.domibus.core.alerts.model.service.MailModel;
import eu.domibus.core.converter.AlertCoreMapper;
import eu.domibus.core.scheduler.ReprogrammableService;
import mockit.*;
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.matchers.GreaterThan;

import javax.jms.Queue;
import java.text.ParseException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

import static eu.domibus.api.property.DomibusPropertyMetadataManagerSPI.DOMIBUS_ALERT_RETRY_MAX_ATTEMPTS;
import static eu.domibus.api.property.DomibusPropertyMetadataManagerSPI.DOMIBUS_ALERT_RETRY_TIME;
import static eu.domibus.core.alerts.model.common.MessageEvent.MESSAGE_ID;
import static eu.domibus.core.alerts.model.common.MessageEvent.OLD_STATUS;
import static eu.domibus.core.alerts.configuration.common.AlertConfigurationServiceImpl.DOMIBUS_ALERT_SUPER_INSTANCE_NAME_SUBJECT;
import static eu.domibus.core.alerts.service.AlertServiceImpl.*;
import static java.util.Optional.of;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.*;

/**
 * @author Thomas Dussart
 * @since 4.0
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "AccessStaticViaInstance"})
@RunWith(JMockit.class)
public class AlertServiceImplTest {

    public static final String SUBJECT = "subject";
    public static final String ALERT_DESCRIPTION_TEST = "Alert description";

    @Tested
    AlertServiceImpl alertService;

    @Injectable
    private EventDao eventDao;

    @Injectable
    private AlertDao alertDao;

    @Injectable
    private DomibusPropertyProvider domibusPropertyProvider;

    @Injectable
    private AlertCoreMapper alertCoreMapper;

    @Injectable
    private JMSManager jmsManager;

    @Injectable
    private Queue alertMessageQueue;

    @Injectable
    private AlertConfigurationService alertConfigurationService;

    @Injectable
    private ServerInfoService serverInfoService;

    @Injectable
    private CommonConfigurationManager commonConfigurationManager;

    @Injectable
    private ReprogrammableService reprogrammableService;

    @Test
    public void createAlertOnEvent(@Injectable AlertModuleConfiguration config) {
        final Event event = new Event();
        event.setEntityId(1);
        event.setType(EventType.MSG_STATUS_CHANGED);

        final eu.domibus.core.alerts.model.persist.Event eventEntity = new eu.domibus.core.alerts.model.persist.Event();
        new Expectations() {{
            eventDao.read(event.getEntityId());
            result = eventEntity;

            domibusPropertyProvider.getIntegerProperty(DOMIBUS_ALERT_RETRY_MAX_ATTEMPTS);
            result = 5;

            alertConfigurationService.getConfiguration(AlertType.MSG_STATUS_CHANGED);
            result = config;

            config.isActive();
            result = true;

            config.getAlertLevel(event);
            result = AlertLevel.HIGH;
        }};

        alertService.createAlertOnEvent(event);

        new VerificationsInOrder() {{
            eu.domibus.core.alerts.model.persist.Alert alert;
            alertDao.create(alert = withCapture());
            times = 1;
            assertEquals(AlertType.MSG_STATUS_CHANGED, alert.getAlertType());
            assertEquals(0, alert.getAttempts(), 0);
            assertEquals(5, alert.getMaxAttempts(), 0);
            assertEquals(AlertStatus.SEND_ENQUEUED, alert.getAlertStatus());
            assertNotNull(alert.getCreationTime());
            assertNull(alert.getReportingTime());
            assertEquals(AlertLevel.HIGH, alert.getAlertLevel());
            assertTrue(alert.getEvents().contains(eventEntity));
            alertCoreMapper.alertPersistToAlertService(alert);
            times = 1;
        }};
    }

    @Test
    public void createAlertOnPluginEvent(@Injectable Event event, @Injectable Alert alert) {
        final eu.domibus.core.alerts.model.persist.Event eventEntity = new eu.domibus.core.alerts.model.persist.Event();
        new Expectations(alertService) {{
            event.getEntityId();
            result = 1L;

            event.getType();
            result = EventType.PLUGIN;

            eventDao.read(event.getEntityId());
            result = eventEntity;

            event.findOptionalProperty(ALERT_NAME);
            result = of(ALERT_NAME);

            event.findOptionalProperty(ALERT_LEVEL);
            result = of(AlertLevel.MEDIUM.name());

            event.findOptionalProperty(ALERT_ACTIVE);
            result = of(Boolean.TRUE.toString());

            domibusPropertyProvider.getIntegerProperty(DOMIBUS_ALERT_RETRY_MAX_ATTEMPTS);
            result = 5;

            alertCoreMapper.alertPersistToAlertService((eu.domibus.core.alerts.model.persist.Alert) any);
            result = alert;
            alertService.enqueueAlert(alert);
        }};

        alertService.createAndEnqueueAlertOnPluginEvent(event);

        new FullVerifications() {{
            eu.domibus.core.alerts.model.persist.Alert alert;
            alertDao.create(alert = withCapture());
            times = 1;
            assertEquals(AlertType.PLUGIN, alert.getAlertType());
            assertEquals(0, alert.getAttempts(), 0);
            assertEquals(5, alert.getMaxAttempts(), 0);
            assertEquals(AlertStatus.SEND_ENQUEUED, alert.getAlertStatus());
            assertNotNull(alert.getCreationTime());
            assertNull(alert.getReportingTime());
            assertEquals(AlertLevel.MEDIUM, alert.getAlertLevel());
            assertTrue(alert.getEvents().contains(eventEntity));
        }};
    }

    @Test
    public void createAlertOnPluginEvent_notActive(@Injectable Event event) {
        final eu.domibus.core.alerts.model.persist.Event eventEntity = new eu.domibus.core.alerts.model.persist.Event();
        new Expectations(alertService) {{
            event.getEntityId();
            result = 1L;

            eventDao.read(event.getEntityId());
            result = eventEntity;

            event.findOptionalProperty(ALERT_NAME);
            result = of(ALERT_NAME);

            event.findOptionalProperty(ALERT_LEVEL);
            result = of(AlertLevel.MEDIUM.name());

            event.findOptionalProperty(ALERT_ACTIVE);
            result = of(Boolean.FALSE.toString());

            event.getType();
            result = EventType.PLUGIN;
        }};

        alertService.createAndEnqueueAlertOnPluginEvent(event);

        new FullVerifications() {
        };
    }

    @Test
    public void createAlertOnPluginEvent_noAlertLevel(@Injectable Event event) {
        final eu.domibus.core.alerts.model.persist.Event eventEntity = new eu.domibus.core.alerts.model.persist.Event();
        new Expectations(alertService) {{
            event.getEntityId();
            result = 1L;

            eventDao.read(event.getEntityId());
            result = eventEntity;

            event.findOptionalProperty(ALERT_LEVEL);
            result = Optional.empty();

            event.findOptionalProperty(ALERT_NAME);
            result = of(ALERT_NAME);

            event.findOptionalProperty(ALERT_ACTIVE);
            result = of(Boolean.TRUE.toString());

            event.getType();
            result = EventType.PLUGIN;
        }};

        alertService.createAndEnqueueAlertOnPluginEvent(event);

        new Verifications() {
        };
    }

    @Test
    public void enqueueAlert() {
        Alert alert = new Alert();

        alertService.enqueueAlert(alert);

        new Verifications() {{
            jmsManager.convertAndSendToQueue(alert, alertMessageQueue, ALERT_SELECTOR);
        }};
    }

    @Test
    public void getMailModelForAlert() {
        final String mailSubject = "Message failure";
        final String mailSubjectServerName = "localhost";
        final String alertSuperInstanceNameSubjectProperty = AlertConfigurationServiceImpl.DOMIBUS_ALERT_SUPER_INSTANCE_NAME_SUBJECT;
        final String messageId = "messageId";
        final long entityId = 1;
        final AlertType alertType = AlertType.MSG_STATUS_CHANGED;
        final AlertLevel alertLevel = AlertLevel.HIGH;

        Alert alert = new Alert();
        alert.setEntityId(entityId);

        final eu.domibus.core.alerts.model.persist.Alert persistedAlert = new eu.domibus.core.alerts.model.persist.Alert();
        persistedAlert.setAlertType(alertType);
        persistedAlert.setAlertLevel(alertLevel);

        final eu.domibus.core.alerts.model.persist.Event event = new eu.domibus.core.alerts.model.persist.Event();
        persistedAlert.addEvent(event);

        final StringEventProperty messageIdProperty = new StringEventProperty();
        messageIdProperty.setStringValue(messageId);
        event.addProperty(MESSAGE_ID.name(), messageIdProperty);

        final StringEventProperty oldStatusProperty = new StringEventProperty();
        oldStatusProperty.setStringValue(MessageStatus.SEND_ENQUEUED.name());
        event.addProperty(OLD_STATUS.name(), oldStatusProperty);
        new Expectations() {{
            alertDao.read(entityId);
            result = persistedAlert;

            alertConfigurationService.getMailSubject(alertType);
            result = mailSubject;

            domibusPropertyProvider.getProperty(alertSuperInstanceNameSubjectProperty);
            result = mailSubjectServerName;
        }};

        final MailModel<Map<String, String>> mailModelForAlert = alertService.getMailModelForAlert(alert);

        assertEquals(mailSubject + "[" + mailSubjectServerName + "]", mailModelForAlert.getSubject());
        assertEquals(alertType.getTemplate(), mailModelForAlert.getTemplatePath());
        final Map<String, String> model = mailModelForAlert.getModel();
        assertEquals(messageId, model.get(MESSAGE_ID.name()));
        assertEquals(MessageStatus.SEND_ENQUEUED.name(), model.get(OLD_STATUS.name()));
        assertEquals(alertLevel.name(), model.get(ALERT_LEVEL));
        assertNotNull(DateUtil.DEFAULT_FORMATTER.parse(model.get(REPORTING_TIME)));
        assertEquals("[" + alertType.getTitle() + "] ", model.get(DESCRIPTION));
    }

    @Test
    public void handleAlertStatusSuccess(final @Injectable eu.domibus.core.alerts.model.persist.Alert persistedAlert) {
        final Alert alert = new Alert();
        final long entityId = 1;
        alert.setEntityId(entityId);
        alert.setAlertStatus(AlertStatus.SUCCESS);
        new Expectations() {{
            alertDao.read(entityId);
            times = 1;
            result = persistedAlert;
            persistedAlert.getAlertStatus();
            result = AlertStatus.SUCCESS;
        }};

        alertService.handleAlertStatus(alert);

        new VerificationsInOrder() {{
            persistedAlert.setAlertStatus(AlertStatus.SUCCESS);
            times = 1;
            times = 1;
            reprogrammableService.removeRescheduleInfo(persistedAlert);
            persistedAlert.setReportingTime(withAny(new Date()));
            times = 1;
        }};
    }

    @Test
    public void handleAlertStatusFailedWithRemainingAttempts(final @Injectable eu.domibus.core.alerts.model.persist.Alert persistedAlert) {
        final int nextAttemptInMinutes = 1;
        final Alert alert = new Alert();
        final long entityId = 1;
        alert.setEntityId(entityId);
        alert.setAlertStatus(AlertStatus.FAILED);
        ZonedDateTime dateTime = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(nextAttemptInMinutes);
        Date nextAttempt = Date.from(dateTime.toInstant());


        new Expectations() {{
            alertDao.read(entityId);
            times = 1;
            result = persistedAlert;

            persistedAlert.getAlertStatus();
            result = AlertStatus.FAILED;

            persistedAlert.getAttempts();
            result = 0;

            persistedAlert.getMaxAttempts();
            result = 2;

            domibusPropertyProvider.getIntegerProperty(DOMIBUS_ALERT_RETRY_TIME);
            result = nextAttemptInMinutes;
        }};

        alertService.handleAlertStatus(alert);

        new VerificationsInOrder() {{
            persistedAlert.setAlertStatus(AlertStatus.FAILED);
            times = 1;
            reprogrammableService.setRescheduleInfo(persistedAlert, withArgThat(greaterThan(nextAttempt)));
            persistedAlert.setAttempts(1);
            times = 1;
            persistedAlert.setAlertStatus(AlertStatus.RETRY);
            times = 1;
        }};
    }

    @Test
    public void handleAlertStatus_notfound() {
        final Alert alert = new Alert();
        final long entityId = 1;
        alert.setEntityId(entityId);
        alert.setAlertStatus(AlertStatus.FAILED);

        new Expectations() {{
            alertDao.read(entityId);
            times = 1;
            result = null;
        }};

        alertService.handleAlertStatus(alert);

        new FullVerifications() {
        };
    }

    @Test
    public void handleAlertStatusFailedWithNoMoreAttempts(
            final @Injectable eu.domibus.core.alerts.model.persist.Alert persistedAlert) throws ParseException {
        final Alert alert = new Alert();
        final long entityId = 1;
        alert.setEntityId(entityId);
        alert.setAlertStatus(AlertStatus.FAILED);
        ZonedDateTime dateTime = ZonedDateTime.now(ZoneOffset.UTC);
        Date failureTime = Date.from(dateTime.toInstant());


        new Expectations() {{
            alertDao.read(entityId);
            times = 1;
            result = persistedAlert;

            persistedAlert.getAttempts();
            result = 1;

            persistedAlert.getMaxAttempts();
            result = 2;

            persistedAlert.getAlertStatus();
            result = AlertStatus.FAILED;

        }};

        alertService.handleAlertStatus(alert);

        new VerificationsInOrder() {{
            persistedAlert.setAlertStatus(AlertStatus.FAILED);
            times = 1;
            reprogrammableService.removeRescheduleInfo(persistedAlert);

            persistedAlert.setReportingTimeFailure(withArgThat(greaterThan(failureTime)));
            times = 1;

            persistedAlert.setAttempts(2);
            times = 1;
        }};
    }

    @Test
    public void retrieveAndResendFailedAlerts() {
        eu.domibus.core.alerts.model.persist.Alert firstRetryAlert = new eu.domibus.core.alerts.model.persist.Alert();
        firstRetryAlert.setEntityId(1);
        eu.domibus.core.alerts.model.persist.Alert secondRetryAlert = new eu.domibus.core.alerts.model.persist.Alert();
        firstRetryAlert.setEntityId(2);
        final Alert firstConvertedAlert = new Alert();
        final Alert secondConvertedAlert = new Alert();
        final List<eu.domibus.core.alerts.model.persist.Alert> alerts = Lists.newArrayList(firstRetryAlert);
        alerts.add(secondRetryAlert);
        new Expectations() {{
            alertDao.findRetryAlerts();
            result = alerts;
            alertCoreMapper.alertPersistToAlertService(firstRetryAlert);
            result = firstConvertedAlert;
            alertCoreMapper.alertPersistToAlertService(secondRetryAlert);
            result = secondConvertedAlert;
        }};

        alertService.retrieveAndResendFailedAlerts();

        new Verifications() {{
            jmsManager.convertAndSendToQueue(withAny(new Alert()), alertMessageQueue, ALERT_SELECTOR);
            times = 2;

        }};
    }

    @Test
    public void findAlerts() {
        final AlertCriteria alertCriteria = new AlertCriteria();
        final ArrayList<eu.domibus.core.alerts.model.persist.Alert> alerts = Lists.newArrayList(new eu.domibus.core.alerts.model.persist.Alert());
        new Expectations() {{
            alertDao.filterAlerts(alertCriteria);
            result = alerts;
        }};

        alertService.findAlerts(alertCriteria);
        new Verifications() {{
            alertCoreMapper.alertPersistListToAlertServiceList(alerts);
            times = 1;
        }};

    }

    @Test
    public void countAlerts() {
        final AlertCriteria alertCriteria = new AlertCriteria();

        alertService.countAlerts(alertCriteria);

        new Verifications() {{
            alertDao.countAlerts(alertCriteria);
            times = 1;
        }};
    }

    @Test
    public void cleanAlerts(@Injectable eu.domibus.core.alerts.model.persist.Alert alert) {
        Integer alertLifeTimeInDays = 10;
        ZonedDateTime dateTime = ZonedDateTime.now(ZoneOffset.UTC).minusDays(alertLifeTimeInDays).withHour(0).withMinute(0).withSecond(0).withNano(0);
        Date alertLimitDate = Date.from(dateTime.toInstant());

        new Expectations() {{
            commonConfigurationManager.getConfiguration().getAlertLifeTimeInDays();
            result = alertLifeTimeInDays;

        }};

        alertService.cleanAlerts();

        new Verifications() {{
            alertDao.deleteAlerts(alertLimitDate);
            times = 1;
        }};
    }

    @Test
    public void updateAlertProcessed() {
        final Alert firstAlert = new Alert();
        final long firstEntityId = 1;
        firstAlert.setEntityId(firstEntityId);
        firstAlert.setProcessed(false);
        List<Alert> alerts = Lists.newArrayList(firstAlert);
        final Alert secondAlert = new Alert();
        secondAlert.setProcessed(true);
        final long secondEntityId = 2;
        secondAlert.setEntityId(secondEntityId);
        alerts.add(secondAlert);

        alertService.updateAlertProcessed(alerts);

        new Verifications() {{
            List<Long> entityIds = new ArrayList<>();
            List<Boolean> processeds = new ArrayList<>();
            alertDao.updateAlertProcessed(withCapture(entityIds), withCapture(processeds));
            assertEquals(firstEntityId, entityIds.get(0), 0);
            assertEquals(secondEntityId, entityIds.get(1), 0);
            assertEquals(false, processeds.get(0));
            assertEquals(true, processeds.get(1));
        }};
    }

    @Test
    public void testFindAlerts(final @Injectable AlertCriteria alertCriteria, final @Injectable List<eu.domibus.core.alerts.model.persist.Alert> alerts) {
        new Expectations() {{
            alertDao.filterAlerts(alertCriteria);
            result = alerts;
        }};

        alertService.findAlerts(alertCriteria);

        new Verifications() {{
            alertDao.filterAlerts(alertCriteria);
            times = 1;
            alertCoreMapper.alertPersistListToAlertServiceList(alerts);
        }};
    }

    @Test
    public void deleteAlert(@Injectable eu.domibus.core.alerts.model.persist.Alert alert,
                            @Injectable eu.domibus.core.alerts.model.persist.Event event1,
                            @Injectable eu.domibus.core.alerts.model.persist.Event event2) {

        final HashSet<eu.domibus.core.alerts.model.persist.Event> eventsParam = new HashSet<>(Arrays.asList(event1, event2));

        new Expectations() {{
            alert.getEvents();
            result = eventsParam;
        }};

        alertService.deleteAlert(alert);

        new Verifications() {{
            event1.removeAlert(alert);
            event2.removeAlert(alert);
            alertDao.delete(alert);
        }};
    }

    @Test
    public void deleteAlerts(@Mocked eu.domibus.core.alerts.model.service.Alert alert1,
                             @Mocked eu.domibus.core.alerts.model.service.Alert alert2,
                             @Mocked eu.domibus.core.alerts.model.persist.Alert modelAlert) {
        new Expectations(alertService) {{
            alert1.toString();
            result = "alert1";

            alert2.toString();
            result = "alert2";

            alert1.getEntityId();
            result = 1L;

            alert1.getEntityId();
            result = 2L;

            alertDao.read(2L);
            result = null;

            alertService.deleteAlert(modelAlert);
        }};

        alertService.deleteAlerts(Arrays.asList(alert1, alert2));

    }

    @Test
    public void getSubject_config(@Mocked AlertType alertType,
                                  @Mocked eu.domibus.core.alerts.model.persist.Event next) {
        Map<String, AbstractEventProperty<?>> properties = new HashMap<>();
        new Expectations() {{
            next.getProperties();
            result = properties;

            alertConfigurationService.getMailSubject(alertType);
            result = SUBJECT;

            domibusPropertyProvider.getProperty(DOMIBUS_ALERT_SUPER_INSTANCE_NAME_SUBJECT);
            result = SERVER_NAME;
        }};
        String finalSubject = alertService.getSubject(alertType, next);

        assertEquals(SUBJECT + "[" + SERVER_NAME + "]", finalSubject);
    }

    @Test
    public void getSubject_props(@Mocked AlertType alertType,
                                 @Mocked eu.domibus.core.alerts.model.persist.Event next,
                                 @Mocked StringEventProperty stringEventProperty) {
        Map<String, AbstractEventProperty<?>> properties = new HashMap<>();
        properties.put(ALERT_SUBJECT, stringEventProperty);
        new Expectations() {{
            next.getProperties();
            result = properties;

            stringEventProperty.getValue();
            result = ALERT_SUBJECT;

            domibusPropertyProvider.getProperty(DOMIBUS_ALERT_SUPER_INSTANCE_NAME_SUBJECT);
            result = SERVER_NAME;
        }};
        String finalSubject = alertService.getSubject(alertType, next);

        assertEquals(ALERT_SUBJECT + "[" + SERVER_NAME + "]", finalSubject);
    }

    @Test
    public void getDescription(@Mocked eu.domibus.core.alerts.model.persist.Alert alert,
                               @Mocked eu.domibus.core.alerts.model.persist.Event next,
                               @Mocked StringEventProperty stringEventProperty) {
        Map<String, AbstractEventProperty<?>> properties = new HashMap<>();
        properties.put(ALERT_DESCRIPTION, stringEventProperty);
        new Expectations() {{
            alert.getAlertType().getTitle();
            result = ALERT_DESCRIPTION_TEST;

            next.getProperties();
            result = properties;

            stringEventProperty.getValue();
            result = ALERT_DESCRIPTION;
        }};
        String finalDescription = alertService.getDescription(alert, next);

        assertEquals("[" + ALERT_DESCRIPTION_TEST + "] " + ALERT_DESCRIPTION, finalDescription);
    }

    @Test
    public void getDescription_3(@Mocked eu.domibus.core.alerts.model.persist.Alert alert,
                                 @Mocked eu.domibus.core.alerts.model.persist.Event next,
                                 @Mocked StringEventProperty stringEventProperty,
                                 @Mocked StringEventProperty stringEventProperty1,
                                 @Mocked StringEventProperty stringEventProperty2) {
        Map<String, AbstractEventProperty<?>> properties = new HashMap<>();
        properties.put(ALERT_DESCRIPTION, stringEventProperty);
        properties.put(ALERT_DESCRIPTION + "_1", stringEventProperty1);
        properties.put(ALERT_DESCRIPTION + "_2", stringEventProperty2);
        new Expectations() {{
            alert.getAlertType().getTitle();
            result = ALERT_DESCRIPTION_TEST;

            next.getProperties();
            result = properties;

            stringEventProperty.getValue();
            result = ALERT_DESCRIPTION;

            stringEventProperty1.getValue();
            result = ALERT_DESCRIPTION;

            stringEventProperty2.getValue();
            result = ALERT_DESCRIPTION;
        }};

        String finalDescription = alertService.getDescription(alert, next);

        assertEquals("[" + ALERT_DESCRIPTION_TEST + "] " + ALERT_DESCRIPTION + ALERT_DESCRIPTION + ALERT_DESCRIPTION, finalDescription);
    }

    @Test
    public void getDescription_X(@Mocked eu.domibus.core.alerts.model.persist.Alert alert,
                                 @Mocked eu.domibus.core.alerts.model.persist.Event next,
                                 @Mocked StringEventProperty stringEventProperty,
                                 @Mocked StringEventProperty stringEventProperty1) {
        Map<String, AbstractEventProperty<?>> properties = new HashMap<>();
        properties.put(ALERT_DESCRIPTION, stringEventProperty);
        properties.put(ALERT_DESCRIPTION + "_1", stringEventProperty1);
        new Expectations() {{
            alert.getAlertType().getTitle();
            result = ALERT_DESCRIPTION_TEST;

            next.getProperties();
            result = properties;

            stringEventProperty.getValue();
            result = ALERT_DESCRIPTION;

            stringEventProperty1.getValue();
            result = "_1";
        }};

        String finalDescription = alertService.getDescription(alert, next);

        assertEquals("[" + ALERT_DESCRIPTION_TEST + "] " + ALERT_DESCRIPTION + "_1", finalDescription);
    }
}
