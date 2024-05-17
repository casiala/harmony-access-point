package eu.domibus.core.error;

import eu.domibus.api.ebms3.model.Ebms3Error;
import eu.domibus.api.ebms3.model.Ebms3Messaging;
import eu.domibus.api.model.AbstractBaseEntity;
import eu.domibus.api.model.MSHRole;
import eu.domibus.api.model.MSHRoleEntity;
import eu.domibus.api.model.UserMessage;
import eu.domibus.common.ErrorCode;
import eu.domibus.core.ebms3.EbMS3Exception;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.persistence.*;
import java.util.Date;

/**
 * An entry in the error log
 *
 * @author Christian Koch, Stefan Mueller
 */
@Entity
@Table(name = "TB_ERROR_LOG")
@NamedQuery(name = "ErrorLogEntry.findUnnotifiedErrorsByMessageId", query = "select e from ErrorLogEntry e where e.messageInErrorId = :MESSAGE_ID and e.notified is null")
@NamedQuery(name = "ErrorLogEntry.findErrorsByMessageIdAndRole", query = "select e from ErrorLogEntry e where e.messageInErrorId = :MESSAGE_ID and e.mshRole = :MSH_ROLE order by e.timestamp desc")
@NamedQuery(name = "ErrorLogEntry.findErrorsByMessageId", query = "select e from ErrorLogEntry e where e.messageInErrorId = :MESSAGE_ID order by e.timestamp desc")
@NamedQuery(name = "ErrorLogEntry.findEntries", query = "select e from ErrorLogEntry e")
@NamedQuery(name = "ErrorLogEntry.countEntries", query = "select count(e.entityId)  from ErrorLogEntry e")
@NamedQuery(name = "ErrorLogEntry.deleteByMessageEntityIdsInError", query = "delete from ErrorLogEntry e where e.userMessage.entityId IN :MESSAGE_ENTITY_IDS")
@NamedQuery(name = "ErrorLogEntry.findErrorsWithoutMessageIds", query = "select e.entityId from ErrorLogEntry e where e.messageInErrorId is null and e.timestamp<:DELETION_DATE")
@NamedQuery(name = "ErrorLogEntry.deleteErrorsWithoutMessageIds", query = "delete from ErrorLogEntry e where e.entityId IN :ENTITY_IDS")
public class ErrorLogEntry extends AbstractBaseEntity {

    @Column(name = "ERROR_SIGNAL_MESSAGE_ID")
    private String errorSignalMessageId;

    @Column(name = "MESSAGE_IN_ERROR_ID")
    private String messageInErrorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ERROR_CODE")
    private ErrorCode errorCode;

    @Column(name = "ERROR_DETAIL")
    private String errorDetail;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "TIME_STAMP")
    private Date timestamp;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "NOTIFIED")
    private Date notified;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MSH_ROLE_ID_FK")
    private MSHRoleEntity mshRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_MESSAGE_ID_FK")
    private UserMessage userMessage;

    public ErrorLogEntry() {
    }

    /**
     * @param ebms3Exception The Exception to be logged
     */
    public ErrorLogEntry(final EbMS3Exception ebms3Exception) {
        this.messageInErrorId = ebms3Exception.getRefToMessageId();
        this.errorSignalMessageId = ebms3Exception.getSignalMessageId();
        this.errorCode = ebms3Exception.getErrorCodeObject();
        this.errorDetail = ebms3Exception.getErrorDetail();
        this.timestamp = new Date();
    }

    public ErrorLogEntry(MSHRoleEntity mshRole, String messageInErrorId, ErrorCode errorCode, String errorDetail) {
        this.mshRole = mshRole;
        this.messageInErrorId = messageInErrorId;
        this.errorCode = errorCode;
        this.errorDetail = errorDetail;
        this.timestamp = new Date();
    }

    /**
     * Creates an ErrorLogEntry from an ebMS3 signal message
     *
     * @param messaging Signal message containing the error
     * @param role      Role of the MSH
     * @return the new error log entry
     */
    public static ErrorLogEntry parse(final Ebms3Messaging messaging) {
        final Ebms3Error error = messaging.getSignalMessage().getError().iterator().next();

        final ErrorLogEntry errorLogEntry = new ErrorLogEntry();
        errorLogEntry.setTimestamp(messaging.getSignalMessage().getMessageInfo().getTimestamp());
        errorLogEntry.setErrorSignalMessageId(messaging.getSignalMessage().getMessageInfo().getMessageId());
        errorLogEntry.setErrorCode(ErrorCode.findBy(error.getErrorCode()));
        errorLogEntry.setMessageInErrorId(error.getRefToMessageInError());
        errorLogEntry.setErrorDetail(error.getErrorDetail());

        return errorLogEntry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        ErrorLogEntry that = (ErrorLogEntry) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(errorSignalMessageId, that.errorSignalMessageId)
                .append(mshRole, that.mshRole)
                .append(messageInErrorId, that.messageInErrorId)
                .append(errorCode, that.errorCode)
                .append(errorDetail, that.errorDetail)
                .append(timestamp, that.timestamp)
                .append(notified, that.notified)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .appendSuper(super.hashCode())
                .append(errorSignalMessageId)
                .append(mshRole)
                .append(messageInErrorId)
                .append(errorCode)
                .append(errorDetail)
                .append(timestamp)
                .append(notified)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("errorSignalMessageId", errorSignalMessageId)
                .append("mshRole", mshRole)
                .append("messageInErrorId", messageInErrorId)
                .append("errorCode", errorCode)
                .append("errorDetail", errorDetail)
                .append("timestamp", timestamp)
                .append("notified", notified)
                .toString();
    }

    public String getErrorSignalMessageId() {
        return this.errorSignalMessageId;
    }

    public void setErrorSignalMessageId(final String messageId) {
        this.errorSignalMessageId = messageId;
    }

    public MSHRole getMshRole() {
        if (this.mshRole != null) {
            return this.mshRole.getRole();
        }
        return null;
    }

    public String getMessageInErrorId() {
        return this.messageInErrorId;
    }

    public void setMessageInErrorId(final String refToMessageId) {
        this.messageInErrorId = refToMessageId;
    }

    public ErrorCode getErrorCode() {
        return this.errorCode;
    }

    public void setErrorCode(final ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorDetail() {
        return this.errorDetail;
    }

    public void setErrorDetail(final String errorDetail) {
        this.errorDetail = errorDetail;
    }

    public Date getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(final Date timestamp) {
        this.timestamp = timestamp;
    }

    public Date getNotified() {
        return this.notified;
    }

    public void setNotified(final Date notified) {
        this.notified = notified;
    }

    public void setMshRole(MSHRoleEntity mshRole) {
        this.mshRole = mshRole;
    }

    public UserMessage getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(UserMessage userMessage) {
        this.userMessage = userMessage;
    }
}
