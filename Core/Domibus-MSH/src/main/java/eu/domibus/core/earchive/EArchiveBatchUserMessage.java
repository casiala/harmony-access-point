package eu.domibus.core.earchive;

import eu.domibus.api.model.DomibusBaseEntity;
import eu.domibus.api.model.MessageStatus;
import eu.domibus.common.JPAConstants;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

/**
 * @author François Gautier
 * @since 5.0
 */
@Entity
@Table(name = "TB_EARCHIVEBATCH_UM")
@NamedQuery(name = "EArchiveBatchUserMessage.findByArchiveBatchEntityId", query = "FROM EArchiveBatchUserMessage batchUms where batchUms.eArchiveBatch.entityId = :batchEntityId")
public class EArchiveBatchUserMessage implements DomibusBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = JPAConstants.DOMIBUS_SCALABLE_SEQUENCE)
    @GenericGenerator(
            name = JPAConstants.DOMIBUS_SCALABLE_SEQUENCE,
            strategy = JPAConstants.DATE_PREFIXED_SEQUENCE_ID_GENERATOR)
    @Column(name = "ID_PK")
    private long entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FK_EARCHIVE_BATCH_ID")
    private EArchiveBatchEntity eArchiveBatch;

    @Column(name = "FK_USER_MESSAGE_ID")
    private Long userMessageEntityId;

    @Column(name = "MESSAGE_ID")
    private String messageId;

    private transient Long messageStatusId;
    private transient MessageStatus messageStatus;

    public EArchiveBatchUserMessage() {
    }

    public EArchiveBatchUserMessage(Long userMessageEntityId, String messageId) {
        this.userMessageEntityId = userMessageEntityId;
        this.messageId = messageId;
    }

    public EArchiveBatchUserMessage(Long userMessageEntityId, String messageId, MessageStatus messageStatus) {
        this.userMessageEntityId = userMessageEntityId;
        this.messageId = messageId;
        this.messageStatus = messageStatus;
    }

    public EArchiveBatchUserMessage(Long userMessageEntityId, String messageId, Long messageStatusId) {
        this.userMessageEntityId = userMessageEntityId;
        this.messageId = messageId;
        this.messageStatusId = messageStatusId;
    }

    public long getEntityId() {
        return entityId;
    }

    public void setEntityId(long entityId) {
        this.entityId = entityId;
    }

    public EArchiveBatchEntity geteArchiveBatch() {
        return eArchiveBatch;
    }

    public void seteArchiveBatch(EArchiveBatchEntity eArchiveBatch) {
        this.eArchiveBatch = eArchiveBatch;
    }

    public Long getUserMessageEntityId() {
        return userMessageEntityId;
    }

    public void setUserMessageEntityId(Long userMessageEntityId) {
        this.userMessageEntityId = userMessageEntityId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String userMessageId) {
        this.messageId = userMessageId;
    }

    public MessageStatus getMessageStatus() {
        return messageStatus;
    }

    public void setMessageStatus(MessageStatus messageStatus) {
        this.messageStatus = messageStatus;
    }

    public Long getMessageStatusId() {
        return messageStatusId;
    }

    @Override
    public String toString() {
        return "EArchiveBatchUserMessage{" +
                "entityId=" + entityId +
                ", userMessageEntityId=" + userMessageEntityId +
                ", messageId='" + messageId + '\'' +
                '}';
    }
}
