package eu.domibus.api.property;

import java.util.Map;

/**
 * The interface implemented by MSH to expose metadata for all of the configuration properties
 *
 * @author Ion Perpegel
 * @since 4.1.1
 */
public interface DomibusPropertyMetadataManagerSPI {
    /**
     * Get all the properties metadata that support changing at runtime
     *
     * @return properties as metadata
     */
    Map<String, DomibusPropertyMetadata> getKnownProperties();

    /**
     * True if the manager handles the specified property
     *
     * @param name the name of the property
     */
    boolean hasKnownProperty(String name);

    String DOMIBUS_ALERT_USER_ACCOUNT_DISABLED_PREFIX = "domibus.alert.user.account_disabled";
    String DOMIBUS_ALERT_USER_ACCOUNT_ENABLED_PREFIX = "domibus.alert.user.account_enabled";
    String DOMIBUS_ALERT_CERT_IMMINENT_EXPIRATION_PREFIX = "domibus.alert.cert.imminent_expiration";
    String DOMIBUS_ALERT_CERT_EXPIRED_PREFIX = "domibus.alert.cert.expired";
    String DOMIBUS_ALERT_USER_LOGIN_FAILURE_PREFIX = "domibus.alert.user.login_failure";
    String DOMIBUS_ALERT_SENDER_SMTP_PREFIX = "domibus.alert.sender.smtp.";
    String DOMIBUS_ALERT_MSG_COMMUNICATION_FAILURE_PREFIX = "domibus.alert.msg.communication_failure";
    String DOMIBUS_ALERT_PASSWORD_EXPIRED_PREFIX = "domibus.alert.password.expired"; //NOSONAR
    String DOMIBUS_ALERT_PASSWORD_IMMINENT_EXPIRATION_PREFIX = "domibus.alert.password.imminent_expiration"; //NOSONAR
    String DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_DISABLED_PREFIX = "domibus.alert.plugin.user.account_disabled";
    String DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_ENABLED_PREFIX = "domibus.alert.plugin.user.account_enabled";
    String DOMIBUS_ALERT_PLUGIN_USER_LOGIN_FAILURE_PREFIX = "domibus.alert.plugin.user.login_failure";
    String DOMIBUS_ALERT_PLUGIN_PASSWORD_EXPIRED_PREFIX = "domibus.alert.plugin_password.expired"; //NOSONAR
    String DOMIBUS_ALERT_PLUGIN_PASSWORD_IMMINENT_EXPIRATION_PREFIX = "domibus.alert.plugin_password.imminent_expiration"; //NOSONAR
    String DOMIBUS_SECURITY_KEYSTORE_PREFIX = "domibus.security.keystore.";
    String DOMIBUS_SECURITY_TRUSTSTORE_PREFIX = "domibus.security.truststore.";
    String DOMIBUS_SECURITY_PROFILE_ORDER = "domibus.security.profile.order";
    String DOMIBUS_PROXY_PREFIX = "domibus.proxy.";

    String DOMIBUS_UI_TITLE_NAME = "domibus.ui.title.name";
    String DOMIBUS_UI_SUPPORT_TEAM_NAME = "domibus.ui.support.team.name";
    String DOMIBUS_UI_SUPPORT_TEAM_EMAIL = "domibus.ui.support.team.email";
    String DOMIBUS_UI_CSV_MAX_ROWS = "domibus.ui.csv.rows.max";
    String DOMIBUS_UI_MESSAGE_LOGS_COUNT_LIMIT = "domibus.ui.pages.messageLogs.countLimit";
    String DOMIBUS_UI_MESSAGE_LOGS_DEFAULT_INTERVAL = "domibus.ui.pages.messageLogs.interval.default";
    String DOMIBUS_UI_MESSAGE_LOGS_LANDING_PAGE = "domibus.ui.pages.messageLogs.landingPage.enabled";
    String DOMIBUS_UI_MESSAGE_LOGS_SEARCH_ADVANCED_ENABLED = "domibus.ui.pages.messageLogs.search.advanced.enabled";

    String DOMIBUS_SECURITY_KEYSTORE_LOCATION = DOMIBUS_SECURITY_KEYSTORE_PREFIX + "location";
    String DOMIBUS_SECURITY_KEYSTORE_TYPE = DOMIBUS_SECURITY_KEYSTORE_PREFIX + "type";
    String DOMIBUS_SECURITY_KEYSTORE_PASSWORD = DOMIBUS_SECURITY_KEYSTORE_PREFIX + "password";//NOSONAR

    String DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX = "domibus.security.key.private.";
    String DOMIBUS_SECURITY_KEY_PRIVATE_ALIAS = DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX + "alias";

    String DOMIBUS_SECURITY_KEY_PRIVATE_RSA_SIGN_ALIAS = DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX + "rsa.sign.alias";
    String DOMIBUS_SECURITY_KEY_PRIVATE_RSA_SIGN_PASSWORD = DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX + "rsa.sign.password";
    String DOMIBUS_SECURITY_KEY_PRIVATE_RSA_SIGN_TYPE = DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX + "rsa.sign.type";
    String DOMIBUS_SECURITY_KEY_PRIVATE_RSA_DECRYPT_ALIAS = DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX + "rsa.decrypt.alias";
    String DOMIBUS_SECURITY_KEY_PRIVATE_RSA_DECRYPT_PASSWORD = DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX + "rsa.decrypt.password";
    String DOMIBUS_SECURITY_KEY_PRIVATE_RSA_DECRYPT_TYPE = DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX + "rsa.decrypt.type";

    String DOMIBUS_SECURITY_KEY_PRIVATE_ECC_SIGN_ALIAS = DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX + "ecc.sign.alias";
    String DOMIBUS_SECURITY_KEY_PRIVATE_ECC_SIGN_PASSWORD = DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX + "ecc.sign.password";
    String DOMIBUS_SECURITY_KEY_PRIVATE_ECC_SIGN_TYPE = DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX + "ecc.sign.type";
    String DOMIBUS_SECURITY_KEY_PRIVATE_ECC_DECRYPT_ALIAS = DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX + "ecc.decrypt.alias";
    String DOMIBUS_SECURITY_KEY_PRIVATE_ECC_DECRYPT_PASSWORD = DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX + "ecc.decrypt.password";
    String DOMIBUS_SECURITY_KEY_PRIVATE_ECC_DECRYPT_TYPE = DOMIBUS_SECURITY_KEY_PRIVATE_PREFIX + "ecc.decrypt.type";

    String DOMIBUS_SECURITY_TRUSTSTORE_LOCATION = DOMIBUS_SECURITY_TRUSTSTORE_PREFIX + "location";
    String DOMIBUS_SECURITY_TRUSTSTORE_TYPE = DOMIBUS_SECURITY_TRUSTSTORE_PREFIX + "type";
    String DOMIBUS_SECURITY_TRUSTSTORE_PASSWORD = DOMIBUS_SECURITY_TRUSTSTORE_PREFIX + "password";//NOSONAR
    String DOMIBUS_AUTH_UNSECURE_LOGIN_ALLOWED = "domibus.auth.unsecureLoginAllowed";
    String DOMIBUS_CONSOLE_LOGIN_MAXIMUM_ATTEMPT = "domibus.console.login.maximum.attempt";
    String DOMIBUS_CONSOLE_LOGIN_SUSPENSION_TIME = "domibus.console.login.suspension.time";
    String DOMIBUS_CERTIFICATE_REVOCATION_OFFSET = "domibus.certificate.revocation.offset";
    String DOMIBUS_CACHE_LOCATION = "domibus.cache.location";
    String DOMIBUS_CRL_BY_URL_CACHE_ENABLED = "domibus.certificate.crlByUrl.cache.enabled";
    String DOMIBUS_CRL_BY_CERT_CACHE_ENABLED = "domibus.certificate.crlByCert.cache.enabled";
    String DOMIBUS_CERTIFICATE_CRL_EXCLUDED_PROTOCOLS = "domibus.certificate.crl.excludedProtocols";
    String DOMIBUS_CERTIFICATE_CRL_HTTP_TIMEOUT = "domibus.certificate.crl.http.timeout";
    String DOMIBUS_PLUGIN_LOGIN_MAXIMUM_ATTEMPT = "domibus.plugin.login.maximum.attempt";
    String DOMIBUS_PLUGIN_LOGIN_SUSPENSION_TIME = "domibus.plugin.login.suspension.time";
    String DOMIBUS_PASSWORD_POLICY_PATTERN = "domibus.passwordPolicy.pattern";//NOSONAR
    String DOMIBUS_PASSWORD_POLICY_VALIDATION_MESSAGE = "domibus.passwordPolicy.validationMessage";//NOSONAR
    String DOMIBUS_PASSWORD_POLICY_EXPIRATION = "domibus.passwordPolicy.expiration";//NOSONAR
    String DOMIBUS_PASSWORD_POLICY_DEFAULT_PASSWORD_EXPIRATION = "domibus.passwordPolicy.defaultPasswordExpiration";//NOSONAR
    String DOMIBUS_PASSWORD_POLICY_WARNING_BEFORE_EXPIRATION = "domibus.passwordPolicy.warning.beforeExpiration";//NOSONAR
    String DOMIBUS_PASSWORD_POLICY_DONT_REUSE_LAST = "domibus.passwordPolicy.dontReuseLast";//NOSONAR
    String DOMIBUS_PASSWORD_POLICY_CHECK_DEFAULT_PASSWORD = "domibus.passwordPolicy.checkDefaultPassword";//NOSONAR
    String DOMIBUS_PASSWORD_POLICY_DEFAULT_USER_CREATE = "domibus.passwordPolicy.defaultUser.create";//NOSONAR
    String DOMIBUS_PASSWORD_POLICY_DEFAULT_USER_AUTOGENERATE_PASSWORD = "domibus.passwordPolicy.defaultUser.autogeneratePassword";//NOSONAR
    String DOMIBUS_PLUGIN_PASSWORD_POLICY_PATTERN = "domibus.plugin.passwordPolicy.pattern";//NOSONAR
    String DOMIBUS_PLUGIN_PASSWORD_POLICY_VALIDATION_MESSAGE = "domibus.plugin.passwordPolicy.validationMessage";//NOSONAR
    String DOMIBUS_PASSWORD_POLICY_PLUGIN_EXPIRATION = "domibus.plugin.passwordPolicy.expiration";//NOSONAR
    String DOMIBUS_PASSWORD_POLICY_PLUGIN_DEFAULT_PASSWORD_EXPIRATION = "domibus.plugin.passwordPolicy.defaultPasswordExpiration";//NOSONAR
    String DOMIBUS_PASSWORD_POLICY_PLUGIN_DONT_REUSE_LAST = "domibus.plugin.passwordPolicy.dontReuseLast";//NOSONAR
    String DOMIBUS_ATTACHMENT_STORAGE_LOCATION = "domibus.attachment.storage.location";
    String DOMIBUS_PAYLOAD_ENCRYPTION_ACTIVE = "domibus.payload.encryption.active";
    String DOMIBUS_PAYLOAD_BUSINESS_CONTENT_ATTACHMENT = "businessContentAttachment";
    String DOMIBUS_PAYLOAD_BUSINESS_CONTENT_ATTACHMENT_ENABLED = "domibus.payload.business.content.attachment.enabled";
    String DOMIBUS_MSH_MESSAGEID_SUFFIX = "domibus.msh.messageid.suffix";
    String DOMIBUS_MSH_RETRY_MESSAGE_EXPIRATION_DELAY = "domibus.msh.retry.messageExpirationDelay";
    String DOMIBUS_MSH_RETRY_TIMEOUT_DELAY = "domibus.msh.retry.timeoutDelay";
    String DOMIBUS_DYNAMICDISCOVERY_USE_DYNAMIC_DISCOVERY = "domibus.dynamicdiscovery.useDynamicDiscovery";
    String DOMIBUS_SMLZONE = "domibus.smlzone";
    String DOMIBUS_DYNAMICDISCOVERY_CLIENT_SPECIFICATION = "domibus.dynamicdiscovery.client.specification";
    String DOMIBUS_DYNAMICDISCOVERY_OASISCLIENT_REGEX_CERTIFICATE_SUBJECT_VALIDATION = "domibus.dynamicdiscovery.oasisclient.regexCertificateSubjectValidation";
    String DOMIBUS_DYNAMICDISCOVERY_PEPPOLCLIENT_REGEX_CERTIFICATE_SUBJECT_VALIDATION = "domibus.dynamicdiscovery.peppolclient.regexCertificateSubjectValidation";
    String DOMIBUS_DYNAMICDISCOVERY_CLIENT_CERTIFICATE_POLICY_OID_VALIDATION = "domibus.dynamicdiscovery.client.allowedCertificatePolicyOIDs";
    String DOMIBUS_DYNAMICDISCOVERY_CLIENT_WILDCARD_DOCUMENT_SCHEMES = "domibus.dynamicdiscovery.client.wildcardDocumentSchemes";
    String DOMIBUS_DYNAMICDISCOVERY_CLIENT_DNS_LOOKUP_TYPES = "domibus.dynamicdiscovery.client.dns.lookup.types";
    String DOMIBUS_DYNAMICDISCOVERY_PEPPOLCLIENT_PARTYID_RESPONDER_ROLE = "domibus.dynamicdiscovery.peppolclient.partyid.responder.role";
    String DOMIBUS_DYNAMICDISCOVERY_OASISCLIENT_PARTYID_RESPONDER_ROLE = "domibus.dynamicdiscovery.oasisclient.partyid.responder.role";
    String DOMIBUS_DYNAMICDISCOVERY_PEPPOLCLIENT_PARTYID_TYPE = "domibus.dynamicdiscovery.peppolclient.partyid.type";
    String DOMIBUS_DYNAMICDISCOVERY_OASISCLIENT_PARTYID_TYPE = "domibus.dynamicdiscovery.oasisclient.partyid.type";
    String DOMIBUS_DYNAMICDISCOVERY_TRANSPORTPROFILEAS_4 = "domibus.dynamicdiscovery.transportprofileas4";


    String DOMIBUS_LIST_PENDING_MESSAGES_MAX_COUNT = "domibus.listPendingMessages.maxCount";
    String DOMIBUS_JMS_CONNECTION_FACTORY_SESSION_CACHE_SIZE = "domibus.jms.connectionFactory.session.cache.size";
    String DOMIBUS_JMS_QUEUE_MAX_BROWSE_SIZE = "domibus.jms.queue.maxBrowseSize";
    String DOMIBUS_JMS_INTERNAL_QUEUE_EXPRESSION = "domibus.jms.internalQueue.expression";
    String DOMIBUS_JMS_INTERNAL_ADDRESS_EXPRESSION = "domibus.jms.internal.address.expression";
    String DOMIBUS_RECEIVER_SELF_SENDING_VALIDATION_ACTIVE = "domibus.receiver.selfsending.validation.active";
    String DOMIBUS_RECEIVER_CERTIFICATE_VALIDATION_ONSENDING = "domibus.receiver.certificate.validation.onsending";
    String DOMIBUS_SENDER_CERTIFICATE_VALIDATION_ONSENDING = "domibus.sender.certificate.validation.onsending";
    String DOMIBUS_SENDER_CERTIFICATE_VALIDATION_ONRECEIVING = "domibus.sender.certificate.validation.onreceiving";
    String DOMIBUS_SENDER_TRUST_VALIDATION_ONRECEIVING = "domibus.sender.trust.validation.onreceiving";
    String DOMIBUS_SENDER_TRUST_VALIDATION_EXPRESSION = "domibus.sender.trust.validation.expression";
    String DOMIBUS_SENDER_TRUST_DYNAMIC_RECEIVER_VALIDATION_EXPRESSION = "domibus.sender.trust.dynamicReceiver.validation.expression";
    String DOMIBUS_SENDER_TRUST_VALIDATION_CERTIFICATE_POLICY_OIDS = "domibus.sender.trust.validation.allowedCertificatePolicyOIDs";
    String DOMIBUS_SENDER_CERTIFICATE_SUBJECT_CHECK = "domibus.sender.certificate.subject.check";
    String DOMIBUS_SENDER_TRUST_VALIDATION_TRUSTSTORE_ALIAS = "domibus.sender.trust.validation.truststore_alias";
    String DOMIBUS_DOWNLOAD_CACERTS_ENABLED = "domibus.cacerts.download.enabled";
    String DOMIBUS_SEND_MESSAGE_MESSAGE_ID_PATTERN = "domibus.sendMessage.messageIdPattern";
    String DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED = "domibus.partyinfo.roles.validation.enabled";
    String DOMIBUS_PMODE_LEGCONFIGURATION_MPC_VALIDATION_ENABLED = "domibus.pmode.legconfiguration.mpc.validation.enabled";
    String DOMIBUS_PMODE_LEGCONFIGURATION_MPC_ENABLED = "domibus.pmode.legconfiguration.mpc.enabled";
    String DOMIBUS_PMODE_VALIDATION_ACTION_PATTERN = "domibus.pmode.validation.action.pattern";
    String DOMIBUS_PMODE_VALIDATION_SERVICE_VALUE_PATTERN = "domibus.pmode.validation.service.value.pattern";
    String DOMIBUS_PMODE_VALIDATION_SERVICE_TYPE_PATTERN = "domibus.pmode.validation.service.type.pattern";
    String DOMIBUS_PARTY_ID_TYPE_VALIDATION_PATTERN = "domibus.partIdType.validation.pattern";
    String DOMIBUS_PARTY_ID_TYPE_VALIDATION_MESSAGE = "domibus.partIdType.validation.message";
    String DOMIBUS_DATE_TIME_PATTERN_ON_RECEIVING = "domibus.datetime.pattern.onreceiving";
    String DOMIBUS_DATE_TIME_PATTERN_ON_SENDING = "domibus.datetime.pattern.onsending";
    String DOMIBUS_DISPATCHER_CONNECTION_TIMEOUT = "domibus.dispatcher.connectionTimeout";
    String DOMIBUS_DISPATCHER_RECEIVE_TIMEOUT = "domibus.dispatcher.receiveTimeout";
    String DOMIBUS_DISPATCHER_ALLOW_CHUNKING = "domibus.dispatcher.allowChunking";
    String DOMIBUS_DISPATCHER_CHUNKING_THRESHOLD = "domibus.dispatcher.chunkingThreshold";
    String DOMIBUS_DISPATCHER_CONCURENCY = "domibus.dispatcher.concurency";
    String DOMIBUS_DISPATCHER_LARGE_FILES_CONCURRENCY = "domibus.dispatcher.largeFiles.concurrency";
    String DOMIBUS_DISPATCHER_CACHEABLE = "domibus.dispatcher.cacheable";
    String DOMIBUS_DISPATCHER_CONNECTION_KEEP_ALIVE = "domibus.dispatcher.connection.keepAlive";
    String DOMIBUS_DISPATCHER_PRIORITY = "domibus.dispatcher.priority";
    String DOMIBUS_RETENTION_WORKER_DELETION_STRATEGY = "domibus.retentionWorker.deletion.strategy";
    String DOMIBUS_RETENTION_WORKER_MESSAGE_RETENTION_DOWNLOADED_MAX_DELETE = "domibus.retentionWorker.message.retention.downloaded.max.delete";
    String DOMIBUS_RETENTION_WORKER_MESSAGE_RETENTION_NOT_DOWNLOADED_MAX_DELETE = "domibus.retentionWorker.message.retention.not_downloaded.max.delete";
    String DOMIBUS_RETENTION_WORKER_MESSAGE_RETENTION_SENT_MAX_DELETE = "domibus.retentionWorker.message.retention.sent.max.delete";
    String DOMIBUS_RETENTION_WORKER_MESSAGE_RETENTION_PAYLOAD_DELETED_MAX_DELETE = "domibus.retentionWorker.message.retention.payload_deleted.max.delete";
    String DOMIBUS_RETENTION_WORKER_MESSAGE_RETENTION_BATCH_DELETE = "domibus.retentionWorker.message.retention.batch.delete";
    String DOMIBUS_RETENTION_JMS_CONCURRENCY = "domibus.retention.jms.concurrency";
    String DOMIBUS_PARTITIONS_DROP_CHECK_MESSAGES_EARCHIVED = "domibus.partitions.drop.check.messages.earchived";
    String DOMIBUS_DISPATCH_EBMS_ERROR_UNRECOVERABLE_RETRY = "domibus.dispatch.ebms.error.unrecoverable.retry";
    String DOMIBUS_PROXY_ENABLED = DOMIBUS_PROXY_PREFIX + "enabled";
    String DOMIBUS_PROXY_HTTP_HOST = DOMIBUS_PROXY_PREFIX + "http.host";
    String DOMIBUS_PROXY_HTTP_PORT = DOMIBUS_PROXY_PREFIX + "http.port";
    String DOMIBUS_PROXY_USER = DOMIBUS_PROXY_PREFIX + "user";
    String DOMIBUS_PROXY_PASSWORD = DOMIBUS_PROXY_PREFIX + "password"; //NOSONAR:
    String DOMIBUS_PROXY_NON_PROXY_HOSTS = DOMIBUS_PROXY_PREFIX + "nonProxyHosts";
    String DOMIBUS_PLUGIN_NOTIFICATION_ACTIVE = "domibus.plugin.notification.active";
    String DOMIBUS_NONREPUDIATION_AUDIT_ACTIVE = "domibus.nonrepudiation.audit.active";
    String DOMIBUS_SEND_MESSAGE_FAILURE_DELETE_PAYLOAD = "domibus.sendMessage.failure.delete.payload";
    String DOMIBUS_SEND_MESSAGE_SUCCESS_DELETE_PAYLOAD = "domibus.sendMessage.success.delete.payload";
    String DOMIBUS_SEND_MESSAGE_ATTEMPT_AUDIT_ACTIVE = "domibus.sendMessage.attempt.audit.active";
    String DOMIBUS_LOGGING_PAYLOAD_PRINT = "domibus.logging.payload.print";
    String DOMIBUS_LOGGING_METADATA_PRINT = "domibus.logging.metadata.print";
    String DOMIBUS_LOGGING_REMOTE_CERTIFICATES_PRINT = "domibus.logging.remote.certificates.print";
    String DOMIBUS_LOGGING_LOCAL_CERTIFICATES_PRINT = "domibus.logging.local.certificates.print";
    String DOMIBUS_LOGGING_SEND_MESSAGE_ENQUEUED_MAX_MINUTES = "domibus.logging.sendMessage.enqueued.max.minutes";
    String DOMIBUS_LOGGING_EBMS3_ERROR_PRINT = "domibus.logging.ebms3.error.print";
    String DOMIBUS_LOGGING_CXF_LIMIT = "domibus.logging.cxf.limit";
    String DOMIBUS_CONNECTION_CXF_SSL_OFFLOAD_ENABLE = "domibus.connection.cxf.ssl.offload.enable";
    String DOMIBUS_ATTACHMENT_TEMP_STORAGE_LOCATION = "domibus.attachment.temp.storage.location";
    String DOMIBUS_DISPATCHER_SPLIT_AND_JOIN_CONCURRENCY = "domibus.dispatcher.splitAndJoin.concurrency";
    String DOMIBUS_DISPATCHER_SPLIT_AND_JOIN_PAYLOADS_SCHEDULE_THRESHOLD = "domibus.dispatcher.splitAndJoin.payloads.schedule.threshold";
    String DOMAIN_TITLE = "domain.title";
    String DOMIBUS_USER_INPUT_BLACK_LIST = "domibus.userInput.blackList";
    String DOMIBUS_USER_INPUT_WHITE_LIST = "domibus.userInput.whiteList";
    String DOMIBUS_PROPERTY_LENGTH_MAX = "domibus.property.length.max";
    String DOMIBUS_PROPERTY_VALIDATION_ENABLED = "domibus.property.validation.enabled";
    String DOMIBUS_PROPERTY_BACKUP_PERIOD_MIN = "domibus.property.backup.period.min";
    String DOMIBUS_PROPERTY_BACKUP_HISTORY_MAX = "domibus.property.backup.history.max";
    String DOMIBUS_ACCOUNT_UNLOCK_CRON = "domibus.account.unlock.cron";
    String DOMIBUS_CERTIFICATE_CHECK_CRON = "domibus.certificate.check.cron";
    String DOMIBUS_PLUGIN_ACCOUNT_UNLOCK_CRON = "domibus.plugin.account.unlock.cron";
    String DOMIBUS_PASSWORD_POLICIES_CHECK_CRON = "domibus.passwordPolicies.check.cron";//NOSONAR
    String DOMIBUS_PLUGIN_PASSWORD_POLICIES_CHECK_CRON = "domibus.plugin_passwordPolicies.check.cron";//NOSONAR
    String DOMIBUS_PAYLOAD_TEMP_JOB_RETENTION_CRON = "domibus.payload.temp.job.retention.cron";
    String DOMIBUS_MSH_RETRY_CRON = "domibus.msh.retry.cron";
    String DOMIBUS_RETENTION_WORKER_CRON_EXPRESSION = "domibus.retentionWorker.cronExpression";
    String DOMIBUS_ONGOING_MESSAGES_SANITIZING_WORKER_CRON = "domibus.ongoingMessagesSanitizing.worker.cron";
    String DOMIBUS_ONGOING_MESSAGES_SANITIZING_WORKER_DELAY_HOURS = "domibus.ongoingMessagesSanitizing.worker.delay.hours";
    String DOMIBUS_ONGOING_MESSAGES_SANITIZING_ALERT_LEVEL = "domibus.ongoingMessagesSanitizing.alert.level";
    String DOMIBUS_ONGOING_MESSAGES_SANITIZING_ALERT_SUBJECT = "domibus.ongoingMessagesSanitizing.alert.email.subject";
    String DOMIBUS_ONGOING_MESSAGES_SANITIZING_ALERT_BODY = "domibus.ongoingMessagesSanitizing.alert.email.body";
    String DOMIBUS_MSH_PULL_CRON = "domibus.msh.pull.cron";
    String DOMIBUS_PULL_RETRY_CRON = "domibus.pull.retry.cron";
    String DOMIBUS_ALERT_CLEANER_CRON = "domibus.alert.cleaner.cron";
    String DOMIBUS_ALERT_RETRY_CRON = "domibus.alert.retry.cron";
    String DOMIBUS_SPLIT_AND_JOIN_RECEIVE_EXPIRATION_CRON = "domibus.splitAndJoin.receive.expiration.cron";

    String DOMIBUS_MONITORING_CONNECTION_CRON = "domibus.monitoring.connection.cron";
    String DOMIBUS_MONITORING_CONNECTION_SELF_CRON = "domibus.monitoring.connection.self.cron";
    String DOMIBUS_MONITORING_CONNECTION_PARTY_ENABLED = "domibus.monitoring.connection.party.enabled";
    String DOMIBUS_MONITORING_CONNECTION_DELETE_HISTORY_FOR_PARTIES = "domibus.monitoring.connection.party.history.delete";
    String DOMIBUS_DELETE_RECEIVED_TEST_MESSAGE_HISTORY_CRON = "domibus.monitoring.connection.messages.received.history.delete.cron";

    String DOMIBUS_SMART_RETRY_ENABLED = "domibus.smart.retry.enabled";

    //composable property
    String DOMIBUS_ALERT_MAIL = "domibus.alert.mail";
    String DOMIBUS_ALERT_ACTIVE = "domibus.alert.active";
    String DOMIBUS_ALERT_MAIL_SENDING_ACTIVE = "domibus.alert.mail.sending.active";
    String DOMIBUS_ALERT_MAIL_SMTP_TIMEOUT = "domibus.alert.mail.smtp.timeout";
    String DOMIBUS_ALERT_SENDER_SMTP_URL = DOMIBUS_ALERT_SENDER_SMTP_PREFIX + "url";
    String DOMIBUS_ALERT_SENDER_SMTP_PORT = DOMIBUS_ALERT_SENDER_SMTP_PREFIX + "port";
    String DOMIBUS_ALERT_SENDER_SMTP_USER = DOMIBUS_ALERT_SENDER_SMTP_PREFIX + "user";
    String DOMIBUS_ALERT_SENDER_SMTP_PASSWORD = DOMIBUS_ALERT_SENDER_SMTP_PREFIX + "password";//NOSONAR
    String DOMIBUS_ALERT_SENDER_EMAIL = "domibus.alert.sender.email";
    String DOMIBUS_ALERT_RECEIVER_EMAIL = "domibus.alert.receiver.email";
    String DOMIBUS_ALERT_CLEANER_ALERT_LIFETIME = "domibus.alert.cleaner.alert.lifetime";
    String DOMIBUS_ALERT_RETRY_TIME = "domibus.alert.retry.time";
    String DOMIBUS_ALERT_RETRY_MAX_ATTEMPTS = "domibus.alert.retry.max_attempts";
    String DOMIBUS_ALERT_MSG_COMMUNICATION_FAILURE_ACTIVE = DOMIBUS_ALERT_MSG_COMMUNICATION_FAILURE_PREFIX + ".active";
    String DOMIBUS_ALERT_MSG_COMMUNICATION_FAILURE_STATES = DOMIBUS_ALERT_MSG_COMMUNICATION_FAILURE_PREFIX + ".states";
    String DOMIBUS_ALERT_MSG_COMMUNICATION_FAILURE_LEVEL = DOMIBUS_ALERT_MSG_COMMUNICATION_FAILURE_PREFIX + ".level";
    String DOMIBUS_ALERT_MSG_COMMUNICATION_FAILURE_MAIL_SUBJECT = DOMIBUS_ALERT_MSG_COMMUNICATION_FAILURE_PREFIX + ".mail.subject";
    String DOMIBUS_ALERT_USER_LOGIN_FAILURE_ACTIVE = DOMIBUS_ALERT_USER_LOGIN_FAILURE_PREFIX + ".active";
    String DOMIBUS_ALERT_USER_LOGIN_FAILURE_LEVEL = DOMIBUS_ALERT_USER_LOGIN_FAILURE_PREFIX + ".level";
    String DOMIBUS_ALERT_USER_LOGIN_FAILURE_MAIL_SUBJECT = DOMIBUS_ALERT_USER_LOGIN_FAILURE_PREFIX + ".mail.subject";
    String DOMIBUS_ALERT_USER_ACCOUNT_DISABLED_ACTIVE = DOMIBUS_ALERT_USER_ACCOUNT_DISABLED_PREFIX + ".active";
    String DOMIBUS_ALERT_USER_ACCOUNT_DISABLED_LEVEL = DOMIBUS_ALERT_USER_ACCOUNT_DISABLED_PREFIX + ".level";
    String DOMIBUS_ALERT_USER_ACCOUNT_DISABLED_MOMENT = DOMIBUS_ALERT_USER_ACCOUNT_DISABLED_PREFIX + ".moment";
    String DOMIBUS_ALERT_USER_ACCOUNT_DISABLED_SUBJECT = DOMIBUS_ALERT_USER_ACCOUNT_DISABLED_PREFIX + ".subject";
    String DOMIBUS_ALERT_USER_ACCOUNT_ENABLED_ACTIVE = DOMIBUS_ALERT_USER_ACCOUNT_ENABLED_PREFIX + ".active";
    String DOMIBUS_ALERT_USER_ACCOUNT_ENABLED_LEVEL = DOMIBUS_ALERT_USER_ACCOUNT_ENABLED_PREFIX + ".level";
    String DOMIBUS_ALERT_USER_ACCOUNT_ENABLED_SUBJECT = DOMIBUS_ALERT_USER_ACCOUNT_ENABLED_PREFIX + ".subject";
    String DOMIBUS_ALERT_CERT_IMMINENT_EXPIRATION_ACTIVE = DOMIBUS_ALERT_CERT_IMMINENT_EXPIRATION_PREFIX + ".active";
    String DOMIBUS_ALERT_CERT_IMMINENT_EXPIRATION_DELAY_DAYS = DOMIBUS_ALERT_CERT_IMMINENT_EXPIRATION_PREFIX + ".delay_days";
    String DOMIBUS_ALERT_CERT_IMMINENT_EXPIRATION_FREQUENCY_DAYS = DOMIBUS_ALERT_CERT_IMMINENT_EXPIRATION_PREFIX + ".frequency_days";
    String DOMIBUS_ALERT_CERT_IMMINENT_EXPIRATION_LEVEL = DOMIBUS_ALERT_CERT_IMMINENT_EXPIRATION_PREFIX + ".level";
    String DOMIBUS_ALERT_CERT_IMMINENT_EXPIRATION_MAIL_SUBJECT = DOMIBUS_ALERT_CERT_IMMINENT_EXPIRATION_PREFIX + ".mail.subject";
    String DOMIBUS_ALERT_CERT_EXPIRED_ACTIVE = DOMIBUS_ALERT_CERT_EXPIRED_PREFIX + ".active";
    String DOMIBUS_ALERT_CERT_EXPIRED_FREQUENCY_DAYS = DOMIBUS_ALERT_CERT_EXPIRED_PREFIX + ".frequency_days";
    String DOMIBUS_ALERT_CERT_EXPIRED_DURATION_DAYS = DOMIBUS_ALERT_CERT_EXPIRED_PREFIX + ".duration_days";
    String DOMIBUS_ALERT_CERT_EXPIRED_LEVEL = DOMIBUS_ALERT_CERT_EXPIRED_PREFIX + ".level";
    String DOMIBUS_ALERT_CERT_EXPIRED_MAIL_SUBJECT = DOMIBUS_ALERT_CERT_EXPIRED_PREFIX + ".mail.subject";
    String DOMIBUS_ALERT_PASSWORD_IMMINENT_EXPIRATION_ACTIVE = DOMIBUS_ALERT_PASSWORD_IMMINENT_EXPIRATION_PREFIX + ".active";//NOSONAR
    String DOMIBUS_ALERT_PASSWORD_IMMINENT_EXPIRATION_DELAY_DAYS = DOMIBUS_ALERT_PASSWORD_IMMINENT_EXPIRATION_PREFIX + ".delay_days";//NOSONAR
    String DOMIBUS_ALERT_PASSWORD_IMMINENT_EXPIRATION_FREQUENCY_DAYS = DOMIBUS_ALERT_PASSWORD_IMMINENT_EXPIRATION_PREFIX + ".frequency_days";//NOSONAR
    String DOMIBUS_ALERT_PASSWORD_IMMINENT_EXPIRATION_LEVEL = DOMIBUS_ALERT_PASSWORD_IMMINENT_EXPIRATION_PREFIX + ".level";//NOSONAR
    String DOMIBUS_ALERT_PASSWORD_IMMINENT_EXPIRATION_MAIL_SUBJECT = DOMIBUS_ALERT_PASSWORD_IMMINENT_EXPIRATION_PREFIX + ".mail.subject";//NOSONAR
    String DOMIBUS_ALERT_PASSWORD_EXPIRED_ACTIVE = DOMIBUS_ALERT_PASSWORD_EXPIRED_PREFIX + ".active";//NOSONAR
    String DOMIBUS_ALERT_PASSWORD_EXPIRED_DELAY_DAYS = DOMIBUS_ALERT_PASSWORD_EXPIRED_PREFIX + ".delay_days";//NOSONAR
    String DOMIBUS_ALERT_PASSWORD_EXPIRED_FREQUENCY_DAYS = DOMIBUS_ALERT_PASSWORD_EXPIRED_PREFIX + ".frequency_days";//NOSONAR
    String DOMIBUS_ALERT_PASSWORD_EXPIRED_LEVEL = DOMIBUS_ALERT_PASSWORD_EXPIRED_PREFIX + ".level";//NOSONAR
    String DOMIBUS_ALERT_PASSWORD_EXPIRED_MAIL_SUBJECT = DOMIBUS_ALERT_PASSWORD_EXPIRED_PREFIX + ".mail.subject";//NOSONAR
    String DOMIBUS_ALERT_PLUGIN_PASSWORD_IMMINENT_EXPIRATION_ACTIVE = DOMIBUS_ALERT_PLUGIN_PASSWORD_IMMINENT_EXPIRATION_PREFIX + ".active";//NOSONAR
    String DOMIBUS_ALERT_PLUGIN_PASSWORD_IMMINENT_EXPIRATION_DELAY_DAYS = DOMIBUS_ALERT_PLUGIN_PASSWORD_IMMINENT_EXPIRATION_PREFIX + ".delay_days";//NOSONAR
    String DOMIBUS_ALERT_PLUGIN_PASSWORD_IMMINENT_EXPIRATION_FREQUENCY_DAYS = DOMIBUS_ALERT_PLUGIN_PASSWORD_IMMINENT_EXPIRATION_PREFIX + ".frequency_days";//NOSONAR
    String DOMIBUS_ALERT_PLUGIN_PASSWORD_IMMINENT_EXPIRATION_LEVEL = DOMIBUS_ALERT_PLUGIN_PASSWORD_IMMINENT_EXPIRATION_PREFIX + ".level";//NOSONAR
    String DOMIBUS_ALERT_PLUGIN_PASSWORD_IMMINENT_EXPIRATION_MAIL_SUBJECT = DOMIBUS_ALERT_PLUGIN_PASSWORD_IMMINENT_EXPIRATION_PREFIX + ".mail.subject";//NOSONAR
    String DOMIBUS_ALERT_PLUGIN_PASSWORD_EXPIRED_ACTIVE = DOMIBUS_ALERT_PLUGIN_PASSWORD_EXPIRED_PREFIX + ".active";//NOSONAR
    String DOMIBUS_ALERT_PLUGIN_PASSWORD_EXPIRED_DELAY_DAYS = DOMIBUS_ALERT_PLUGIN_PASSWORD_EXPIRED_PREFIX + ".delay_days";//NOSONAR
    String DOMIBUS_ALERT_PLUGIN_PASSWORD_EXPIRED_FREQUENCY_DAYS = DOMIBUS_ALERT_PLUGIN_PASSWORD_EXPIRED_PREFIX + ".frequency_days";//NOSONAR
    String DOMIBUS_ALERT_PLUGIN_PASSWORD_EXPIRED_LEVEL = DOMIBUS_ALERT_PLUGIN_PASSWORD_EXPIRED_PREFIX + ".level";//NOSONAR
    String DOMIBUS_ALERT_PLUGIN_PASSWORD_EXPIRED_MAIL_SUBJECT = DOMIBUS_ALERT_PLUGIN_PASSWORD_EXPIRED_PREFIX + ".mail.subject";//NOSONAR
    String DOMIBUS_ALERT_PLUGIN_USER_LOGIN_FAILURE_ACTIVE = DOMIBUS_ALERT_PLUGIN_USER_LOGIN_FAILURE_PREFIX + ".active";
    String DOMIBUS_ALERT_PLUGIN_USER_LOGIN_FAILURE_LEVEL = DOMIBUS_ALERT_PLUGIN_USER_LOGIN_FAILURE_PREFIX + ".level";
    String DOMIBUS_ALERT_PLUGIN_USER_LOGIN_FAILURE_MAIL_SUBJECT = DOMIBUS_ALERT_PLUGIN_USER_LOGIN_FAILURE_PREFIX + ".mail.subject";
    String DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_DISABLED_ACTIVE = DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_DISABLED_PREFIX + ".active";
    String DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_DISABLED_LEVEL = DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_DISABLED_PREFIX + ".level";
    String DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_DISABLED_MOMENT = DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_DISABLED_PREFIX + ".moment";
    String DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_DISABLED_SUBJECT = DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_DISABLED_PREFIX + ".subject";
    String DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_ENABLED_ACTIVE = DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_ENABLED_PREFIX + ".active";
    String DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_ENABLED_LEVEL = DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_ENABLED_PREFIX + ".level";
    String DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_ENABLED_SUBJECT = DOMIBUS_ALERT_PLUGIN_USER_ACCOUNT_ENABLED_PREFIX + ".subject";
    String DOMIBUS_ALERT_PARTITION_CHECK_PREFIX = "domibus.alert.partition.check";
    String DOMIBUS_ALERT_PARTITION_CHECK_FREQUENCY_DAYS = DOMIBUS_ALERT_PARTITION_CHECK_PREFIX + ".frequency_days";
    String DOMIBUS_ALERT_EARCHIVING_NOTIFICATION_FAILED_PREFIX = "domibus.alert.earchive.notification";
    String DOMIBUS_ALERT_EARCHIVING_NOTIFICATION_FAILED_ACTIVE = DOMIBUS_ALERT_EARCHIVING_NOTIFICATION_FAILED_PREFIX + ".active";
    String DOMIBUS_ALERT_EARCHIVING_NOTIFICATION_FAILED_LEVEL = DOMIBUS_ALERT_EARCHIVING_NOTIFICATION_FAILED_PREFIX + ".level";
    String DOMIBUS_ALERT_EARCHIVING_NOTIFICATION_FAILED_MAIL_SUBJECT = DOMIBUS_ALERT_EARCHIVING_NOTIFICATION_FAILED_PREFIX + ".mail.subject";

    String DOMIBUS_ALERT_CONNECTION_MONITORING_FAILED_PREFIX = "domibus.alert.connection.monitoring";
    String DOMIBUS_ALERT_CONNECTION_MONITORING_FAILED_PARTIES = DOMIBUS_ALERT_CONNECTION_MONITORING_FAILED_PREFIX + ".parties";
    String DOMIBUS_ALERT_CONNECTION_MONITORING_FAILED_FREQUENCY_DAYS = DOMIBUS_ALERT_CONNECTION_MONITORING_FAILED_PREFIX + ".frequency_days";
    String DOMIBUS_ALERT_CONNECTION_MONITORING_FAILED_LEVEL = DOMIBUS_ALERT_CONNECTION_MONITORING_FAILED_PREFIX + ".level";
    String DOMIBUS_ALERT_CONNECTION_MONITORING_FAILED_MAIL_SUBJECT = DOMIBUS_ALERT_CONNECTION_MONITORING_FAILED_PREFIX + ".mail.subject";

    String DOMIBUS_PULL_REQUEST_SEND_PER_JOB_CYCLE = "domibus.pull.request.send.per.job.cycle";
    String DOMIBUS_PULL_REQUEST_FREQUENCY_RECOVERY_TIME = "domibus.pull.request.frequency.recovery.time";
    String DOMIBUS_PULL_REQUEST_FREQUENCY_ERROR_COUNT = "domibus.pull.request.frequency.error.count";
    String DOMIBUS_PULL_DYNAMIC_INITIATOR = "domibus.pull.dynamic.initiator";
    String DOMIBUS_PULL_MULTIPLE_LEGS = "domibus.pull.multiple_legs";
    String DOMIBUS_PULL_FORCE_BY_MPC = "domibus.pull.force_by_mpc";
    String DOMIBUS_PULL_MPC_INITIATOR_SEPARATOR = "domibus.pull.mpc_initiator_separator";
    String DOMIBUS_PULL_RECEIPT_QUEUE_CONCURRENCY = "domibus.pull.receipt.queue.concurrency";
    String DOMIBUS_PULL_QUEUE_CONCURENCY = "domibus.pull.queue.concurency";

    String DOMIBUS_EXTENSION_IAM_AUTHENTICATION_IDENTIFIER = "domibus.extension.iam.authentication.identifier";
    String DOMIBUS_EXTENSION_IAM_AUTHORIZATION_IDENTIFIER = "domibus.extension.iam.authorization.identifier";
    String DOMIBUS_EXCEPTIONS_REST_ENABLE = "domibus.exceptions.rest.enable";

    String DOMIBUS_PAYLOAD_TEMP_JOB_RETENTION_EXCLUDE_REGEX = "domibus.payload.temp.job.retention.exclude.regex";
    String DOMIBUS_PAYLOAD_TEMP_JOB_RETENTION_EXPIRATION = "domibus.payload.temp.job.retention.expiration";
    String DOMIBUS_PAYLOAD_TEMP_JOB_RETENTION_DIRECTORIES = "domibus.payload.temp.job.retention.directories";
    String DOMIBUS_PAYLOAD_LIMIT_28ATTACHMENTS_PER_MESSAGE = "domibus.payload.limit.28attachments.per.message";
    String DOMIBUS_PAYLOAD_DECOMPRESSION_VALIDATION_ACTIVE = "domibus.payload.decompression.validation.active";
    String DOMIBUS_INSTANCE_NAME = "domibus.instance.name";

    String DOMIBUS_CONFIG_LOCATION = "domibus.config.location";
    String DOMIBUS_DEPLOYMENT_CLUSTERED = "domibus.deployment.clustered";
    String DOMIBUS_SCHEDULER_BOOTSTRAP_SYNCHRONIZED = "domibus.scheduler.bootstrap.synchronized";
    String DOMIBUS_SYNCHRONIZATION_TIMEOUT = "domibus.synchronization.timeout";
    String DOMIBUS_SECURITY_KEY_PRIVATE_PASSWORD = "domibus.security.key.private.password";//NOSONAR
    String DOMIBUS_DATABASE_GENERAL_SCHEMA = "domibus.database.general.schema";
    String DOMIBUS_DATABASE_SCHEMA = "domibus.database.schema";

    // application data source props
    String DOMIBUS_DATASOURCE_DRIVER_CLASS_NAME = "domibus.datasource.driverClassName";
    String DOMIBUS_DATASOURCE_URL = "domibus.datasource.url";
    String DOMIBUS_DATASOURCE_USER = "domibus.datasource.user";
    String DOMIBUS_DATASOURCE_PASSWORD = "domibus.datasource.password";//NOSONAR
    String DOMIBUS_DATASOURCE_MAX_LIFETIME = "domibus.datasource.maxLifetime";
    String DOMIBUS_DATASOURCE_MAX_POOL_SIZE = "domibus.datasource.maxPoolSize";
    String DOMIBUS_DATASOURCE_CONNECTION_TIMEOUT = "domibus.datasource.connectionTimeout";
    String DOMIBUS_DATASOURCE_IDLE_TIMEOUT = "domibus.datasource.idleTimeout";
    String DOMIBUS_DATASOURCE_MINIMUM_IDLE = "domibus.datasource.minimumIdle";
    String DOMIBUS_DATASOURCE_POOL_NAME = "domibus.datasource.poolName";

    // quartz data source props
    String DOMIBUS_QUARTZ_DATASOURCE_DRIVER_CLASS_NAME = "domibus.quartz.datasource.driverClassName";
    String DOMIBUS_QUARTZ_DATASOURCE_URL = "domibus.quartz.datasource.url";
    String DOMIBUS_QUARTZ_DATASOURCE_USER = "domibus.quartz.datasource.user";
    String DOMIBUS_QUARTZ_DATASOURCE_PASSWORD = "domibus.quartz.datasource.password";//NOSONAR
    String DOMIBUS_QUARTZ_DATASOURCE_MAX_LIFETIME = "domibus.quartz.datasource.maxLifetime";
    String DOMIBUS_QUARTZ_DATASOURCE_MAX_POOL_SIZE = "domibus.quartz.datasource.maxPoolSize";
    String DOMIBUS_QUARTZ_DATASOURCE_CONNECTION_TIMEOUT = "domibus.quartz.datasource.connectionTimeout";
    String DOMIBUS_QUARTZ_DATASOURCE_IDLE_TIMEOUT = "domibus.quartz.datasource.idleTimeout";
    String DOMIBUS_QUARTZ_DATASOURCE_MINIMUM_IDLE = "domibus.quartz.datasource.minimumIdle";
    String DOMIBUS_QUARTZ_DATASOURCE_POOL_NAME = "domibus.quartz.datasource.poolName";

    String DOMIBUS_ENTITY_MANAGER_FACTORY_PACKAGES_TO_SCAN = "domibus.entityManagerFactory.packagesToScan";
    String DOMIBUS_ENTITY_MANAGER_FACTORY_JPA_PROPERTY = "domibus.entityManagerFactory.jpaProperty";
    String DOMIBUS_ENTITY_MANAGER_FACTORY_JPA_PROPERTY_HIBERNATE_CONNECTION_DRIVER_CLASS = "domibus.entityManagerFactory.jpaProperty.hibernate.connection.driver_class";
    String DOMIBUS_ENTITY_MANAGER_FACTORY_JPA_PROPERTY_HIBERNATE_DIALECT = "domibus.entityManagerFactory.jpaProperty.hibernate.dialect";
    String DOMIBUS_ENTITY_MANAGER_FACTORY_JPA_PROPERTY_HIBERNATE_ID_NEW_GENERATOR_MAPPINGS = "domibus.entityManagerFactory.jpaProperty.hibernate.id.new_generator_mappings";
    String DOMIBUS_ENTITY_MANAGER_FACTORY_JPA_PROPERTY_HIBERNATE_FORMAT_SQL = "domibus.entityManagerFactory.jpaProperty.hibernate.format_sql";
    String DOMIBUS_ENTITY_MANAGER_FACTORY_JPA_PROPERTY_HIBERNATE_JDBC_FETCH = "domibus.entityManagerFactory.jpaProperty.hibernate.jdbc.fetch_size";
    String DOMIBUS_PASSWORD_ENCRYPTION_ACTIVE = "domibus.password.encryption.active"; //NOSONAR
    String DOMIBUS_PASSWORD_ENCRYPTION_PROPERTIES = "domibus.password.encryption.properties"; //NOSONAR
    String DOMIBUS_PASSWORD_ENCRYPTION_KEY_LOCATION = "domibus.password.encryption.key.location";//NOSONAR
    String DOMIBUS_JMS_QUEUE_PULL = "domibus.jms.queue.pull";
    String DOMIBUS_JMS_CONNECTION_FACTORY_MAX_POOL_SIZE = "domibus.jms.connectionFactory.maxPoolSize";
    String DOMIBUS_JMS_QUEUE_ALERT = "domibus.jms.queue.alert";

    String DOMIBUS_EARCHIVE_QUEUE_CONCURRENCY = "domibus.earchive.queue.concurrency";
    String DOMIBUS_EARCHIVE_NOTIFICATION_QUEUE_CONCURRENCY = "domibus.earchive.notification.queue.concurrency";
    String DOMIBUS_EARCHIVE_NOTIFICATION_DLQ_CONCURRENCY = "domibus.earchive.notification.dlq.concurrency";

    String DOMIBUS_TASK_EXECUTOR_THREAD_COUNT = "domibus.taskExecutor.threadCount";
    String DOMIBUS_MSH_TASK_EXECUTOR_THREAD_COUNT = "domibus.mshTaskExecutor.threadCount";
    String ACTIVE_MQ_BROKER_HOST = "activeMQ.broker.host";
    String ACTIVE_MQ_BROKER_NAME = "activeMQ.brokerName";
    String ACTIVE_MQ_EMBEDDED_CONFIGURATION_FILE = "activeMQ.embedded.configurationFile";
    String ACTIVE_MQ_JMXURL = "activeMQ.JMXURL";
    String ACTIVE_MQ_CONNECTOR_PORT = "activeMQ.connectorPort";
    String ACTIVE_MQ_TRANSPORT_CONNECTOR_URI = "activeMQ.transportConnector.uri";
    String ACTIVE_MQ_USERNAME = "activeMQ.username";
    String ACTIVE_MQ_PASSWORD = "activeMQ.password";//NOSONAR
    String ACTIVE_MQ_PERSISTENT = "activeMQ.persistent";
    String ACTIVE_MQ_CONNECTION_CLOSE_TIMEOUT = "activeMQ.connection.closeTimeout";
    String ACTIVE_MQ_CONNECTION_CONNECT_RESPONSE_TIMEOUT = "activeMQ.connection.connectResponseTimeout";
    String ACTIVE_MQ_ARTEMIS_BROKER = "domibus.jms.activemq.artemis.broker";
    String DOMIBUS_ALERT_QUEUE_CONCURRENCY = "domibus.alert.queue.concurrency";
    String MESSAGE_FACTORY_CLASS = "messageFactoryClass";
    String COMPRESSION_BLACKLIST = "compressionBlacklist";
    String DOMIBUS_JMS_INTERNAL_COMMAND_CONCURENCY = "domibus.jms.internal.command.concurrency";
    String DOMIBUS_INTERNAL_QUEUE_CONCURENCY = "domibus.internal.queue.concurency";
    String DOMIBUS_METRICS_JMX_REPORTER_ENABLE = "domibus.metrics.jmx.reporter.enable";
    String DOMIBUS_METRICS_SLF4J_REPORTER_ENABLE = "domibus.metrics.slf4j.reporter.enable";
    String DOMIBUS_METRICS_SLF4J_REPORTER_PERIOD_TIME_UNIT = "domibus.metrics.slf4j.reporter.period.time.unit";
    String DOMIBUS_METRICS_SLF4J_REPORTER_PERIOD_NUMBER = "domibus.metrics.slf4j.reporter.period.number";
    String DOMIBUS_METRICS_MONITOR_MEMORY = "domibus.metrics.monitor.memory";
    String DOMIBUS_METRICS_MONITOR_GC = "domibus.metrics.monitor.gc";
    String DOMIBUS_METRICS_MONITOR_CACHED_THREADS = "domibus.metrics.monitor.cached.threads";
    String DOMIBUS_METRICS_MONITOR_JMS_QUEUES = "domibus.metrics.monitor.jms.queues";
    String DOMIBUS_SECURITY_EXT_AUTH_PROVIDER_ENABLED = "domibus.security.ext.auth.provider.enabled";
    String DOMIBUS_SECURITY_PROVIDER_BOUNCY_CASTLE_POSITION = "domibus.security.provider.bouncyCastle.position";
    String DOMIBUS_JMX_PASSWORD = "domibus.jmx.password"; //NOSONAR
    String DOMIBUS_JMX_USER = "domibus.jmx.user";
    String WEBLOGIC_MANAGEMENT_SERVER = "weblogic.management.server";
    String DOMIBUS_CLUSTER_COMMAND_CRON_EXPRESSION = "domibus.cluster.command.cronExpression";
    String DOMIBUS_PULL_REQUEST_SEND_PER_JOB_CYCLE_PER_MPC = "domibus.pull.request.send.per.job.cycle.per.mpc";
    String DOMIBUS_FILE_UPLOAD_MAX_SIZE = "domibus.file.upload.maxSize";
    String DOMIBUS_HTTP_SECURITY_STRICT_TRANSPORT_SECURITY = "domibus.httpSecurity.httpStrictTransportSecurity.maxAge";
    String DOMIBUS_MESSAGE_DOWNLOAD_MAX_SIZE = "domibus.message.download.maxSize";
    String DOMIBUS_JDBC_DATASOURCE_JNDI_NAME = "domibus.jdbc.datasource.jndi.name";
    String DOMIBUS_JDBC_DATASOURCE_QUARTZ_JNDI_NAME = "domibus.jdbc.datasource.quartz.jndi.name";
    String DOMIBUS_METRICS_MONITOR_JMS_QUEUES_REFRESH_PERIOD = "domibus.metrics.monitor.jms.queues.refresh.period";
    String DOMIBUS_METRICS_MONITOR_JMS_QUEUES_SHOW_DLQ_ONLY = "domibus.metrics.monitor.jms.queues.show.dlq.only";
    String DOMIBUS_SCHEMAFACTORY = "domibus.javax.xml.validation.SchemaFactory";
    String DOMIBUS_RESEND_BUTTON_ENABLED_RECEIVED_MINUTES = "domibus.ui.resend.action.enabled.received.minutes";
    String DOMIBUS_UI_SESSION_SECURE = "domibus.ui.session.secure";
    String DOMIBUS_UI_SESSION_JVMROUTE = "domibus.ui.session.jvmroute";
    String DOMIBUS_DISPATCHER_TIMEOUT = "domibus.dispatcher.timeout";
    String DOMIBUS_UI_SESSION_TIMEOUT = "domibus.ui.session.timeout";
    String DOMIBUS_UI_SESSION_SAME_SITE = "domibus.ui.session.sameSite";

    String DOMIBUS_UI_SESSION_DATABASE_UNIQUE_NAME = "domibus.ui.session.database.generateUniqueName";

    String DOMIBUS_ERRORLOG_CLEANER_CRON = "domibus.errorlog.cleaner.cron";
    String DOMIBUS_ERRORLOG_CLEANER_OLDER_DAYS = "domibus.errorlog.cleaner.older.days";
    String DOMIBUS_ERRORLOG_CLEANER_BATCH_SIZE = "domibus.errorlog.cleaner.batch.size";

    String DOMIBUS_DYNAMICDISCOVERY_LOOKUP_CACHE_TTL = "domibus.dynamicdiscovery.lookup.cache.ttl";
    String DOMIBUS_DYNAMICDISCOVERY_CLEAN_RETENTION_CRON = "domibus.dynamicdiscovery.lookup.clean.retention.cron";
    String DOMIBUS_DYNAMICDISCOVERY_CLEAN_RETENTION_HOURS = "domibus.dynamicdiscovery.lookup.clean.retention.hours";

    String DOMIBUS_EARCHIVE_ACTIVE = "domibus.earchive.active";
    String DOMIBUS_EARCHIVE_EXPORT_EMPTY = "domibus.earchive.export.empty";
    String DOMIBUS_EARCHIVE_STORAGE_LOCATION = "domibus.earchive.storage.location";
    String DOMIBUS_EARCHIVE_CRON = "domibus.earchive.cron";
    String DOMIBUS_EARCHIVE_SANITY_CRON = "domibus.earchive.sanitizer.cron";
    String DOMIBUS_EARCHIVE_SANITY_DELAY = "domibus.earchive.sanitizer.messagesCheck.delay.hours";
    String DOMIBUS_EARCHIVE_BATCH_SIZE = "domibus.earchive.batch.size";
    String DOMIBUS_EARCHIVE_BATCH_SIZE_PAYLOAD = "domibus.earchive.batch.size.payload";
    String DOMIBUS_EARCHIVE_BATCH_MAX = "domibus.earchive.batch.max";
    String DOMIBUS_EARCHIVE_BATCH_RETRY_TIMEOUT = "domibus.earchive.batch.retry.timeout";
    String DOMIBUS_EARCHIVE_BATCH_MPCS = "domibus.earchive.batch.mpcs";
    String DOMIBUS_EARCHIVE_NOTIFICATION_URL = "domibus.earchive.notification.url";
    String DOMIBUS_EARCHIVE_NOTIFICATION_TIMEOUT = "domibus.earchive.notification.timeout";
    String DOMIBUS_EARCHIVE_NOTIFICATION_USEPROXY = "domibus.earchive.notification.useProxy";
    String DOMIBUS_EARCHIVE_NOTIFICATION_USERNAME = "domibus.earchive.notification.username";
    String DOMIBUS_EARCHIVE_NOTIFICATION_PASSWORD = "domibus.earchive.notification.password"; //NOSONAR
    String DOMIBUS_EARCHIVE_REST_API_RETURN_MESSAGES = "domibus.earchive.rest.messages.return";
    String DOMIBUS_EARCHIVE_RETENTION_DAYS = "domibus.earchive.retention.days";
    String DOMIBUS_EARCHIVE_RETENTION_CRON = "domibus.earchive.retention.cron";
    String DOMIBUS_EARCHIVE_RETENTION_DELETE_MAX = "domibus.earchive.retention.delete.max";
    String DOMIBUS_EARCHIVE_RETENTION_DELETE_DB = "domibus.earchive.retention.delete.db";
    String DOMIBUS_EARCHIVE_START_DATE_STOPPED_ALLOWED_HOURS = "domibus.earchive.start_date.stopped.allowed_hours";
    String DOMIBUS_EARCHIVE_STUCK_CRON = "domibus.earchive.stuck.cron";
    String DOMIBUS_EARCHIVE_STUCK_IGNORE_RECENT_MINUTES = "domibus.earchive.stuck.ignore.recent.minutes";
    String DOMIBUS_ALERT_EARCHIVING_MSG_NON_FINAL_PREFIX = "domibus.alert.earchive.messages_non_final";
    String DOMIBUS_ALERT_EARCHIVING_MSG_NON_FINAL_ACTIVE = DOMIBUS_ALERT_EARCHIVING_MSG_NON_FINAL_PREFIX + ".active";
    String DOMIBUS_ALERT_EARCHIVING_MSG_NON_FINAL_LEVEL = DOMIBUS_ALERT_EARCHIVING_MSG_NON_FINAL_PREFIX + ".level";
    String DOMIBUS_ALERT_EARCHIVING_MSG_NON_FINAL_MAIL_SUBJECT = DOMIBUS_ALERT_EARCHIVING_MSG_NON_FINAL_PREFIX + ".mail.subject";
    String DOMIBUS_ALERT_EARCHIVING_START_DATE_STOPPED_PREFIX = "domibus.alert.earchive.start_date_stopped";
    String DOMIBUS_ALERT_EARCHIVING_START_DATE_STOPPED_ACTIVE = DOMIBUS_ALERT_EARCHIVING_START_DATE_STOPPED_PREFIX + ".active";
    String DOMIBUS_ALERT_EARCHIVING_START_DATE_STOPPED_LEVEL = DOMIBUS_ALERT_EARCHIVING_START_DATE_STOPPED_PREFIX + ".level";
    String DOMIBUS_ALERT_EARCHIVING_START_DATE_STOPPED_MAIL_SUBJECT = DOMIBUS_ALERT_EARCHIVING_START_DATE_STOPPED_PREFIX + ".mail.subject";
    String DOMIBUS_ALERT_EARCHIVING_EXPORT_FAILED_PREFIX = "domibus.alert.earchive.export.failed";
    String DOMIBUS_ALERT_EARCHIVING_EXPORT_FAILED_ACTIVE = DOMIBUS_ALERT_EARCHIVING_EXPORT_FAILED_PREFIX + ".active";
    String DOMIBUS_ALERT_EARCHIVING_EXPORT_FAILED_LEVEL = DOMIBUS_ALERT_EARCHIVING_EXPORT_FAILED_PREFIX + ".level";
    String DOMIBUS_ALERT_EARCHIVING_EXPORT_FAILED_MAIL_SUBJECT = DOMIBUS_ALERT_EARCHIVING_EXPORT_FAILED_PREFIX + ".mail.subject";
    String DOMIBUS_EARCHIVING_NOTIFICATION_DETAILS_ENABLED = "domibus.earchive.notification.details.enabled";
    String DOMIBUS_QUARTZ_TRIGGER_BLOCKED_DURATION = "domibus.quartz.trigger.blocked.duration";
    String DOMIBUS_MESSAGE_RESEND_CRON = "domibus.message.resend.cron";

    //Start distributed cache properties
    String DOMIBUS_DISTRIBUTED_CACHE_DEFAULT_TTL = "domibus.cache.distributed.ttl";
    String DOMIBUS_MESSAGES_STUCK_CRON = "domibus.messages.stuck.cron";
    String DOMIBUS_MESSAGES_STUCK_IGNORE_RECENT_MINUTES = "domibus.messages.stuck.ignore.recent.minutes";

    String DOMIBUS_DISTRIBUTED_CACHE_DEFAULT_SIZE = "domibus.cache.distributed.size";
    String DOMIBUS_DISTRIBUTED_CACHE_MAX_IDLE = "domibus.cache.distributed.idle.max";

    String DOMIBUS_DISTRIBUTED_NEAR_CACHE_DEFAULT_SIZE = "domibus.cache.distributed.nearcache.size";
    String DOMIBUS_DISTRIBUTED_NEAR_CACHE_DEFAULT_TTL = "domibus.cache.distributed.nearcache.ttl";

    String DOMIBUS_DISTRIBUTED_NEAR_CACHE_DEFAULT_MAX_IDLE = "domibus.cache.distributed.nearcache.idle.max";

    String DOMIBUS_DISTRIBUTED_CACHE_PORT = "domibus.cache.distributed.port";
    String DOMIBUS_DISTRIBUTED_CACHE_PORT_AUTOINCREMENT = "domibus.cache.distributed.port.autoincrement";
    String DOMIBUS_DISTRIBUTED_CACHE_PORT_COUNT = "domibus.cache.distributed.port.count";
    String DOMIBUS_DISTRIBUTED_CACHE_MEMBERS = "domibus.cache.distributed.members";
    //End distributed cache properties

    String DOMIBUS_SECURITY_BC_PROVIDER_ORDER="domibus.security.bc.provider.order";

    String DOMIBUS_MESSAGE_TEST_DELIVERY = "domibus.message.test.notification";

    String DOMIBUS_EXTENSIONS_LOCATION = "domibus.extensions.location";

    /**
     *  Controls whether backup tries to preserve file modification data. Some filesystems
     *  (notably Azure Files SMB shares) do not allow this. Default is true.
     */
    String DOMIBUS_BACKUP_PRESERVE_FILE_DATE="domibus.backup.preserveFileDate";
}
