package eu.domibus.logging;


import eu.domibus.logging.api.MessageCode;

/**
 * @author Cosmin Baciu
 * @since 3.3
 */
public enum DomibusMessageCode implements MessageCode {

    BUS_MESSAGE_RECEIVED("BUS-001", "Message successfully received from [{}] to [{}]"),
    BUS_MESSAGE_RECEIVE_FAILED("BUS-002", "Failed to receive message from [{}] to [{}]"),
    BUS_MESSAGE_VALIDATION_FAILED("BUS-003", "Failed to validate message"),
    BUS_BACKEND_NOTIFICATION_FAILED("BUS-004", "Failed to notify backend for incoming message"),
    BUS_MESSAGE_CHARSET_INVALID("BUS-005", "Invalid charset [{}] used"),
    BUS_RELIABILITY_INVALID_WITH_NO_SECURITY_HEADER("BUS-006", "Invalid NonRepudiationInformation: no security header found"),
    BUS_RELIABILITY_INVALID_WITH_MULTIPLE_SECURITY_HEADERS("BUS-007", "Invalid NonRepudiationInformation: multiple security headers found"),
    BUS_RELIABILITY_INVALID_WITH_MESSAGING_NOT_SIGNED("BUS-008", "Invalid NonRepudiationInformation: eb:Messaging not signed"),
    BUS_RELIABILITY_INVALID_NOT_MATCHING_THE_MESSAGE("BUS-009", "Invalid NonRepudiationInformation: non repudiation information [{}] and request message do not match [{}]"),
    BUS_RELIABILITY_RECEIPT_INVALID_EMPTY("BUS-010", "There is no content inside the receipt element received by the responding gateway"),
    BUS_RELIABILITY_GENERAL_ERROR("BUS-011", "Reliability check failed, check your configuration"),
    BUS_RELIABILITY_SUCCESSFUL("BUS-012", "Reliability check was successful"),
    BUS_MESSAGE_PAYLOAD_COMPRESSION_FAILURE_MISSING_MIME_TYPE("BUS-013", "Compression failure: no mime type found for payload with cid [{}]"),
    BUS_MESSAGE_PAYLOAD_COMPRESSION_FAILURE("BUS-014", "Error compressing payload with cid [{}]"),
    BUS_MESSAGE_PAYLOAD_COMPRESSION("BUS-015", "Payload with cid [{}] has been compressed"),
    BUS_MESSAGE_PAYLOAD_DECOMPRESSION_FAILURE_MISSING_MIME_TYPE("BUS-016", "Decompression failure: no mime type found for payload with cid [{}]"),
    BUS_MESSAGE_PAYLOAD_DECOMPRESSION("BUS-017", "Payload with cid [{}] will be decompressed"),
    BUS_MESSAGE_PAYLOAD_DECOMPRESSION_NOT_ENABLED("BUS-018", "Decompression is not performed: leg compressPayloads parameter is false"),
    BUS_MESSAGE_PAYLOAD_DECOMPRESSION_PART_INFO_IN_BODY("BUS-019", "Decompression is not performed: partInfo with cid [{}] is in body"),
    BUS_MESSAGE_ACTION_FOUND("BUS-020", "Message action [{}] found for value [{}]"),
    BUS_MESSAGE_ACTION_NOT_FOUND("BUS-021", "Message action not found for value [{}]"),
    BUS_MESSAGE_AGREEMENT_FOUND("BUS-022", "Message agreement [{}] found for value [{}]"),
    BUS_MESSAGE_AGREEMENT_NOT_FOUND("BUS-023", "Message agreement not found for value [{}]"),
    BUS_SENDER_PARTY_ID_FOUND("BUS-024", "Sender Party id [{}] found for value [{}]"),
    BUS_SENDER_PARTY_ID_NOT_FOUND("BUS-025", "Sender Party id not found for value [{}]"),
    BUS_PARTY_ID_INVALID_URI("BUS-026", "Party [{}] is not a valid URI [CORE]"),
    BUS_MESSAGE_SERVICE_FOUND("BUS-027", "Message service [{}] found for value [{}]"),
    BUS_MESSAGE_SERVICE_NOT_FOUND("BUS-028", "Message service not found for value [{}]"),
    BUS_MESSAGE_SERVICE_INVALID_URI("BUS-029", "Message service [{}] is not a valid URI [CORE]"),
    BUS_LEG_NAME_FOUND("BUS-030", "Leg name found [{}] for agreement [{}], senderParty [{}], receiverParty [{}], service [{}] and action [{}]"),
    BUS_LEG_NAME_NOT_FOUND("BUS-031", "Matching Process or Leg not found for agreement [{}], senderParty [{}], receiverParty [{}], service [{}] and action [{}].\nProcess mismatch details:[{}].\nLeg mismatch details:[{}]."),
    BUS_MESSAGE_SEND_INITIATION("BUS-032", "Preparing to send message from [{}] to [{}]"),
    BUS_MESSAGE_SEND_SUCCESS("BUS-033", "Message sent successfully from [{}] to [{}]"),
    BUS_MESSAGE_SEND_FAILURE("BUS-034", "Message sending from [{}] to [{}] failed"),
    BUS_MESSAGE_ATTACHMENT_NOT_FOUND("BUS-035", "No Attachment found for cid [{}]"),
    BUS_MULTIPLE_PART_INFO_REFERENCING_SOAP_BODY("BUS-036", "More than one Partinfo referencing the soap body found"),
    BUS_PAYLOAD_PROFILE_VALIDATION_SKIP("BUS-037", "Payload profile validation skipped: payload profile is not defined for leg [{}]"),
    BUS_PAYLOAD_WITH_CID_MISSING("BUS-038", "Payload profiling for this exchange does not include a payload with CID [{}]"),
    BUS_PAYLOAD_WITH_MIME_TYPE_MISSING("BUS-039", "Payload profiling for this exchange requires all message parts to declare a MimeType property [{}]"),
    BUS_PAYLOAD_MISSING("BUS-040", "Payload profiling error, missing payload [{}]"),
    BUS_PAYLOAD_PROFILE_VALIDATION("BUS-041", "Payload profile [{}] validated"),
    BUS_PROPERTY_PROFILE_VALIDATION_SKIP("BUS-042", "Property profile validation skipped: property profile is not defined for leg [{}]"),
    BUS_PROPERTY_MISSING("BUS-043", "Property profiling for this exchange does not include a property named [{}]"),
    BUS_PROPERTY_PROFILE_VALIDATION("BUS-044", "Property profile [{}] validated"),
    BUS_MESSAGE_PERSISTED("BUS-045", "Message persisted"),
    BUS_MESSAGE_RECEIPT_GENERATED("BUS-046", "Message receipt generated with nonRepudiation value [{}]"),
    BUS_MESSAGE_RECEIPT_FAILURE("BUS-047", "Message receipt generation failure"),
    BUS_MESSAGE_STATUS_UPDATE("BUS-048", "Message with type [{}] has status updated to [{}]"),
    BUS_MESSAGE_PAYLOAD_DATA_CLEARED("BUS-049", "All payloads data for user message have been cleared"),
    BUS_SECURITY_POLICY_OUTGOING_NOT_FOUND("BUS-050", "Security policy [{}] was not found for outgoing message"),
    BUS_SECURITY_POLICY_OUTGOING_USE("BUS-051", "Security policy [{}] is used for outgoing message"),
    BUS_SECURITY_ALGORITHM_OUTGOING_USE("BUS-052", "Security algorithm [{}] is used for outgoing message"),
    BUS_SECURITY_ALGORITHM_INCOMING_USE("BUS-053", "Security algorithm [{}] is used for incoming message"),
    BUS_SECURITY_USER_OUTGOING_USE("BUS-054", "Security encryption username [{}] is used for outgoing message"),
    BUS_SECURITY_POLICY_INCOMING_NOT_FOUND("BUS-055", "Security policy [{}] for incoming message  was not found"),
    BUS_SECURITY_POLICY_INCOMING_USE("BUS-056", "Security policy [{}] for incoming message is used"),
    BUS_PARTY_ROLE_NOT_FOUND("BUS-057", "No Role with value [{}] has been found"),
    BUS_PARTY_NAME_NOT_FOUND("BUS-058", "Party with name [{}] has not been found"),
    BUS_MSG_NOT_FOUND("BUS-059", "Message with id [{}] has not been found"),
    BUS_MSG_CONSUMED("BUS-060", "Message with id [{}] has been consumed from the queue [{}]"),
    BUS_MESSAGE_RECEIVED_PAYLOAD_SIZE("BUS-061", "Received payload with cid [{}] for message [{}] of size [{}] (in bytes)"),
    BUS_MESSAGE_SENDING_PAYLOAD_SIZE("BUS-062", "Saved payload with cid [{}] for message [{}] of size [{}] (in bytes) for sending"),
    BUS_MESSAGE_STATUS_CHANGED("BUS-063", "Notifying about message status change from [{}] to [{}]"),
    BUS_MESSAGE_SUBMITTED("BUS-064", "Message submitted"),
    BUS_MESSAGE_SUBMIT_FAILED("BUS-065", "Message submission failed"),
    BUS_MESSAGE_RETRIEVED("BUS-066", "Message retrieved"),
    BUS_MESSAGE_RETRIEVE_FAILED("BUS-067", "Message retrieval failed"),
    BUS_TEST_MESSAGE_RECEIVED("BUS-068", "Test message successfully received from [{}] to [{}]"),
    BUS_TEST_MESSAGE_RECEIVE_FAILED("BUS-069", "Failed to receive test message from [{}] to [{}]"),
    BUS_TEST_MESSAGE_SEND_INITIATION("BUS-070", "Preparing to send test message from [{}] to [{}]"),
    BUS_TEST_MESSAGE_SEND_SUCCESS("BUS-071", "Test message sent successfully from [{}] to [{}]"),
    BUS_TEST_MESSAGE_SEND_FAILURE("BUS-072", "Test message sending from [{}] to [{}] failed"),
    BUS_MESSAGE_PROPERTY_SIZE_EXCEEDED("BUS-073", "Message property [{}] exceeds [{}] characters limit"),
    BUS_RECEIVER_PARTY_ID_FOUND("BUS-074", "Receiver Party id [{}] found for value [{}]"),
    BUS_RECEIVER_PARTY_ID_NOT_FOUND("BUS-075", "Receiver Party id not found for value [{}]"),
    BUS_PROPERTY_DUPLICATE("BUS-076", "Duplicate Message Property found for property name [{}]"),
    BUS_PAYLOAD_INVALID_SIZE("BUS-077", "Payload size is greater than maximum size [{}] defined in payload profile [{}]"),
    MANDATORY_MESSAGE_HEADER_METADATA_MISSING("BUS-078", "Mandatory Message Header metadata [{}] is not provided."),
    VALUE_LONGER_THAN_DEFAULT_STRING_LENGTH("BUS-079", "Value of [{}] is too long (over 255 characters). Value provided: [{}]"),
    VALUE_DO_NOT_CONFORM_TO_MESSAGEID_PATTERN("BUS-080", "Value of [{}] does not conform to the required MessageIdPattern: [{}]. Value provided: [{}]"),
    DUPLICATE_MESSAGEID("BUS-081", "Message with id [{}] already exists. Message identifiers must be unique."),
    VALUE_LONGER_THAN_STRING_LENGTH_1024("BUS-082", "Value of [{}] is too long (over 1024 characters). Value provided: [{}]"),
    BUS_ARCHIVE_BATCH_CREATE("BUS-083", "Enqueue [{}] archive batch [{}]."),
    BUS_ARCHIVE_BATCH_REEXPORT("BUS-084", "Archiving client requests a manual (re-)export for batch [{}]."),
    BUS_ARCHIVE_BATCH_EXPORTED("BUS-085", "A batch [{}] is exported to file path: {}!"),
    BUS_ARCHIVE_BATCH_NOTIFICATION_SENT("BUS-086", "Export Notification for batch [{}] is sent from Domibus to the archiving client!"),
    BUS_ARCHIVE_BATCH_ARCHIVED_NOTIFICATION_RECEIVED("BUS-087", "Received Archive Notification for batch: [{}] with message: [{}] from the archiving client to Domibus!"),
    BUS_ARCHIVE_BATCH_ERROR_NOTIFICATION_RECEIVED("BUS-088", "Received Archive Failed notification for batch: [{}] with message: [{}] from the archiving client to Domibus!"),
    BUS_ARCHIVE_BATCH_EXPORT_FAILED("BUS-089", "Export failed batch: [{}]. Error message: [{}]!"),
    BUS_ARCHIVE_BATCH_ARCHIVED("BUS-090", "Batch: [{}] with first [{}] and last message: [{}] is Archived."),
    BUS_ATTACHMENTS_MORE_THAN_28("BUS-091", "Maximum number of attachments Domibus can accept in a message is 28."),
    BUS_MESSAGE_STATUS_ROLLBACK("BUS-092", "Message with type [{}] has status rolled back to [{}]"),
    BUS_NOTIFY_MESSAGE_RESPONSE_SENT_ERROR("BUS-093", "An error occurred while notifying plugin [{}] of message response sent."),
    BUS_MESSAGE_PLUGIN_RECEIVE_FAILED("BUS-094", "Failed to receive message in the plugin [{}]"),
    BUS_NOTIFY_MESSAGE_RECEIVED("BUS-095", "Notify message received for messageId [{}] or messageEntityId [{}]."),

    SEC_UNSECURED_LOGIN_ALLOWED("SEC-001", "Unsecure login is allowed, no authentication will be performed"),
    SEC_BASIC_AUTHENTICATION_USE("SEC-002", "Basic authentication is used"),
    SEC_X509CERTIFICATE_AUTHENTICATION_USE("SEC-003", "X509Certificate authentication is used"),
    SEC_BLUE_COAT_AUTHENTICATION_USE("SEC-004", "Blue coat authentication is used"),
    SEC_CONNECTION_ATTEMPT("SEC-005", "The host [{}] attempted to access [{}]"),
    SEC_AUTHORIZED_ACCESS("SEC-006", "The host [{}] has been granted access to [{}] with roles [{}]"),
    SEC_UNAUTHORIZED_ACCESS("SEC-007", "The host [{}] has been refused access to [{}]"),
    SEC_CERTIFICATE_EXPIRED("SEC-008", "Certificate [{}] is not valid at the current date [{}]. Certificate valid from [{}] to [{}]"),
    SEC_CERTIFICATE_NOT_YET_VALID("SEC-009", "Certificate is not yet valid at the current date [{}]. Certificate valid from [{}] to [{}]"),
    SEC_NO_SECURITY_POLICY_USED("SEC-010", "No security policy (intended for testing alone) is used. Security certificate validations will be bypassed!"),
    SEC_UNAUTHORIZED_MESSAGE_ACCESS("SEC-011", "User [{}] is trying to access a message having final recipient: [{}]"),
    SEC_INVALID_X509CERTIFICATE("SEC-012", "X509Certificate invalid or not found"),
    SEC_CONSOLE_LOGIN_UNKNOWN_USER("SEC-013", "The user [{}] is unknown"),
    SEC_CONSOLE_LOGIN_INACTIVE_USER("SEC-014", "The user [{}] is not active"),
    SEC_CONSOLE_LOGIN_SUSPENDED_USER("SEC-015", "The user [{}] is suspended"),
    SEC_CONSOLE_LOGIN_BAD_CREDENTIALS("SEC-016", "The user [{}] is trying to login with bad credentials"),
    SEC_CONSOLE_LOGIN_LOCKED_USER("SEC-017", "The user [{}] is locked after trying to login for [{}] wrong attempts."),
    SEC_CERTIFICATE_SOON_REVOKED("SEC-018", "The [{}] certificate with alias [{}] will be revoked on [{}]."),
    SEC_DOMIBUS_CERTIFICATE_REVOKED("SEC-019", "The [{}] certificate with alias [{}] is revoked since [{}]."),
    SEC_CERTIFICATE_REVOKED("SEC-019", "The certificate [{}] is revoked."),
    SEC_PASSWORD_IMMINENT_EXPIRATION("SEC-020", "The password for user [{}] will expire on [{}]"),
    SEC_PASSWORD_EXPIRED("SEC-021", "The password for user [{}] expired on [{}]"),
    PLUGIN_DEFAULT("ABC-000", "The Plugin could not reach the backend for user [{}] expired on [{}]");//TODO: François Gautier 16-02-21  new code

    String code;
    String message;

    DomibusMessageCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
