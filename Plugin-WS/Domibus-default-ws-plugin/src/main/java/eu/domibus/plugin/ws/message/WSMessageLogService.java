package eu.domibus.plugin.ws.message;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Catalin Enache
 * @since 5.0
 */
public interface WSMessageLogService {

    void create(WSMessageLogEntity wsMessageLogEntity);

    WSMessageLogEntity findByMessageId(final String messageId);

    void delete(WSMessageLogEntity wsMessageLogEntity);

    int deleteByMessageId(final String messageId);

    void deleteByMessageIds(List<String> messageIds);

    List<WSMessageLogEntity> findAllWithFilter(final String messageId, final String fromPartyId,
                                               final String conversationId, final String referenceMessageId, final String originalSender,
                                               final String finalRecipient, LocalDateTime receivedFrom, LocalDateTime receivedTo, int maxPendingMessagesRetrieveCount);
}

