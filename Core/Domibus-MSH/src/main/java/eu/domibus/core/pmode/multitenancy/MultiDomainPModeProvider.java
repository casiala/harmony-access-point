package eu.domibus.core.pmode.multitenancy;

import eu.domibus.api.ebms3.MessageExchangePattern;
import eu.domibus.api.model.*;
import eu.domibus.api.model.ServiceEntity;
import eu.domibus.api.multitenancy.Domain;
import eu.domibus.api.multitenancy.DomainContextProvider;
import eu.domibus.api.pmode.PModeArchiveInfo;
import eu.domibus.api.pmode.ValidationIssue;
import eu.domibus.api.pmode.PModeValidationException;
import eu.domibus.api.util.xml.UnmarshallerResult;
import eu.domibus.api.model.MSHRole;
import eu.domibus.core.ebms3.EbMS3Exception;
import eu.domibus.common.model.configuration.Process;
import eu.domibus.common.model.configuration.*;
import eu.domibus.core.pmode.provider.LegConfigurationPerMpc;
import eu.domibus.core.pmode.provider.PModeProvider;
import eu.domibus.core.pmode.provider.PModeProviderFactoryImpl;
import eu.domibus.core.message.MessageExchangeConfiguration;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.messaging.XmlProcessingException;
import eu.domibus.plugin.ProcessingType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Cosmin Baciu
 * @since 4.0
 */
@org.springframework.stereotype.Service
@Primary
public class MultiDomainPModeProvider extends PModeProvider {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(MultiDomainPModeProvider.class);

    protected volatile Map<Domain, PModeProvider> providerMap = new HashMap<>();

    @Autowired
    protected DomainContextProvider domainContextProvider;

    @Autowired
    protected PModeProviderFactoryImpl pModeProviderFactory;

    @Override
    public void init() {
        //nothing to initialize
    }

    @Override
    public void refresh() {
        getCurrentPModeProvider().refresh();
    }

    public PModeProvider getCurrentPModeProvider() {
        final Domain currentDomain = domainContextProvider.getCurrentDomain();
        LOG.debug("Get domain PMode provider for domain [{}]", currentDomain);
        PModeProvider pModeProvider = providerMap.get(currentDomain);
        if (pModeProvider == null) {
            synchronized (providerMap) {
                // retrieve again from map, otherwise it is null even for the second thread(because the variable has method scope)
                pModeProvider = providerMap.get(currentDomain);
                if (pModeProvider == null) { //NOSONAR: double-check locking
                    LOG.debug("Creating domain PMode provider  for domain [{}]", currentDomain);
                    pModeProvider = pModeProviderFactory.createDomainPModeProvider(currentDomain);
                    providerMap.put(currentDomain, pModeProvider);
                }
            }
        }
        return pModeProvider;
    }

    @Override
    public boolean isConfigurationLoaded() {
        return getCurrentPModeProvider().isConfigurationLoaded();
    }

    @Override
    public List<String> getMpcList() {
        return getCurrentPModeProvider().getMpcList();
    }

    @Override
    public List<String> getMpcURIList() {
        return getCurrentPModeProvider().getMpcURIList();
    }

    @Override
    public String findMpcUri(String mpcName) throws EbMS3Exception {
        return getCurrentPModeProvider().findMpcUri(mpcName);
    }

    @Override
    public String findLegName(String agreementRef, String senderParty, String receiverParty, String service, String action, Role initiatorRole, Role responderRole, ProcessingType processingType, String mpc) throws EbMS3Exception {
        return getCurrentPModeProvider().findLegName(agreementRef, senderParty, receiverParty, service, action, initiatorRole, responderRole, processingType, mpc);
    }

    @Override
    public String findPullLegName(String agreementRef, String senderParty, String receiverParty, String service, String action, String mpc, Role initiatorRole, Role responderRole) throws EbMS3Exception {
        return getCurrentPModeProvider().findPullLegName(agreementRef, senderParty, receiverParty, service, action, mpc, initiatorRole, responderRole);
    }

    @Override
    public String findActionName(String action) throws EbMS3Exception {
        return getCurrentPModeProvider().findActionName(action);
    }

    @Override
    public eu.domibus.common.model.configuration.Mpc findMpc(String mpcValue) throws EbMS3Exception {
        return getCurrentPModeProvider().findMpc(mpcValue);
    }

    @Override
    public String findServiceName(ServiceEntity service) throws EbMS3Exception {
        return getCurrentPModeProvider().findServiceName(service);
    }

    @Override
    public String findServiceName(String service, String serviceType) throws EbMS3Exception {
        return getCurrentPModeProvider().findServiceName(service,serviceType);
    }

    @Override
    public String findPartyName(PartyId partyId) throws EbMS3Exception {
        return getCurrentPModeProvider().findPartyName(partyId);
    }

    @Override
    public String findPartyName(String partyId,String partyIdType) throws EbMS3Exception {
        return getCurrentPModeProvider().findPartyName(partyId,partyIdType);
    }

    @Override
    public MessageExchangeConfiguration findUserMessageExchangeContext(final UserMessage userMessage, final MSHRole mshRole, final boolean isPull, ProcessingType processingType) throws EbMS3Exception {
        return getCurrentPModeProvider().findUserMessageExchangeContext(userMessage, mshRole, isPull, processingType);
    }

    @Override
    public MessageExchangeConfiguration findUserMessageExchangeContext(final UserMessage userMessage, final MSHRole mshRole) throws EbMS3Exception {
        return getCurrentPModeProvider().findUserMessageExchangeContext(userMessage, mshRole);
    }

    @Override
    public String findAgreement(AgreementRefEntity agreementRef) throws EbMS3Exception {
        return getCurrentPModeProvider().findAgreement(agreementRef);
    }

    @Override
    public Party getGatewayParty() {
        return getCurrentPModeProvider().getGatewayParty();
    }

    @Override
    public Party getSenderParty(String pModeKey) {
        return getCurrentPModeProvider().getSenderParty(pModeKey);
    }

    @Override
    public Party getReceiverParty(String pModeKey) {
        return getCurrentPModeProvider().getReceiverParty(pModeKey);
    }

    @Override
    public Party getPartyByIdentifier(String partyIdentifier) {
        return getCurrentPModeProvider().getPartyByIdentifier(partyIdentifier);
    }

    @Override
    public eu.domibus.common.model.configuration.Service getService(String pModeKey) {
        return getCurrentPModeProvider().getService(pModeKey);
    }

    @Override
    public eu.domibus.common.model.configuration.Action getAction(String pModeKey) {
        return getCurrentPModeProvider().getAction(pModeKey);
    }

    @Override
    public Agreement getAgreement(String pModeKey) {
        return getCurrentPModeProvider().getAgreement(pModeKey);
    }

    @Override
    public LegConfiguration getLegConfiguration(String pModeKey) {
        return getCurrentPModeProvider().getLegConfiguration(pModeKey);
    }

    @Override
    public boolean isMpcExistant(String mpc) {
        return getCurrentPModeProvider().isMpcExistant(mpc);
    }

    @Override
    public int getRetentionDownloadedByMpcName(String mpcName) {
        return getCurrentPModeProvider().getRetentionDownloadedByMpcName(mpcName);
    }

    @Override
    public int getRetentionDownloadedByMpcURI(String mpcURI) {
        return getCurrentPModeProvider().getRetentionDownloadedByMpcURI(mpcURI);
    }

    @Override
    public int getRetentionUndownloadedByMpcName(String mpcName) {
        return getCurrentPModeProvider().getRetentionUndownloadedByMpcName(mpcName);
    }

    @Override
    public int getRetentionUndownloadedByMpcURI(String mpcURI) {
        return getCurrentPModeProvider().getRetentionUndownloadedByMpcURI(mpcURI);
    }

    @Override
    public int getRetentionSentByMpcURI(final String mpcURI) {
        return getCurrentPModeProvider().getRetentionSentByMpcURI(mpcURI);
    }

    @Override
    public int getMetadataRetentionOffsetByMpcURI(String mpcURI) {
        return getCurrentPModeProvider().getMetadataRetentionOffsetByMpcURI(mpcURI);
    }

    @Override
    public boolean isDeleteMessageMetadataByMpcURI(final String mpcURI) {
        return getCurrentPModeProvider().isDeleteMessageMetadataByMpcURI(mpcURI);
    }

    @Override
    public int getRetentionMaxBatchByMpcURI(final String mpcURI, final int defaultValue) {
        return getCurrentPModeProvider().getRetentionMaxBatchByMpcURI(mpcURI, defaultValue);
    }

    @Override
    public Role getBusinessProcessRole(String roleValue) throws EbMS3Exception {
        return getCurrentPModeProvider().getBusinessProcessRole(roleValue);
    }

    @Override
    public List<Process> findPullProcessesByMessageContext(MessageExchangeConfiguration messageExchangeConfiguration) {
        return getCurrentPModeProvider().findPullProcessesByMessageContext(messageExchangeConfiguration);
    }

    @Override
    public List<Process> findPullProcessesByInitiator(Party party) {
        return getCurrentPModeProvider().findPullProcessesByInitiator(party);
    }

    @Override
    public List<Process> findPullProcessByMpc(String mpc) {
        return getCurrentPModeProvider().findPullProcessByMpc(mpc);
    }

    @Override
    public List<Process> findAllProcesses() {
        return getCurrentPModeProvider().findAllProcesses();
    }

    @Override
    public List<Party> findAllParties() {
        return getCurrentPModeProvider().findAllParties();
    }

    @Override
    public List<String> findPartiesByInitiatorServiceAndAction(String initiatingPartyId, final String service, final String action, final List<MessageExchangePattern> meps) {
        return getCurrentPModeProvider().findPartiesByInitiatorServiceAndAction(initiatingPartyId, service, action, meps);
    }

    @Override
    public int getMaxRetryTimeout() {
        return getCurrentPModeProvider().getMaxRetryTimeout();
    }

    @Override
    public List<String> findPartiesByResponderServiceAndAction(String responderPartyId, String service, String action, List<MessageExchangePattern> meps) {
        return getCurrentPModeProvider().findPartiesByResponderServiceAndAction(responderPartyId, service, action, meps);
    }

    @Override
    public String getPartyIdType(String partyIdentifier) {
        return getCurrentPModeProvider().getPartyIdType(partyIdentifier);
    }

    @Override
    public String getServiceType(String serviceValue) {
        return getCurrentPModeProvider().getServiceType(serviceValue);
    }

    @Override
    public String getRole(String roleType, String serviceValue) {
        return getCurrentPModeProvider().getRole(roleType, serviceValue);
    }

    @Override
    public Agreement getAgreementRef(String serviceValue) {
        return getCurrentPModeProvider().getAgreementRef(serviceValue);
    }

    @Override
    public LegConfigurationPerMpc getAllLegConfigurations() {
        return getCurrentPModeProvider().getAllLegConfigurations();
    }

    @Override
    public byte[] getPModeFile(long id) {
        return getCurrentPModeProvider().getPModeFile(id);
    }

    @Override
    public ConfigurationRaw getRawConfiguration(long id) {
        return getCurrentPModeProvider().getRawConfiguration(id);
    }

    @Override
    public PModeArchiveInfo getCurrentPmode() {
        return getCurrentPModeProvider().getCurrentPmode();
    }

    @Override
    public void removePMode(long id) {
        getCurrentPModeProvider().removePMode(id);
    }

    @Transactional
    @Override
    public List<PModeArchiveInfo> getRawConfigurationList() {
        return getCurrentPModeProvider().getRawConfigurationList();
    }

    @Override
    public UnmarshallerResult parsePMode(byte[] bytes) throws XmlProcessingException {
        return getCurrentPModeProvider().parsePMode(bytes);
    }

    @Override
    public Configuration getPModeConfiguration(byte[] bytes) throws XmlProcessingException {
        return getCurrentPModeProvider().getPModeConfiguration(bytes);
    }

    @Override
    public List<ValidationIssue> updatePModes(byte[] bytes, String description) throws XmlProcessingException, PModeValidationException {
        return getCurrentPModeProvider().updatePModes(bytes, description);
    }

    @Override
    public UnmarshallerResult unmarshall(byte[] bytes, boolean ignoreWhitespaces) throws XmlProcessingException {
        return getCurrentPModeProvider().unmarshall(bytes, ignoreWhitespaces);
    }

    @Override
    public byte[] serializePModeConfiguration(Configuration configuration) throws XmlProcessingException {
        return getCurrentPModeProvider().serializePModeConfiguration(configuration);
    }

    @Override
    public String getSenderPartyNameFromPModeKey(String pModeKey) {
        return getCurrentPModeProvider().getSenderPartyNameFromPModeKey(pModeKey);
    }

    @Override
    public String getReceiverPartyNameFromPModeKey(String pModeKey) {
        return getCurrentPModeProvider().getReceiverPartyNameFromPModeKey(pModeKey);
    }

    @Override
    public String getServiceNameFromPModeKey(String pModeKey) {
        return getCurrentPModeProvider().getServiceNameFromPModeKey(pModeKey);
    }

    @Override
    public String getActionNameFromPModeKey(String pModeKey) {
        return getCurrentPModeProvider().getActionNameFromPModeKey(pModeKey);
    }

    @Override
    public String getAgreementRefNameFromPModeKey(String pModeKey) {
        return getCurrentPModeProvider().getAgreementRefNameFromPModeKey(pModeKey);
    }

    @Override
    public String getLegConfigurationNameFromPModeKey(String pModeKey) {
        return getCurrentPModeProvider().getLegConfigurationNameFromPModeKey(pModeKey);
    }

    @Override
    public boolean hasLegWithSplittingConfiguration() {
        return getCurrentPModeProvider().hasLegWithSplittingConfiguration();
    }

    @Override
    public Party getPartyByName(String partyName) {
        return getCurrentPModeProvider().getPartyByName(partyName);
    }

    @Override
    public void removeReceiverParty(String partyName) {
        getCurrentPModeProvider().removeReceiverParty(partyName);
    }

    @Override
    public Party removeParty(String partyName) {
        return getCurrentPModeProvider().removeParty(partyName);
    }
}
