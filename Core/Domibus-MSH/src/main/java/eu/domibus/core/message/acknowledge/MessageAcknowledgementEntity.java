package eu.domibus.core.message.acknowledge;

import eu.domibus.api.model.AbstractBaseEntity;
import eu.domibus.api.model.UserMessage;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * @author migueti, Cosmin Baciu
 * @since 3.3
 */
@Entity
@Table(name = "TB_MESSAGE_ACKNW")
@NamedQueries({
        @NamedQuery(name = "MessageAcknowledgement.findMessageAcknowledgementByMessageIdAndRole",
                query = "select messageAcknowledge from MessageAcknowledgementEntity messageAcknowledge where messageAcknowledge.userMessage.messageId = :MESSAGE_ID " +
                        "and messageAcknowledge.userMessage.mshRole=:MSH_ROLE"),
        @NamedQuery(name = "MessageAcknowledgement.deleteMessageAcknowledgementsByMessageIds",
                query = "delete from MessageAcknowledgementEntity messageAcknowledge where messageAcknowledge.userMessage.entityId IN :IDS")

})
public class MessageAcknowledgementEntity extends AbstractBaseEntity {

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_MESSAGE_ID_FK")
    protected UserMessage userMessage;

    @Column(name = "FROM_VALUE")
    private String from;

    @Column(name = "TO_VALUE")
    private String to;

    @Column(name = "ACKNOWLEDGE_DATE")
    private Timestamp acknowledgeDate;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public Timestamp getAcknowledgeDate() {
        return acknowledgeDate;
    }

    public void setAcknowledgeDate(Timestamp acknowledgeDate) {
        this.acknowledgeDate = acknowledgeDate;
    }

    public UserMessage getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(UserMessage userMessage) {
        this.userMessage = userMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        MessageAcknowledgementEntity that = (MessageAcknowledgementEntity) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(from, that.from)
                .append(to, that.to)
                .append(acknowledgeDate, that.acknowledgeDate)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .appendSuper(super.hashCode())
                .append(from)
                .append(to)
                .append(acknowledgeDate)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("from", from)
                .append("to", to)
                .append("acknowledgeDate", acknowledgeDate)
                .toString();
    }
}
