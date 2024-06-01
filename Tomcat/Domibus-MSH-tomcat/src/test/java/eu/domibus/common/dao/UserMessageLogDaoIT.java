package eu.domibus.common.dao;

import eu.domibus.test.AbstractIT;
import eu.domibus.api.model.*;
import eu.domibus.api.util.DateUtil;
import eu.domibus.common.MessageDaoTestUtil;
import eu.domibus.core.earchive.EArchiveBatchUserMessage;
import eu.domibus.core.message.MessageLogInfo;
import eu.domibus.core.message.UserMessageLogDao;
import eu.domibus.core.message.dictionary.MpcDao;
import eu.domibus.core.message.dictionary.NotificationStatusDao;
import eu.domibus.core.plugin.BackendConnectorProvider;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.messaging.MessagingProcessingException;
import eu.domibus.test.common.BackendConnectorMock;
import org.junit.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eu.domibus.api.model.DomibusDatePrefixedSequenceIdGeneratorGenerator.DATETIME_FORMAT_DEFAULT;
import static eu.domibus.api.model.MessageStatus.*;
import static eu.domibus.api.util.DateUtil.REST_FORMATTER;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.equalsAnyIgnoreCase;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * @author Ion Perpegel
 * @since 5.0
 */
@Transactional
public class UserMessageLogDaoIT extends AbstractIT {
    public static final String TIMEZONE_ID_AMERICA_LOS_ANGELES = "America/Los_Angeles";
    public static final String MPC = "UserMessageLogDaoITMpc";
    private final static DomibusLogger LOG = DomibusLoggerFactory.getLogger(UserMessageLogDaoIT.class);

    private final static String NUMBER_FORMAT_DEFAULT = "%010d";
    public static final String WS_PLUGIN = "wsPlugin";
    public static final String ORIGINAL_SENDER = "originalSender1";
    public static final String FINAL_RECIPIENT = "finalRecipient2";

    @Autowired
    private UserMessageLogDao userMessageLogDao;

    @Autowired
    DateUtil dateUtil;

    @Autowired
    MessageDaoTestUtil messageDaoTestUtil;

    @Autowired
    private BackendConnectorProvider backendConnectorProvider;

    @Autowired
    private MpcDao mpcDao;

    @Autowired
    private NotificationStatusDao notificationStatusDao;

    private Date before;
    private Date after;
    private Date old;

    private final String deletedNoProperties = "deletedNoProperties" + randomUUID();
    private final String deletedWithProperties = "deletedWithProperties" + randomUUID();
    private final String receivedNoProperties = "receivedNoProperties" + randomUUID();
    private final String receivedWithProperties = "receivedWithProperties" + randomUUID();
    private final String downloadedNoProperties = "downloadedNoProperties" + randomUUID();
    private final String downloadedWithProperties = "downloadedWithProperties" + randomUUID();
    private final String waitingForRetryNoProperties = "waitingForRetryNoProperties" + randomUUID();
    private final String waitingForRetryWithProperties = "waitingForRetryWithProperties" + randomUUID();
    private final String sendFailureNoProperties = "sendFailureNoProperties" + randomUUID();
    private final String sendFailureWithProperties = "sendFailureWithProperties" + randomUUID();
    private final String testDate = randomUUID().toString();
    private long maxEntityId;
    private UserMessageLog msg1;
    private UserMessageLog msg1Fragment;
    private UserMessageLog msg2;
    private UserMessageLog msg3;

    @Before
    @Transactional
    public void setup() throws Exception {
        deleteAllMessages();

        before = dateUtil.fromString("2019-01-01T12:00:00Z");
        Date timeT = dateUtil.fromString("2020-01-01T12:00:00Z");
        after = dateUtil.fromString("2021-01-01T12:00:00Z");
        old = Date.from(before.toInstant().minusSeconds(60 * 60 * 24)); // one day older than "before"

        uploadPmode();
        Mockito.when(backendConnectorProvider.getBackendConnector(anyString()))
                .thenReturn(new BackendConnectorMock(WS_PLUGIN));
        String messageOneId = "msg1-" + randomUUID();
        String messageTwoId = "msg2-" + randomUUID();
        String messageThreeId = "msg3-" + randomUUID();
        String messageFourId = "msg4-" + randomUUID();
        msg1 = receiveMessage(messageOneId, timeT, false);

        msg2 = receiveMessage(messageTwoId , timeT, false);

        msg3 = receiveMessage(messageThreeId, old, false);

        msg1Fragment = receiveMessage(messageFourId, timeT, true);

        messageDaoTestUtil.createUserMessageLog(testDate, Date.from(ZonedDateTime.now(ZoneOffset.UTC).toInstant()), MSHRole.RECEIVING, MessageStatus.NOT_FOUND, true, MPC, new Date());

        sendMessage(deletedNoProperties, timeT, DELETED, false, new Date());
        sendMessage(receivedNoProperties, timeT, RECEIVED, false, new Date());
        sendMessage(downloadedNoProperties, timeT, DOWNLOADED, false, new Date());
        sendMessage(waitingForRetryNoProperties, timeT, WAITING_FOR_RETRY, false, new Date());
        sendMessage(sendFailureNoProperties, timeT, SEND_FAILURE, false, new Date());
        sendMessage(deletedWithProperties, timeT, DELETED, true, null);
        sendMessage(receivedWithProperties, timeT, RECEIVED, true, null);
        sendMessage(downloadedWithProperties, timeT, DOWNLOADED, true, null);
        sendMessage(waitingForRetryWithProperties, timeT, WAITING_FOR_RETRY, true, null);
        sendMessage(sendFailureWithProperties, timeT, SEND_FAILURE, true, null);

        maxEntityId = Long.parseLong(ZonedDateTime
                .now(ZoneOffset.UTC)
                .plusDays(1)
                .format(ofPattern(DATETIME_FORMAT_DEFAULT, Locale.ENGLISH)) + String.format(NUMBER_FORMAT_DEFAULT, 0));

        LOG.putMDC(DomibusLogger.MDC_USER, "test_user");
    }

    private void sendMessage(String messageId, Date timeT, MessageStatus status, boolean hasProperties, Date archivedAndExported) throws MessagingProcessingException {
        UserMessageLog userMessageLog = itTestsService.sendMessageWithStatus(status, messageId);
        userMessageLog.setNotificationStatus(notificationStatusDao.findOrCreate(NotificationStatus.NOTIFIED));
        UserMessage userMessage = userMessageLog.getUserMessage();
        userMessage.setMpc(mpcDao.findOrCreateMpc(MPC));

        Set<MessageProperty> messageProperties = userMessage.getMessageProperties();
        messageProperties.clear();
        if(hasProperties){
            MessageProperty messageProperty1 = messageDaoTestUtil.createMessageProperty("originalSender", ORIGINAL_SENDER, "");
            MessageProperty messageProperty2 = messageDaoTestUtil.createMessageProperty("finalRecipient", FINAL_RECIPIENT, "");
            messageProperties.addAll(Arrays.asList(messageProperty1, messageProperty2));
        }
        MessageDaoTestUtil.setUserMessageLogDates(userMessageLog, timeT, archivedAndExported);
    }

    private UserMessageLog receiveMessage(String messageId, Date timeT, boolean isFragment) throws Exception {
        itTestsService.receiveMessage(messageId);
        UserMessageLog msg = userMessageLogDao.findByMessageId(messageId);
        MessageDaoTestUtil.setUserMessageLogDates(msg, timeT, null);
        if(isFragment){
            msg.getUserMessage().setMessageFragment(true);
        }
        return msg;
    }

    @BeforeClass
    public static void setTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone(TIMEZONE_ID_AMERICA_LOS_ANGELES));
    }

    @AfterClass
    public static void resetTimezone() {
        TimeZone.setDefault(null);
    }

    @Test
    public void getSentUserMessagesWithPayloadNotClearedOlderThan_found() {
        List<UserMessageLogDto> downloadedUserMessagesOlderThan =
                userMessageLogDao.getSentUserMessagesOlderThan(dateUtil.fromString(LocalDate.now().getYear() + 2 + "-01-01T12:00:00Z"), MPC, 10, false);
        assertEquals(2, downloadedUserMessagesOlderThan.size());
        assertThat(downloadedUserMessagesOlderThan
                .stream()
                .map(UserMessageLogDto::getMessageId)
                .collect(Collectors.toList()), hasItems(sendFailureNoProperties, sendFailureWithProperties));
    }

    @Test
    public void getSentUserMessagesWithPayloadNotClearedOlderThan_found_eArchive() {
        List<UserMessageLogDto> downloadedUserMessagesOlderThan =
                userMessageLogDao.getSentUserMessagesOlderThan(dateUtil.fromString(LocalDate.now().getYear() + 2 + "-01-01T12:00:00Z"), MPC, 10, true);
        assertEquals(1, downloadedUserMessagesOlderThan.size());
    }

    @Test
    public void getSentUserMessagesWithPayloadNotClearedOlderThan_notFound() {
        List<UserMessageLogDto> deletedUserMessagesOlderThan =
                userMessageLogDao.getSentUserMessagesOlderThan(before, MPC, 10, false);
        assertEquals(0, deletedUserMessagesOlderThan.size());
    }

    @Test
    public void getSentUserMessagesOlderThan_found() {
        List<UserMessageLogDto> downloadedUserMessagesOlderThan =
                userMessageLogDao.getSentUserMessagesOlderThan(dateUtil.fromString(LocalDate.now().getYear() + 2 + "-01-01T12:00:00Z"), MPC, 10, false);
        assertEquals(2, downloadedUserMessagesOlderThan.size());
        assertThat(downloadedUserMessagesOlderThan
                .stream()
                .map(UserMessageLogDto::getMessageId)
                .collect(Collectors.toList()), hasItems(sendFailureNoProperties, sendFailureWithProperties));
    }

    @Test
    public void getSentUserMessagesOlderThan_notFound() {
        List<UserMessageLogDto> deletedUserMessagesOlderThan =
                userMessageLogDao.getSentUserMessagesOlderThan(before, MPC, 10, false);
        assertEquals(0, deletedUserMessagesOlderThan.size());
    }

    @Test
    public void getDownloadedUserMessagesOlderThan_found() {
        List<UserMessageLogDto> downloadedUserMessagesOlderThan =
                userMessageLogDao.getDownloadedUserMessagesOlderThan(after, MPC, 10, false);
        assertEquals(2, downloadedUserMessagesOlderThan.size());
        assertThat(downloadedUserMessagesOlderThan
                .stream()
                .map(UserMessageLogDto::getMessageId)
                .collect(Collectors.toList()), hasItems(downloadedNoProperties, downloadedWithProperties));
    }
    @Test
    public void getDownloadedUserMessagesOlderThan_found_eArchive() {
        List<UserMessageLogDto> downloadedUserMessagesOlderThan =
                userMessageLogDao.getDownloadedUserMessagesOlderThan(after, MPC, 10, true);
        assertEquals(1, downloadedUserMessagesOlderThan.size());
    }

    @Test
    public void getDownloadedUserMessagesOlderThan_notFound() {
        List<UserMessageLogDto> deletedUserMessagesOlderThan =
                userMessageLogDao.getDownloadedUserMessagesOlderThan(before, MPC, 10, false);
        assertEquals(0, deletedUserMessagesOlderThan.size());
    }

    @Test
    @Transactional
    public void getUndownloadedUserMessagesOlderThan_found() {
        List<UserMessageLogDto> undownloadedUserMessagesOlderThan =
                userMessageLogDao.getUndownloadedUserMessagesOlderThan(after, MPC, 10, false);
        assertEquals(2, undownloadedUserMessagesOlderThan.size());
        assertThat(undownloadedUserMessagesOlderThan
                .stream()
                .map(UserMessageLogDto::getMessageId)
                .collect(Collectors.toList()), hasItems(receivedNoProperties, receivedWithProperties));
    }

    @Test
    public void getUndownloadedUserMessagesOlderThan_found_eArchive() {
        List<UserMessageLogDto> undownloadedUserMessagesOlderThan =
                userMessageLogDao.getUndownloadedUserMessagesOlderThan(after, MPC, 10, true);
        assertEquals(1, undownloadedUserMessagesOlderThan.size());
    }

    @Test
    public void getUndownloadedUserMessagesOlderThan_notFound() {
        List<UserMessageLogDto> deletedUserMessagesOlderThan =
                userMessageLogDao.getUndownloadedUserMessagesOlderThan(before, MPC, 10, false);
        assertEquals(0, deletedUserMessagesOlderThan.size());
    }

    @Test
    public void getDeletedUserMessagesOlderThan_found() {
        List<UserMessageLogDto> deletedUserMessagesOlderThan =
                userMessageLogDao.getDeletedUserMessagesOlderThan(after, MPC, 10, false);
        assertEquals(2, deletedUserMessagesOlderThan.size());
        assertThat(deletedUserMessagesOlderThan
                .stream()
                .map(UserMessageLogDto::getMessageId)
                .collect(Collectors.toList()), hasItems(deletedNoProperties, deletedWithProperties));
    }

    @Test
    public void getDeletedUserMessagesOlderThan_found_eArchive() {
        List<UserMessageLogDto> deletedUserMessagesOlderThan =
                userMessageLogDao.getDeletedUserMessagesOlderThan(after, MPC, 10, true);
        assertEquals(1, deletedUserMessagesOlderThan.size());
    }

    @Test
    public void currentAndFutureDateTimesSavedInUtcIrrespectiveOfApplicationTimezone() {
        UserMessageLog retryMessage = userMessageLogDao.findByMessageId(testDate, MSHRole.RECEIVING);
        Assert.assertNotNull("Should have found a retry message", retryMessage);

        final Date now = dateUtil.getUtcDate();
        Assert.assertTrue("Should have saved the received date in UTC, irrespective of the application timezone " +
                        "(difference to UTC current date time less than 10 minutes)",
                dateUtil.getDiffMinutesBetweenDates(now, retryMessage.getReceived()) < 10);
    }

    private Map<String, String> getProperties(List<UserMessageLogDto> deletedUserMessagesOlderThan, String deletedWithProperties) {
        return deletedUserMessagesOlderThan.stream()
                .filter(userMessageLogDto -> equalsAnyIgnoreCase(userMessageLogDto.getMessageId(), deletedWithProperties))
                .findAny()
                .map(UserMessageLogDto::getProperties)
                .orElse(null);
    }

    @Test
    public void getDeletedUserMessagesOlderThan_notFound() {
        List<UserMessageLogDto> deletedUserMessagesOlderThan =
                userMessageLogDao.getDeletedUserMessagesOlderThan(before, MPC, 10, false);
        assertEquals(0, deletedUserMessagesOlderThan.size());
    }

    @Test
    @Transactional
    public void testCount() {
        Map<String, Object> filters = Stream.of(new Object[][]{
                {"receivedFrom", before},
                {"receivedTo", after},
        }).collect(Collectors.toMap(data -> (String) data[0], data -> data[1]));

        long count = userMessageLogDao.countEntries(filters);

        assertEquals(13, count);
    }

    @Test
    @Transactional
    public void testCountWithMoreFilters() {
        Map<String, Object> filters = Stream.of(new Object[][]{
                {"receivedFrom", before},
                {"receivedTo", after},
                {"mshRole", MSHRole.RECEIVING},
                {"messageStatus", RECEIVED},
        }).collect(Collectors.toMap(data -> (String) data[0], data -> data[1]));

        long count = userMessageLogDao.countEntries(filters);

        assertEquals(3, count);
    }

    @Test
    @Transactional
    public void testFindAllInfoPaged() {
        Map<String, Object> filters = Stream.of(new Object[][]{
                {"receivedFrom", before},
                {"receivedTo", after},
        }).collect(Collectors.toMap(data -> (String) data[0], data -> data[1]));

        List<MessageLogInfo> messages = userMessageLogDao.findAllInfoPaged(0, 100, "received", true, filters, Collections.emptyList());

        assertEquals(13, messages.size());

        long count = userMessageLogDao.countEntries(filters);

        assertEquals(count, messages.size());
    }

    @Test
    @Ignore
    @Transactional
    public void findMessagesToDelete() {
        final ZonedDateTime currentDate = ZonedDateTime.now(ZoneOffset.UTC);
        final ZonedDateTime startDate = currentDate.minusDays(1);
        final ZonedDateTime endDate = currentDate.plusDays(1);
        final String finalRecipient = FINAL_RECIPIENT;

        Long idPkStartDate = dateUtil.getIdPkDateHour(startDate.format(REST_FORMATTER));
        Long idPkEndDate = dateUtil.getIdPkDateHour(endDate.format(REST_FORMATTER));
        List<UserMessageLogDto> message = userMessageLogDao.findMessagesToDeleteNotInFinalStatus(finalRecipient, idPkStartDate, idPkEndDate);
        assertEquals(3, message.size());
    }

    @Test
    @Transactional
    public void findFailedMessages() {
        final ZonedDateTime currentDate = ZonedDateTime.now(ZoneOffset.UTC);
        final ZonedDateTime startDate = currentDate.minusDays(1);
        final ZonedDateTime endDate = currentDate.plusDays(1);
        final String finalRecipient = FINAL_RECIPIENT;

        List<String> message = userMessageLogDao.findFailedMessages(finalRecipient, null, dateUtil.getIdPkDateHour(startDate.format(REST_FORMATTER)), dateUtil.getIdPkDateHour(endDate.format(REST_FORMATTER)));
        assertEquals(1, message.size());
    }

    @Test
    @Transactional
    public void findFailedMessages_all() {
        final ZonedDateTime currentDate = ZonedDateTime.now(ZoneOffset.UTC);
        final ZonedDateTime startDate = currentDate.minusDays(1);
        final ZonedDateTime endDate = currentDate.plusDays(1);

        List<String> message = userMessageLogDao.findFailedMessages(null, null, dateUtil.getIdPkDateHour(startDate.format(REST_FORMATTER)), dateUtil.getIdPkDateHour(endDate.format(REST_FORMATTER)));
        assertEquals(2, message.size());
    }

    @Test
    @Transactional
    public void findFailedMessages_unknownFinalRecipient() {
        final ZonedDateTime currentDate = ZonedDateTime.now(ZoneOffset.UTC);
        final ZonedDateTime startDate = currentDate.minusDays(1);
        final ZonedDateTime endDate = currentDate.plusDays(1);

        List<String> message = userMessageLogDao.findFailedMessages("unknown", null, dateUtil.getIdPkDateHour(startDate.format(REST_FORMATTER)), dateUtil.getIdPkDateHour(endDate.format(REST_FORMATTER)));
        assertEquals(0, message.size());
    }

    @Test
    @Transactional
    public void findFailedMessages_originalSender() {
        final ZonedDateTime currentDate = ZonedDateTime.now(ZoneOffset.UTC);
        final ZonedDateTime startDate = currentDate.minusDays(1);
        final ZonedDateTime endDate = currentDate.plusDays(1);
        List<String> message = userMessageLogDao.findFailedMessages(FINAL_RECIPIENT, ORIGINAL_SENDER, dateUtil.getIdPkDateHour(startDate.format(REST_FORMATTER)), dateUtil.getIdPkDateHour(endDate.format(REST_FORMATTER)));
        assertEquals(1, message.size());
    }

    @Test
    @Transactional
    public void findFailedMessages_originalSender_notFound() {
        final ZonedDateTime currentDate = ZonedDateTime.now(ZoneOffset.UTC);
        final ZonedDateTime startDate = currentDate.minusDays(1);
        final ZonedDateTime endDate = currentDate.plusDays(1);
        List<String> message = userMessageLogDao.findFailedMessages(FINAL_RECIPIENT, "notExists", dateUtil.getIdPkDateHour(startDate.format(REST_FORMATTER)), dateUtil.getIdPkDateHour(endDate.format(REST_FORMATTER)));
        assertEquals(0, message.size());
    }

    @Test
    @Transactional
    public void findFailedMessagesWithOutDates() {
        List<String> message = userMessageLogDao.findFailedMessages(null, null, null, null);
        assertEquals(2, message.size());
    }

    @Test
    @Transactional
    public void testFindMessagesForArchiving_oldest() {
        UserMessageLog msg = userMessageLogDao.findByMessageId(downloadedWithProperties, MSHRole.SENDING);

        List<EArchiveBatchUserMessage> messagesForArchiving = userMessageLogDao.findMessagesForArchivingAsc(0L, maxEntityId, 100);
        assertThat(messagesForArchiving.stream()
                        .map(EArchiveBatchUserMessage::getMessageId)
                        .collect(Collectors.toList()),
                hasItems(msg1.getUserMessage().getMessageId(),
                        msg2.getUserMessage().getMessageId(),
                        msg3.getUserMessage().getMessageId(),
                        receivedWithProperties,
                        downloadedWithProperties));
    }

    @Test
    @Transactional
    public void testFindMessagesForArchiving_rest() {
        UserMessageLog msg1 = userMessageLogDao.findByMessageId(this.msg1.getUserMessage().getMessageId(), this.msg1.getUserMessage().getMshRole().getRole());

        List<EArchiveBatchUserMessage> messagesForArchiving = userMessageLogDao.findMessagesForArchivingAsc(msg1.getEntityId(), maxEntityId, 20);
        assertEquals(4, messagesForArchiving.size());
    }

    @Test
    @Transactional
    public void updateStatusToArchived() {
        List<UserMessageLog> allUserMessageLogs = messageDaoTestUtil.getAllUserMessageLogs();
        List<Long> resultList = allUserMessageLogs.stream().map(AbstractNoGeneratedPkEntity::getEntityId).collect(Collectors.toList());

        userMessageLogDao.update(resultList, userMessageLogDao::updateArchivedBatched);

        List<UserMessageLog> result = messageDaoTestUtil.getAllUserMessageLogs();

        for (UserMessageLog uml : result) {
            em.refresh(uml);
            Assert.assertNotNull(uml.getArchived());
        }
    }


    @Test
    @Transactional
    public void updateStatusToExported() {
        List<UserMessageLog> allUserMessageLogs = messageDaoTestUtil.getAllUserMessageLogs();
        List<Long> resultList = allUserMessageLogs.stream().map(AbstractNoGeneratedPkEntity::getEntityId).collect(Collectors.toList());

        userMessageLogDao.update(resultList, userMessageLogDao::updateExportedBatched);

        List<UserMessageLog> result = messageDaoTestUtil.getAllUserMessageLogs();

        for (UserMessageLog uml : result) {
            em.refresh(uml);
            Assert.assertNotNull(uml.getExported());
        }
    }

    @Test
    @Transactional
    public void findRetryMessages() {
        List<Long> retryMessages = userMessageLogDao.findRetryMessages(0, 999999999999999999L);

        assertEquals(2, retryMessages.size());
    }

    @Test
    @Transactional
    public void findMessagesNotFinalAsc() {
        List<EArchiveBatchUserMessage> retryMessages = userMessageLogDao.findMessagesNotFinalAsc(0, 999999999999999999L);

        assertEquals(1, retryMessages.size());
        assertNotNull(retryMessages.get(0).getMessageStatus());
    }

    @Test
    @Transactional
    public void getMessageStatus_messageId() {
        MessageStatus messageStatus = userMessageLogDao.getMessageStatus(msg1.getUserMessage().getEntityId());

        assertEquals(RECEIVED, messageStatus);
    }

    @Test
    @Transactional
    public void getMessageStatus_entityId() {
        MessageStatus messageStatus = userMessageLogDao.getMessageStatus(msg1.getEntityId());

        assertEquals(RECEIVED, messageStatus);
    }

    @Test
    @Transactional
    public void getMessageStatus_messageIdNotFound() {
        MessageStatus messageStatus = userMessageLogDao.getMessageStatus("notfound", MSHRole.RECEIVING);

        assertEquals(NOT_FOUND, messageStatus);
    }

    @Test
    @Transactional
    public void getMessageStatus_entityIdNotFound() {
        MessageStatus messageStatus = userMessageLogDao.getMessageStatus(12234567890L);

        assertEquals(NOT_FOUND, messageStatus);
    }

    @Test
    @Transactional
    public void findByMessageIdSafely_notfound() {
        UserMessageLog userMessageLog = userMessageLogDao.findByMessageIdSafely("notFound", MSHRole.SENDING);

        assertNull(userMessageLog);
    }

    @Test
    @Transactional
    public void findByMessageIdSafely_ok() {
        UserMessageLog userMessageLog = userMessageLogDao.findByMessageIdSafely(msg1.getUserMessage().getMessageId(),
                msg1.getUserMessage().getMshRole().getRole());

        Assert.assertNotNull(userMessageLog);
    }

    @Test
    @Transactional
    public void findByEntityId() {
        UserMessageLog userMessageLog = userMessageLogDao.findByEntityId(msg1.getEntityId());

        Assert.assertNotNull(userMessageLog);
    }

    @Test
    @Transactional
    public void findByEntityId_notFound() {
        UserMessageLog userMessageLog = userMessageLogDao.findByEntityId(12234567890L);

        assertNull(userMessageLog);
    }

    @Test
    @Transactional
    public void findByEntityIdSafely() {
        UserMessageLog userMessageLog = userMessageLogDao.findByEntityIdSafely(msg1.getEntityId());

        Assert.assertNotNull(userMessageLog);
    }

    @Test
    @Transactional
    public void findByEntityIdSafely_notFound() {
        UserMessageLog userMessageLog = userMessageLogDao.findByEntityIdSafely(12234567890L);

        assertNull(userMessageLog);
    }

    @Test
    @Transactional
    public void setMessageStatus_DELETED() {
        userMessageLogDao.setMessageStatus(msg1, DELETED);

        UserMessageLog byEntityId = userMessageLogDao.findByEntityId(msg1.getEntityId());
        assertEquals(DELETED, byEntityId.getMessageStatus());
        Assert.assertNotNull(byEntityId.getDeleted());
        assertNull(byEntityId.getAcknowledged());
        assertNull(byEntityId.getDownloaded());
        assertNull(byEntityId.getFailed());
    }
    @Test
    @Transactional
    public void setMessageStatus_ACKNOWLEDGED() {
        userMessageLogDao.setMessageStatus(msg1, ACKNOWLEDGED);

        UserMessageLog byEntityId = userMessageLogDao.findByEntityId(msg1.getEntityId());
        assertEquals(ACKNOWLEDGED, byEntityId.getMessageStatus());
        assertNull(byEntityId.getDeleted());
        Assert.assertNotNull(byEntityId.getAcknowledged());
        assertNull(byEntityId.getDownloaded());
        assertNull(byEntityId.getFailed());
    }

    @Test
    @Transactional
    public void setMessageStatus_ACKNOWLEDGED_WITH_WARNING() {
        userMessageLogDao.setMessageStatus(msg1, ACKNOWLEDGED_WITH_WARNING);

        UserMessageLog byEntityId = userMessageLogDao.findByEntityId(msg1.getEntityId());
        assertEquals(ACKNOWLEDGED_WITH_WARNING, byEntityId.getMessageStatus());
        assertNull(byEntityId.getDeleted());
        Assert.assertNotNull(byEntityId.getAcknowledged());
        assertNull(byEntityId.getDownloaded());
        assertNull(byEntityId.getFailed());
    }

    @Test
    @Transactional
    public void setMessageStatus_DOWNLOADED() {
        userMessageLogDao.setMessageStatus(msg1, DOWNLOADED);

        UserMessageLog byEntityId = userMessageLogDao.findByEntityId(msg1.getEntityId());
        assertEquals(DOWNLOADED, byEntityId.getMessageStatus());
        assertNull(byEntityId.getDeleted());
        assertNull(byEntityId.getAcknowledged());
        Assert.assertNotNull(byEntityId.getDownloaded());
        assertNull(byEntityId.getFailed());
    }

    @Test
    @Transactional
    public void setMessageStatus_SEND_FAILURE() {
        userMessageLogDao.setMessageStatus(msg1, SEND_FAILURE);

        UserMessageLog byEntityId = userMessageLogDao.findByEntityId(msg1.getEntityId());
        assertEquals(SEND_FAILURE, byEntityId.getMessageStatus());
        assertNull(byEntityId.getDeleted());
        assertNull(byEntityId.getAcknowledged());
        assertNull(byEntityId.getDownloaded());
        Assert.assertNotNull(byEntityId.getFailed());
    }

    @Test
    @Transactional
    public void findBackendForMessageId() {
        String backendForMessageId = userMessageLogDao.findBackendForMessageId(msg1.getUserMessage().getMessageId(), msg1.getUserMessage().getMshRole().getRole());
        assertEquals(WS_PLUGIN, backendForMessageId);
    }

    @Test
    @Transactional
    public void setAsNotified() {
        userMessageLogDao.setAsNotified(msg1);

        UserMessageLog byEntityId = userMessageLogDao.findByEntityId(msg1.getEntityId());
        assertEquals(NotificationStatus.NOTIFIED, byEntityId.getNotificationStatus().getStatus());
    }

    @Test
    public void findAllInfoPaged() {
        List<MessageLogInfo> messages = userMessageLogDao.findAllInfoPaged(0, 5, "BACKEND", true, new HashMap<>(), Collections.emptyList());
        assertEquals(5, messages.size());
    }

    @Test
    public void findMessagesToDeleteInFinalStatus() {
       // messageDaoTestUtil.clear();

        String originalUser = "pluginUser1";
        String originalSender = originalUser;
        String finalRecipient = "pluginUser2";
        String originalSender2 = finalRecipient;

        messageDaoTestUtil.createUserMessageLog("msg1", new Date(), MSHRole.RECEIVING, MessageStatus.RECEIVED, finalRecipient, originalSender);
        messageDaoTestUtil.createUserMessageLog("msg2", new Date(), MSHRole.SENDING, ACKNOWLEDGED, originalSender, finalRecipient);

        messageDaoTestUtil.createUserMessageLog("msg3", new Date(), MSHRole.RECEIVING, MessageStatus.RECEIVED, originalSender2, originalSender2);

        messageDaoTestUtil.createUserMessageLog("msg4", new Date(), MSHRole.RECEIVING, RECEIVED_WITH_WARNINGS, finalRecipient, originalSender);
        messageDaoTestUtil.createUserMessageLog("msg5", new Date(), MSHRole.SENDING, SEND_FAILURE, originalSender, finalRecipient);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        c.add(Calendar.DATE, -1);
        String sdate = sdf.format(c.getTime());
        Long startDate = dateUtil.getIdPkDateHour(sdate);

        c.setTime(new Date());
        c.add(Calendar.DATE, 1);
        String edate = sdf.format(c.getTime());
        Long endDate = dateUtil.getIdPkDateHour(edate);

        List<UserMessageLogDto> msgs = userMessageLogDao.findMessagesToDeleteInFinalStatus(originalUser, startDate, endDate);

        assertEquals(2, msgs.size());
    }

    @Test
    public void findMessagesToDeleteNotInFinalStatus() {
       // messageDaoTestUtil.clear();

        String originalUser = "pluginUser1";
        String originalSender = originalUser;
        String finalRecipient = "pluginUser2";
        String originalSender2 = finalRecipient;

        messageDaoTestUtil.createUserMessageLog("msg1", new Date(), MSHRole.RECEIVING, MessageStatus.RECEIVED, finalRecipient, originalSender);
        messageDaoTestUtil.createUserMessageLog("msg2", new Date(), MSHRole.SENDING, ACKNOWLEDGED, originalSender, finalRecipient);

        messageDaoTestUtil.createUserMessageLog("msg3", new Date(), MSHRole.RECEIVING, MessageStatus.RECEIVED, originalSender2, originalSender2);

        messageDaoTestUtil.createUserMessageLog("msg4", new Date(), MSHRole.RECEIVING, RECEIVED_WITH_WARNINGS, finalRecipient, originalSender);
        messageDaoTestUtil.createUserMessageLog("msg5", new Date(), MSHRole.SENDING, SEND_FAILURE, originalSender, finalRecipient);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        c.add(Calendar.DATE, -1);
        String sdate = sdf.format(c.getTime());
        Long startDate = dateUtil.getIdPkDateHour(sdate);

        c.setTime(new Date());
        c.add(Calendar.DATE, 1);
        String edate = sdf.format(c.getTime());
        Long endDate = dateUtil.getIdPkDateHour(edate);

        List<UserMessageLogDto> msgs = userMessageLogDao.findMessagesToDeleteNotInFinalStatus(originalUser, startDate, endDate);

        assertEquals(2, msgs.size());
    }
}
