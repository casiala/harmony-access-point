package eu.domibus.api.model;

import javax.persistence.*;

@NamedQueries({
        @NamedQuery(name = "SignalMessageRaw.findByUserMessageId", query = "SELECT new eu.domibus.api.model.RawEnvelopeDto(l.entityId,l.rawXML,l.compressed) " +
                "FROM SignalMessageRaw l JOIN l.signalMessage sm " +
                "JOIN sm.userMessage um where um.messageId=:MESSAGE_ID and sm.userMessage.mshRole = :MSH_ROLE"),
        @NamedQuery(name = "SignalMessageRaw.findBySignalMessageEntityId", query = "SELECT new eu.domibus.api.model.RawEnvelopeDto(l.entityId,l.rawXML,l.compressed) " +
                "FROM SignalMessageRaw l where l.signalMessage.entityId=:ENTITY_ID"),
        @NamedQuery(name = "SignalMessageRaw.deleteMessages", query = "delete from SignalMessageRaw mi where mi.entityId in :IDS"),
})
@Entity
@Table(name = "TB_SIGNAL_MESSAGE_RAW")
public class SignalMessageRaw extends RawXmlEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PK")
    @MapsId
    protected SignalMessage signalMessage;

    public SignalMessage getSignalMessage() {
        return signalMessage;
    }

    public void setSignalMessage(SignalMessage signalMessage) {
        this.signalMessage = signalMessage;
    }

}
