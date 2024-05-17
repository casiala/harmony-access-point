package eu.domibus.core.earchive;

import eu.domibus.api.earchive.EArchiveBatchFilter;
import eu.domibus.api.earchive.EArchiveBatchStatus;
import eu.domibus.api.earchive.EArchiveRequestType;
import eu.domibus.api.model.AbstractBaseEntity;
import eu.domibus.api.model.MessageStatus;
import eu.domibus.api.model.MessageStatusEntity;
import eu.domibus.core.dao.BasicDao;
import eu.domibus.core.message.MessageStatusDao;
import eu.domibus.core.util.QueryUtil;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

/**
 * @author François Gautier
 * @since 5.0
 */
@Repository
public class EArchiveBatchDao extends BasicDao<EArchiveBatchEntity> {

    private final QueryUtil queryUtil;

    private final MessageStatusDao messageStatusDao;

    public EArchiveBatchDao(QueryUtil queryUtil, MessageStatusDao messageStatusDao) {
        super(EArchiveBatchEntity.class);
        this.queryUtil = queryUtil;
        this.messageStatusDao = messageStatusDao;
    }

    public EArchiveBatchEntity findEArchiveBatchByBatchEntityId(long entityId) {
        TypedQuery<EArchiveBatchEntity> query = this.em.createNamedQuery("EArchiveBatchEntity.findByEntityId", EArchiveBatchEntity.class);
        query.setParameter("BATCH_ENTITY_ID", entityId);
        return getFirstResult(query);
    }

    public EArchiveBatchEntity findEArchiveBatchByBatchId(String batchId) {
        TypedQuery<EArchiveBatchEntity> query = this.em.createNamedQuery("EArchiveBatchEntity.findByBatchId", EArchiveBatchEntity.class);
        query.setParameter("BATCH_ID", batchId);
        return getFirstResult(query);
    }

    protected <T> T getFirstResult(TypedQuery<T> query) {
        List<T> resultList = query.getResultList();
        if (isEmpty(resultList)) {
            return null;
        }
        return resultList.get(0);
    }

    @Transactional
    public EArchiveBatchEntity setStatus(EArchiveBatchEntity eArchiveBatchByBatchId, EArchiveBatchStatus status, String message, String code) {
        eArchiveBatchByBatchId.setEArchiveBatchStatus(status);
        eArchiveBatchByBatchId.setMessage(message);
        eArchiveBatchByBatchId.setDomibusCode(code);
        return merge(eArchiveBatchByBatchId);
    }

    @Transactional
    public void expireBatches(final Date limitDate) {
        final Query query = em.createNamedQuery("EArchiveBatchEntity.updateStatusByDate");
        query.setParameter("LIMIT_DATE", limitDate);
        query.setParameter("STATUSES", singletonList(EArchiveBatchStatus.EXPORTED));
        query.setParameter("NEW_STATUS", EArchiveBatchStatus.EXPIRED);
        query.executeUpdate();
    }

    public List<EArchiveBatchEntity> findBatchesByStatus(List<EArchiveBatchStatus> statuses, Integer pageSize) {
        TypedQuery<EArchiveBatchEntity> query = this.em.createNamedQuery("EArchiveBatchEntity.findByStatus", EArchiveBatchEntity.class);
        query.setParameter("STATUSES", statuses);
        queryUtil.setPaginationParametersToQuery(query, 0, pageSize);
        return query.getResultList();
    }

    public List<EArchiveBatchEntity> getBatchRequestList(EArchiveBatchFilter filter) {

        CriteriaBuilder builder = em.getCriteriaBuilder();

        CriteriaQuery<EArchiveBatchEntity> criteria = builder.createQuery(EArchiveBatchEntity.class);
        final Root<EArchiveBatchEntity> eArchiveBatchRoot = criteria.from(EArchiveBatchEntity.class);
        criteria.orderBy(builder.desc(eArchiveBatchRoot.get(EArchiveBatchEntity_.dateRequested)));
        List<Predicate> predicates = getPredicates(filter, builder, eArchiveBatchRoot);
        criteria.where(predicates.toArray(new Predicate[0]));
        TypedQuery<EArchiveBatchEntity> batchQuery = em.createQuery(criteria);

        queryUtil.setPaginationParametersToQuery(batchQuery, filter.getPageStart(), filter.getPageSize());

        return batchQuery.getResultList();
    }

    public Long getBatchRequestListCount(EArchiveBatchFilter filter) {
        CriteriaBuilder builder = em.getCriteriaBuilder();

        CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
        Root<EArchiveBatchEntity> eArchiveBatchRoot = criteria.from(EArchiveBatchEntity.class);
        criteria.select(builder.count(eArchiveBatchRoot));
        List<Predicate> predicates = getPredicates(filter, builder, eArchiveBatchRoot);
        criteria.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(criteria).getSingleResult();
    }

    public List<EArchiveBatchUserMessage> getNotArchivedMessages(Long startMessageId, Long endMessageId, Integer pageStart, Integer pageSize) {
        TypedQuery<EArchiveBatchUserMessage> query = this.em.createNamedQuery("UserMessageLog.findMessagesForArchivingAsc", EArchiveBatchUserMessage.class);
        query.setParameter("LAST_ENTITY_ID", startMessageId);
        query.setParameter("MAX_ENTITY_ID", endMessageId);
        query.setParameter("STATUSES", messageStatusDao.getEntitiesOf(MessageStatus.getSuccessfulStates()));
        queryUtil.setPaginationParametersToQuery(query, pageStart, pageSize);
        List<EArchiveBatchUserMessage> res =  query.getResultList();
        res.forEach(eArchiveBatchUserMessage -> {
            MessageStatusEntity entity = messageStatusDao.read(eArchiveBatchUserMessage.getMessageStatusId());
            if (entity != null) {
                eArchiveBatchUserMessage.setMessageStatus(entity.getMessageStatus());
            }
        });
        return res;
    }

    public Long getNotArchivedMessageCountForPeriod(Long startMessageId, Long endMessageId) {
        TypedQuery<Long> query = em.createNamedQuery("UserMessageLog.countMessagesForArchiving", Long.class);
        query.setParameter("LAST_ENTITY_ID", startMessageId);
        query.setParameter("MAX_ENTITY_ID", endMessageId);

        List<MessageStatusEntity> statuses = messageStatusDao.getEntitiesOf(MessageStatus.getSuccessfulStates());
        query.setParameter("STATUS_IDS", statuses);

        return query.getSingleResult();
    }

    private List<Predicate> getPredicates(EArchiveBatchFilter filter, CriteriaBuilder builder, Root<EArchiveBatchEntity> eArchiveBatchRoot) {
        List<Predicate> predicates = new ArrayList<>();
        // filter by batch request date
        if (filter.getStartDate() != null) {
            predicates.add(builder.greaterThanOrEqualTo(eArchiveBatchRoot.get(EArchiveBatchEntity_.dateRequested), filter.getStartDate()));
        }
        if (filter.getEndDate() != null) {
            predicates.add(builder.lessThan(eArchiveBatchRoot.get(EArchiveBatchEntity_.dateRequested), filter.getEndDate()));
        }
        // the "batch MessageId" is a range. Check if start and end message Id falls into the range
        if (filter.getMessageStarId() != null) {
            predicates.add(builder.greaterThanOrEqualTo(eArchiveBatchRoot.get(EArchiveBatchEntity_.firstPkUserMessage), filter.getMessageStarId()));
        }
        if (filter.getMessageEndId() != null) {
            predicates.add(builder.lessThan(eArchiveBatchRoot.get(EArchiveBatchEntity_.lastPkUserMessage), filter.getMessageEndId()));
        }

        // filter by type
        if (filter.getRequestTypes() != null && !filter.getRequestTypes().isEmpty()) {
            Expression<EArchiveRequestType> statusExpression = eArchiveBatchRoot.get(EArchiveBatchEntity_.requestType);
            predicates.add(statusExpression.in(filter.getRequestTypes()));
        }

        // filter by batch status list.
        if (filter.getStatusList() != null && !filter.getStatusList().isEmpty()) {
            Expression<EArchiveBatchStatus> statusExpression = eArchiveBatchRoot.get(EArchiveBatchEntity_.eArchiveBatchStatus);
            predicates.add(statusExpression.in(filter.getStatusList()));
        }

        // by default (null) or if values is false return all batches which do not have exported set to true
        // for returnReExportedBatches return all batches - do not set condition
        if (filter.getIncludeReExportedBatches() == null || !filter.getIncludeReExportedBatches()) {
            // Note: reExported column is false by default and it should not be null
            Expression<Boolean> expression = eArchiveBatchRoot.get(EArchiveBatchEntity_.reExported);
            predicates.add(builder.equal(expression, Boolean.FALSE));
        }
        return predicates;
    }
}
