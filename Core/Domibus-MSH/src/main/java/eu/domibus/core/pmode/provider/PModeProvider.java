package eu.domibus.core.pmode.provider;

import eu.domibus.api.cache.CacheConstants;
import eu.domibus.api.cluster.SignalService;
import eu.domibus.api.ebms3.Ebms3Constants;
import eu.domibus.api.ebms3.MessageExchangePattern;
import eu.domibus.api.exceptions.DomibusCoreErrorCode;
import eu.domibus.api.model.*;
import eu.domibus.api.multitenancy.DomainContextProvider;
import eu.domibus.api.pmode.*;
import eu.domibus.api.property.DomibusPropertyMetadataManagerSPI;
import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.api.util.xml.UnmarshallerResult;
import eu.domibus.api.util.xml.XMLUtil;
import eu.domibus.common.ErrorCode;
import eu.domibus.common.JPAConstants;
import eu.domibus.common.model.configuration.Process;
import eu.domibus.common.model.configuration.*;
import eu.domibus.api.cache.DomibusLocalCacheService;
import eu.domibus.core.ebms3.EbMS3Exception;
import eu.domibus.core.ebms3.EbMS3ExceptionBuilder;
import eu.domibus.core.message.MessageExchangeConfiguration;
import eu.domibus.core.message.pull.MpcService;
import eu.domibus.core.pmode.ConfigurationDAO;
import eu.domibus.core.pmode.ConfigurationRawDAO;
import eu.domibus.core.pmode.validation.PModeValidationService;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.logging.DomibusMessageCode;
import eu.domibus.logging.MDCKey;
import eu.domibus.messaging.XmlProcessingException;
import eu.domibus.plugin.ProcessingType;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.SAXException;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * @author Christian Koch, Stefan Mueller
 */
public abstract class PModeProvider {
    public static final String SCHEMAS_DIR = "schemas/";
    public static final String DOMIBUS_PMODE_XSD = "domibus-pmode.xsd";
    protected static final String OPTIONAL_AND_EMPTY = "OAE";
    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(PModeProvider.class);

    @Autowired
    protected ConfigurationDAO configurationDAO;

    @Autowired
    protected ConfigurationRawDAO configurationRawDAO;

    @PersistenceContext(unitName = JPAConstants.PERSISTENCE_UNIT_NAME)
    protected EntityManager entityManager;

    @Autowired
    protected SignalService signalService;

    @Autowired
    protected DomainContextProvider domainContextProvider;

    @Autowired
    protected DomibusPropertyProvider domibusPropertyProvider;

    @Autowired
    XMLUtil xmlUtil;

    @Autowired
    PModeValidationService pModeValidationService;

    @Autowired
    @Qualifier("jaxbContextConfig")
    private JAXBContext jaxbContext;

    @Autowired
    private MpcService mpcService;

    @Autowired
    private DomibusLocalCacheService domibusLocalCacheService;

    protected abstract void init();

    public abstract void refresh();

    public abstract boolean isConfigurationLoaded();

    public abstract boolean hasLegWithSplittingConfiguration();

    public byte[] getPModeFile(long id) {
        final ConfigurationRaw rawConfiguration = getRawConfiguration(id);
        if (rawConfiguration == null) {
            throw new PModeException(DomibusCoreErrorCode.DOM_009, "PMode [" + id + "] does not exist");
        }
        return getRawConfigurationBytes(rawConfiguration);
    }

    private byte[] getRawConfigurationBytes(ConfigurationRaw rawConfiguration) {
        if (rawConfiguration != null) {
            return rawConfiguration.getXml();
        }
        return new byte[0];
    }

    public ConfigurationRaw getRawConfiguration(long id) {
        return this.configurationRawDAO.getConfigurationRaw(id);
    }

    public PModeArchiveInfo getCurrentPmode() {
        final ConfigurationRaw currentRawConfiguration = this.configurationRawDAO.getCurrentRawConfiguration();
        if (currentRawConfiguration != null) {
            return new PModeArchiveInfo(
                    currentRawConfiguration.getEntityId(),
                    currentRawConfiguration.getConfigurationDate(),
                    "",
                    currentRawConfiguration.getDescription());
        }
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void removePMode(long id) {
        LOG.debug("Removing PMode with id: [{}]", id);
        configurationRawDAO.deleteById(id);
    }

    public List<PModeArchiveInfo> getRawConfigurationList() {
        return this.configurationRawDAO.getDetailedConfigurationRaw();
    }

    public UnmarshallerResult parsePMode(byte[] bytes) throws XmlProcessingException {
        UnmarshallerResult result = unmarshall(bytes, true);

        if (!result.isValid()) {
            String errorMessage = "The PMode file is not XSD compliant. Please correct the issues: [" + result.getErrorMessage() + "]";
            XmlProcessingException xmlProcessingException = new XmlProcessingException(errorMessage);
            xmlProcessingException.addErrors(result.getErrors());
            throw xmlProcessingException;
        }

        return unmarshall(bytes, false);
    }

    public Configuration getPModeConfiguration(byte[] bytes) throws XmlProcessingException {
        final UnmarshallerResult unmarshallerResult = parsePMode(bytes);
        return unmarshallerResult.getResult();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_AP_ADMIN')")
    public List<ValidationIssue> updatePModes(byte[] bytes, String description) throws XmlProcessingException, PModeValidationException {
        LOG.debug("Updating the PMode");
        description = validateDescriptionSize(description);

        final UnmarshallerResult unmarshalledConfiguration = parsePMode(bytes);

        List<ValidationIssue> issues = new ArrayList<>();

        Configuration configuration = unmarshalledConfiguration.getResult();

        issues.addAll(pModeValidationService.validate(configuration));

        configurationDAO.updateConfiguration(configuration);

        //save the raw configuration
        final ConfigurationRaw configurationRaw = new ConfigurationRaw();
        configurationRaw.setConfigurationDate(Calendar.getInstance().getTime());
        configurationRaw.setXml(bytes);
        configurationRaw.setDescription(description);
        configurationRawDAO.create(configurationRaw);

        LOG.info("Configuration successfully updated");

        domibusLocalCacheService.clearCache(CacheConstants.DICTIONARY_QUERIES);

        this.refresh();

        // Sends a message into the topic queue in order to refresh all the singleton instances of the PModeProvider.
        signalService.signalPModeUpdate();

        return issues;
    }

    private String validateDescriptionSize(final String description) {
        if (StringUtils.isNotEmpty(description) && description.length() > 255) {
            return description.substring(0, 254);
        }
        return description;
    }


    public UnmarshallerResult unmarshall(byte[] bytes, boolean ignoreWhitespaces) throws XmlProcessingException {
        Configuration configuration;
        UnmarshallerResult unmarshallerResult;

        InputStream xsdStream = getClass().getClassLoader().getResourceAsStream(SCHEMAS_DIR + DOMIBUS_PMODE_XSD);
        ByteArrayInputStream xmlStream = new ByteArrayInputStream(bytes);

        try {
            unmarshallerResult = xmlUtil.unmarshal(ignoreWhitespaces, jaxbContext, xmlStream, xsdStream);
            configuration = unmarshallerResult.getResult();
        } catch (JAXBException | SAXException | ParserConfigurationException | XMLStreamException | NumberFormatException e) {
            LOG.error("Error unmarshalling the PMode", e);
            throw new XmlProcessingException("Error unmarshalling the PMode: " + e.getMessage(), e);
        }
        if (configuration == null) {
            throw new XmlProcessingException("Error unmarshalling the PMode: could not process the PMode file");
        }
        return unmarshallerResult;
    }

    public byte[] serializePModeConfiguration(Configuration configuration) throws XmlProcessingException {

        InputStream xsdStream = getClass().getClassLoader().getResourceAsStream(SCHEMAS_DIR + DOMIBUS_PMODE_XSD);

        byte[] serializedPMode;
        try {
            serializedPMode = xmlUtil.marshal(jaxbContext, configuration, xsdStream);
        } catch (JAXBException | SAXException | ParserConfigurationException | XMLStreamException e) {
            LOG.error("Error marshalling the PMode", e);
            throw new XmlProcessingException("Error marshalling the PMode: " + e.getMessage(), e);
        }
        return serializedPMode;
    }

    @MDCKey({DomibusLogger.MDC_MESSAGE_ID, DomibusLogger.MDC_MESSAGE_ENTITY_ID, DomibusLogger.MDC_FROM, DomibusLogger.MDC_TO, DomibusLogger.MDC_SERVICE, DomibusLogger.MDC_ACTION})
    public MessageExchangeConfiguration findUserMessageExchangeContext(final UserMessage userMessage, final MSHRole mshRole, final boolean isPull, ProcessingType processingType) throws EbMS3Exception {
        return findUserMessageExchangeContext(userMessage, mshRole, isPull, processingType, false);
    }

    @MDCKey({DomibusLogger.MDC_MESSAGE_ID, DomibusLogger.MDC_MESSAGE_ENTITY_ID, DomibusLogger.MDC_FROM, DomibusLogger.MDC_TO, DomibusLogger.MDC_SERVICE, DomibusLogger.MDC_ACTION})
    public MessageExchangeConfiguration findUserMessageExchangeContext(final UserMessage userMessage, final MSHRole mshRole, final boolean isPull, ProcessingType processingType, boolean beforeDynamicDiscovery) throws EbMS3Exception {
        final String agreementName;
        final String service;
        final String action;
        final String leg;
        String mpc;
        String senderParty;
        String receiverParty;

        final String messageId = userMessage.getMessageId();
        //add messageId to MDC map
        if (StringUtils.isNotBlank(messageId)) {
            LOG.putMDC(DomibusLogger.MDC_MESSAGE_ID, messageId);
        }
        LOG.putMDC(DomibusLogger.MDC_FROM, userMessage.getPartyInfo().getFrom().getFromPartyId().getValue());
        LOG.putMDC(DomibusLogger.MDC_TO, userMessage.getPartyInfo().getToParty());
        LOG.putMDC(DomibusLogger.MDC_SERVICE, userMessage.getService().getValue());
        LOG.putMDC(DomibusLogger.MDC_ACTION, userMessage.getActionValue());

        try {
            agreementName = findAgreement(userMessage.getAgreementRef());
            LOG.businessInfo(DomibusMessageCode.BUS_MESSAGE_AGREEMENT_FOUND, agreementName, userMessage.getAgreementRef());

            senderParty = findSenderParty(userMessage);
            receiverParty = findReceiverParty(userMessage, isPull, senderParty, beforeDynamicDiscovery);
            final Role senderRole = findSenderRole(userMessage);
            final Role receiverRole = findReceiverRole(userMessage);

            LOG.debug("Found SenderParty as [{}], senderRole as [{}] and  Receiver Party as [{}], receiverRole as [{}]", senderParty, senderRole, receiverParty, receiverRole);

            service = findServiceName(userMessage.getService());
            LOG.businessInfo(DomibusMessageCode.BUS_MESSAGE_SERVICE_FOUND, service, userMessage.getService());
            action = findActionName(userMessage.getActionValue());
            LOG.businessInfo(DomibusMessageCode.BUS_MESSAGE_ACTION_FOUND, action, userMessage.getActionValue());

            //TODO - refactor EDELIVERY-12876
            Role initiatorRole = senderRole;
            Role responderRole = receiverRole;

            if (isPullContext(isPull, processingType, userMessage.getMpcValue())) { // in pull, the responder is the From party that sends the UserMessage
                LOG.debug("Pull context, switching roles.");
                initiatorRole = receiverRole;

                responderRole = senderRole;
            }
            LOG.info("Found roles initiatorRole=[{}], responderRole=[{}]", initiatorRole, responderRole);

            if (isPull && mpcService.forcePullOnMpc(userMessage.getMpcValue())) {
                mpc = mpcService.extractBaseMpc(userMessage.getMpcValue());
                LOG.debug("Extracted base mpc [{}] ", mpc);
                leg = findPullLegName(agreementName, senderParty, receiverParty, service, action, mpc, initiatorRole, responderRole);
            } else {
                mpc = userMessage.getMpcValue();
                LOG.debug("UserMessage mpc [{}] ", mpc);
                leg = findLegName(agreementName, senderParty, receiverParty, service, action, initiatorRole, responderRole, processingType, mpc);
            }
            LOG.businessInfo(DomibusMessageCode.BUS_LEG_NAME_FOUND, leg, agreementName, senderParty, receiverParty, service, action, mpc);

            if ((StringUtils.equalsIgnoreCase(action, Ebms3Constants.TEST_ACTION) && (!StringUtils.equalsIgnoreCase(service, Ebms3Constants.TEST_SERVICE)))) {
                throw EbMS3ExceptionBuilder.getInstance()
                        .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0010)
                        .message("ebMS3 Test Service: " + Ebms3Constants.TEST_SERVICE + " and ebMS3 Test Action: " + Ebms3Constants.TEST_ACTION + " can only be used together [CORE]")
                        .refToMessageId(messageId)
                        .build();
            }

            MessageExchangeConfiguration messageExchangeConfiguration = new MessageExchangeConfiguration(agreementName, senderParty, receiverParty, service, action, leg, mpc);
            LOG.debug("Found pmodeKey [{}] for message [{}]", messageExchangeConfiguration.getPmodeKey(), userMessage);
            return messageExchangeConfiguration;
        } catch (EbMS3Exception e) {
            e.setRefToMessageId(messageId);
            if (!(isPull && mpcService.forcePullOnMpc(userMessage))) {
                e.setMshRole(mshRole);
            }
            throw e;
        } catch (IllegalStateException ise) {
            // It can happen if DB is clean and no pmodes are configured yet!
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0010)
                    .message("PMode could not be found. Are PModes configured in the database?")
                    .refToMessageId(messageId)
                    .cause(ise)
                    .build();
        }
    }

    protected boolean isPullContext(final boolean isPull, ProcessingType processingType, String mpc) {
        if( (processingType == ProcessingType.PULL) ||
                isPull ||
                mpcService.forcePullOnMpc(mpc)) {
            LOG.debug("Pull context true");
            return true;
        }
        return false;
    }

    protected String findSenderParty(UserMessage userMessage) throws EbMS3Exception {
        String senderParty;
        PartyId fromPartyId = userMessage.getPartyInfo().getFrom().getFromPartyId();
        if (fromPartyId == null) {
            LOG.businessError(DomibusMessageCode.MANDATORY_MESSAGE_HEADER_METADATA_MISSING, "PartyInfo/From/PartyId");
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0003)
                    .message("Mandatory field From PartyId is not provided.")
                    .build();
        }
        try {
            senderParty = findPartyName(fromPartyId);
            LOG.businessInfo(DomibusMessageCode.BUS_SENDER_PARTY_ID_FOUND, senderParty, fromPartyId);
        } catch (EbMS3Exception exc) {
            LOG.businessError(DomibusMessageCode.BUS_SENDER_PARTY_ID_NOT_FOUND, fromPartyId);
            exc.setErrorDetail("Sender party could not be found for the value  " + fromPartyId);
            throw exc;
        }
        return senderParty;
    }

    protected Role findSenderRole(UserMessage userMessage) throws EbMS3Exception {
        String senderRole = userMessage.getPartyInfo().getFrom().getRoleValue();
        if (StringUtils.isBlank(senderRole)) {
            LOG.businessError(DomibusMessageCode.MANDATORY_MESSAGE_HEADER_METADATA_MISSING, "From/Role");
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0003)
                    .message("Mandatory field Sender Role is not provided.")
                    .build();
        }
        return getBusinessProcessRole(senderRole);
    }

    protected String findReceiverParty(UserMessage userMessage, boolean isPull, String senderParty, boolean beforeDynamicDiscovery) throws EbMS3Exception {
        String receiverParty = StringUtils.EMPTY;
        final PartyId toPartyId = userMessage.getPartyInfo().getTo().getToPartyId();
        if (toPartyId == null) {
            if (!beforeDynamicDiscovery) {
                LOG.businessError(DomibusMessageCode.MANDATORY_MESSAGE_HEADER_METADATA_MISSING, "PartyInfo/To/PartyId");
            }
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0003)
                    .message("Mandatory field To PartyId is not provided.")
                    .build();
        }
        try {
            receiverParty = findPartyName(toPartyId);
            LOG.businessInfo(DomibusMessageCode.BUS_RECEIVER_PARTY_ID_FOUND, receiverParty, toPartyId);
        } catch (EbMS3Exception exc) {
            if (isPull && mpcService.forcePullOnMpc(userMessage)) {
                LOG.info("Receiver party not found in pMode, extract from MPC");
                receiverParty = mpcService.extractInitiator(userMessage.getMpcValue());
                exc.setErrorDetail("Receiver Party extracted from MPC is " + receiverParty + ", and SenderParty is " + senderParty);
            } else {
                LOG.businessError(DomibusMessageCode.BUS_RECEIVER_PARTY_ID_NOT_FOUND, toPartyId);
                exc.setErrorDetail((receiverParty.isEmpty()) ? "Receiver party could not be found for the value " + toPartyId : "Receiver Party in Pmode is " + receiverParty + ", and SenderParty is " + senderParty);
                throw exc;
            }
        }
        return receiverParty;
    }

    protected Role findReceiverRole(UserMessage userMessage) throws EbMS3Exception {
        String receiverRole = userMessage.getPartyInfo().getTo().getRoleValue();
        if (StringUtils.isBlank(receiverRole)) {
            LOG.businessError(DomibusMessageCode.MANDATORY_MESSAGE_HEADER_METADATA_MISSING, "To Role");
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0003)
                    .message("Mandatory field Receiver Role is not provided.")
                    .build();
        }
        return getBusinessProcessRole(receiverRole);
    }

    @MDCKey({DomibusLogger.MDC_MESSAGE_ID, DomibusLogger.MDC_MESSAGE_ROLE, DomibusLogger.MDC_MESSAGE_ENTITY_ID})
    public MessageExchangeConfiguration findUserMessageExchangeContext(final UserMessage userMessage, final MSHRole mshRole) throws EbMS3Exception {
        return findUserMessageExchangeContext(userMessage, mshRole, false);
    }

    @MDCKey({DomibusLogger.MDC_MESSAGE_ID, DomibusLogger.MDC_MESSAGE_ROLE, DomibusLogger.MDC_MESSAGE_ENTITY_ID})
    public MessageExchangeConfiguration findUserMessageExchangeContext(final UserMessage userMessage, final MSHRole mshRole, boolean isPull) throws EbMS3Exception {
        return findUserMessageExchangeContext(userMessage, mshRole, isPull, null);
    }

    /**
     * It will check if the messages are sent to the same Domibus instance
     *
     * @param pmodeKey pmode key
     * @return boolean true if there is the same AP
     */
    public boolean checkSelfSending(String pmodeKey) {
        if (BooleanUtils.isNotTrue(domibusPropertyProvider.getBooleanProperty(DomibusPropertyMetadataManagerSPI.DOMIBUS_RECEIVER_SELF_SENDING_VALIDATION_ACTIVE))) {
            LOG.debug("Self sending check is deactivated");
            return false;
        }

        final Party receiver = getReceiverParty(pmodeKey);
        final Party sender = getSenderParty(pmodeKey);

        //check endpoint
        return StringUtils.trimToEmpty(receiver.getEndpoint()).equalsIgnoreCase(StringUtils.trimToEmpty(sender.getEndpoint()));
    }

    public abstract List<String> getMpcList();

    public abstract List<String> getMpcURIList();

    public abstract String findMpcUri(final String mpcName) throws EbMS3Exception;

    public abstract String findLegName(String agreementRef, String senderParty, String receiverParty, String service, String action, Role initiatorRole, Role responderRole, ProcessingType processingType, String mpc) throws EbMS3Exception;

    public abstract String findPullLegName(String agreementRef, String senderParty, String receiverParty, String service, String action, String mpc, Role initiatorRole, Role responderRole) throws EbMS3Exception;

    public abstract String findActionName(String action) throws EbMS3Exception;

    public abstract Mpc findMpc(final String mpcValue) throws EbMS3Exception;

    public abstract String findServiceName(ServiceEntity service) throws EbMS3Exception;

    public abstract String findServiceName(String service, String serviceType) throws EbMS3Exception;

    public abstract String findPartyName(PartyId partyId) throws EbMS3Exception;

    public abstract String findPartyName(String partyId, String partyIdType) throws EbMS3Exception;

    public abstract String findAgreement(AgreementRefEntity agreementRef) throws EbMS3Exception;

    public String findMpcName(String mpcValue) throws EbMS3Exception {
        Mpc mpc;
        try {
            LOG.debug("Find the mpc based on the pullRequest mpc [{}]", mpcValue);
            mpc = findMpc(mpcValue);
        } catch (EbMS3Exception e) {
            LOG.debug("Could not find the mpc [{}], check if base mpc should be used", mpcValue);
            if (mpcService.forcePullOnMpc(mpcValue)) {
                String mpcQualifiedName = mpcService.extractBaseMpc(mpcValue);
                LOG.debug("Trying base mpc [{}]", mpcQualifiedName);
                mpc = findMpc(mpcQualifiedName);
            } else {
                LOG.debug("Base mpc is not to be used, rethrowing the exception", e);
                throw e;
            }
        }
        return mpc.getName();
    }

    public abstract Party getGatewayParty();

    public abstract Party getPartyByIdentifier(String partyIdentifier);

    public abstract Party getSenderParty(String pModeKey);

    public abstract Party getReceiverParty(String pModeKey);

    /**
     * Removes party from the list of responderParties from all processes->responderParties
     */
    public abstract void removeReceiverParty(String partyName);

    public abstract Party getPartyByName(String partyName);

    /**
     * Removes party from the list of available parties businessProcesses->parties
     */
    public abstract Party removeParty(String partyName);

    public abstract Service getService(String pModeKey);

    public abstract Action getAction(String pModeKey);

    public abstract Agreement getAgreement(String pModeKey);

    public abstract LegConfiguration getLegConfiguration(String pModeKey);

    public abstract boolean isMpcExistant(String mpc);

    public abstract int getRetentionDownloadedByMpcName(String mpcName);

    public abstract int getRetentionDownloadedByMpcURI(final String mpcURI);

    public abstract int getRetentionUndownloadedByMpcName(String mpcName);

    public abstract int getRetentionUndownloadedByMpcURI(final String mpcURI);

    public abstract int getRetentionSentByMpcURI(final String mpcURI);

    public abstract boolean isDeleteMessageMetadataByMpcURI(final String mpcURI);

    public abstract int getMetadataRetentionOffsetByMpcURI(String mpc);

    public abstract int getRetentionMaxBatchByMpcURI(final String mpcURI, final int defaultValue);

    public abstract Role getBusinessProcessRole(String roleValue) throws EbMS3Exception;

    public String getSenderPartyNameFromPModeKey(final String pModeKey) {
        return pModeKey.split(PModeConstants.PMODEKEY_SEPARATOR)[0];
    }

    public String getReceiverPartyNameFromPModeKey(final String pModeKey) {
        return pModeKey.split(PModeConstants.PMODEKEY_SEPARATOR)[1];
    }

    public String getServiceNameFromPModeKey(final String pModeKey) {
        return pModeKey.split(PModeConstants.PMODEKEY_SEPARATOR)[2];
    }

    public String getActionNameFromPModeKey(final String pModeKey) {
        return pModeKey.split(PModeConstants.PMODEKEY_SEPARATOR)[3];
    }

    public String getAgreementRefNameFromPModeKey(final String pModeKey) {
        return pModeKey.split(PModeConstants.PMODEKEY_SEPARATOR)[4];
    }

    public String getLegConfigurationNameFromPModeKey(final String pModeKey) {
        return pModeKey.split(PModeConstants.PMODEKEY_SEPARATOR)[5];
    }

    public abstract List<Process> findPullProcessesByMessageContext(final MessageExchangeConfiguration messageExchangeConfiguration);

    public abstract List<Process> findPullProcessesByInitiator(final Party party);

    public abstract List<Process> findPullProcessByMpc(final String mpc);

    public abstract List<Process> findAllProcesses();

    public abstract List<Party> findAllParties();

    public abstract List<String> findPartiesByInitiatorServiceAndAction(String initiatingPartyId, final String service, final String action, final List<MessageExchangePattern> meps);

    public abstract List<String> findPartiesByResponderServiceAndAction(String responderPartyId, final String service, final String action, final List<MessageExchangePattern> meps);

    public abstract String getPartyIdType(String partyIdentifier);

    public abstract String getServiceType(String serviceValue);

    public abstract String getRole(String roleType, String serviceValue);

    public abstract Agreement getAgreementRef(String serviceValue);

    public abstract LegConfigurationPerMpc getAllLegConfigurations();

    public abstract int getMaxRetryTimeout();

}
