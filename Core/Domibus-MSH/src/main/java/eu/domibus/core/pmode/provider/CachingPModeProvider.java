package eu.domibus.core.pmode.provider;

import com.google.common.collect.Lists;
import eu.domibus.api.ebms3.MessageExchangePattern;
import eu.domibus.api.model.AgreementRefEntity;
import eu.domibus.api.model.PartyId;
import eu.domibus.api.model.ServiceEntity;
import eu.domibus.api.multitenancy.Domain;
import eu.domibus.api.pmode.PModeEventListener;
import eu.domibus.api.pmode.PModeValidationException;
import eu.domibus.api.pmode.ValidationIssue;
import eu.domibus.common.ErrorCode;
import eu.domibus.common.model.configuration.Process;
import eu.domibus.common.model.configuration.*;
import eu.domibus.core.ebms3.EbMS3Exception;
import eu.domibus.core.ebms3.EbMS3ExceptionBuilder;
import eu.domibus.core.exception.ConfigurationException;
import eu.domibus.core.message.MessageExchangeConfiguration;
import eu.domibus.core.message.pull.PullProcessValidator;
import eu.domibus.core.pmode.ProcessPartyExtractorProvider;
import eu.domibus.core.pmode.ProcessTypePartyExtractor;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.logging.DomibusMessageCode;
import eu.domibus.messaging.XmlProcessingException;
import eu.domibus.plugin.ProcessingType;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static eu.domibus.api.ebms3.MessageExchangePattern.*;
import static eu.domibus.api.property.DomibusPropertyMetadataManagerSPI.*;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.*;

/**
 * @author Cosmin Baciu, Thomas Dussart, Ioana Dragusanu
 */
public class CachingPModeProvider extends PModeProvider {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(CachingPModeProvider.class);
    private static final String DOES_NOT_MATCH_END_STRING = "] does not match";
    private static final String CURRENTLY_UNAVAILABLE = "currently unavailable";

    protected Domain domain;

    @Autowired
    private PullProcessValidator pullProcessValidator;

    //Don't access directly, use getter instead
    private volatile Configuration configuration;

    @Autowired
    private ProcessPartyExtractorProvider processPartyExtractorProvider;

    @Autowired
    protected List<PModeEventListener> pModeEventListeners;

    //pull processes cache.
    private Map<Party, List<Process>> pullProcessesByInitiatorCache = new HashMap<>();

    private Map<String, List<Process>> pullProcessByMpcCache = new HashMap<>();

    private final Object configurationLock;

    public CachingPModeProvider(Domain domain) {
        this.domain = domain;
        configurationLock = ConfigurationLockContainer.getForDomain(domain);
    }

    public Configuration getConfiguration() {
        if (this.configuration == null) {
            synchronized (configurationLock) {
                if (this.configuration == null) {
                    this.init();
                }
            }
        }
        return this.configuration;
    }

    @Override
    public Party getGatewayParty() {
        return getConfiguration().getParty();
    }

    @Override
    protected void init() {
        if (!this.configurationDAO.configurationExists()) {
            throw new IllegalStateException("No processing modes found. To exchange messages, upload configuration file through the web gui.");
        }
        load();
    }

    protected void load() {
        LOG.debug("Initialising the configuration");
        try {
            this.configuration = this.configurationDAO.readEager();
            LOG.debug("Configuration initialized: [{}]", this.configuration.getEntityId());

            initPullProcessesCache();
        } catch (Exception ex) {
            throw new ConfigurationException("Could not load the pMode. Please ensure there is a pMode uploaded.", ex);
        }
    }

    private void initPullProcessesCache() {
        final Set<Mpc> mpcs = getConfiguration().getMpcs();
        for (Mpc mpc : mpcs) {
            final String qualifiedName = mpc.getQualifiedName();
            final List<Process> pullProcessByMpc = getPullProcessByMpc(qualifiedName);
            pullProcessByMpcCache.put(qualifiedName, pullProcessByMpc);
        }

        Set<Party> initiatorsForPullProcesses = getInitiatorsForPullProcesses();
        for (Party initiator : initiatorsForPullProcesses) {
            final List<Process> pullProcessesByInitiator = getPullProcessesWithInitiator(initiator);
            pullProcessesByInitiatorCache.put(initiator, pullProcessesByInitiator);
        }
    }

    protected List<Process> getPullProcessesWithInitiator(Party initiator) {
        final List<Process> pullProcesses = getAllPullProcesses();
        return pullProcesses.stream()
                .filter(process -> hasInitiatorParty(process, initiator.getName()))
                .collect(Collectors.toList());
    }

    protected List<Process> getAllPullProcesses() {
        final List<Process> processes = getConfiguration().getBusinessProcesses().getProcesses();
        return processes.stream()
                .filter(this::isPullProcess)
                .collect(Collectors.toList());
    }

    protected Set<Party> getInitiatorsForPullProcesses() {
        Set<Party> initiators = new HashSet<>();
        final List<Process> pullProcesses = getAllPullProcesses();
        pullProcesses.stream()
                .map(Process::getInitiatorParties)
                .forEach(initiators::addAll);
        return initiators;
    }

    protected List<Process> getPullProcessByMpc(final String mpcQualifiedName) {
        List<Process> result = new ArrayList<>();

        final List<Process> pullProcesses = getAllPullProcesses();
        for (Process process : pullProcesses) {
            if (isProcessMatchingMpcLeg(process, mpcQualifiedName)) {
                LOG.debug("Matched pull process [{}] with mpc [{}]", process.getName(), mpcQualifiedName);
                result.add(process);
            }
        }
        return result;
    }

    protected boolean isProcessMatchingMpcLeg(Process process, final String mpcQualifiedName) {
        Set<LegConfiguration> legConfigurations = process.getLegs();
        if (legConfigurations == null) {
            return false;
        }
        return legConfigurations.stream()
                .anyMatch(legConfiguration -> StringUtils.equals(legConfiguration.getDefaultMpc().getQualifiedName(), mpcQualifiedName));
    }


    /**
     * The match means that either has an Agreement and its name matches the Agreement name found previously
     * or it has no Agreement configured and the Agreement name was not indicated in the submitted message.
     *
     * @param process       the process containing the agreement
     * @param agreementName the agreement name
     */
    protected boolean matchAgreement(Process process, String agreementName) {
        return (process.getAgreement() != null && equalsIgnoreCase(process.getAgreement().getName(), agreementName)
                || (equalsIgnoreCase(agreementName, OPTIONAL_AND_EMPTY) && process.getAgreement() == null)
                // Please notice that this is only for backward compatibility and will be removed ASAP!
                || (equalsIgnoreCase(agreementName, OPTIONAL_AND_EMPTY) && process.getAgreement() != null && StringUtils.isEmpty(process.getAgreement().getValue()))
        );
    }

    protected void checkAgreementMismatch(Process process, LegFilterCriteria legFilterCriteria) {
        if (matchAgreement(process, legFilterCriteria.getAgreementName())) {
            LOG.debug("Agreement:[{}] matched for Process:[{}]", legFilterCriteria.getAgreementName(), process.getName());
            return;
        }
        legFilterCriteria.appendProcessMismatchErrors(process, "Agreement:[" + legFilterCriteria.getAgreementName() + DOES_NOT_MATCH_END_STRING);
    }

    /**
     * The match means that either there is no initiator and it is allowed
     * by configuration OR the initiator name matches
     *
     * @param process     the process containing the initiators
     * @param senderParty the senderParty
     */
    protected boolean matchInitiator(final Process process, final String senderParty) {
        if (CollectionUtils.isEmpty(process.getInitiatorParties())) {
            if (pullProcessValidator.allowDynamicInitiatorInPullProcess()) {
                return true;
            }
            return false;
        }

        for (final Party party : process.getInitiatorParties()) {
            if (equalsIgnoreCase(party.getName(), senderParty)) {
                return true;
            }
        }
        return false;
    }

    protected void checkInitiatorMismatch(Process process, ProcessTypePartyExtractor processTypePartyExtractor, LegFilterCriteria legFilterCriteria) {
        if (matchInitiator(process, processTypePartyExtractor.getSenderParty())) {
            LOG.debug("Initiator:[{}] matched for Process:[{}]", processTypePartyExtractor.getSenderParty(), process.getName());
            return;
        }
        legFilterCriteria.appendProcessMismatchErrors(process, "Initiator:[" + processTypePartyExtractor.getSenderParty() + DOES_NOT_MATCH_END_STRING);
    }

    /**
     * The match requires that the responder exists in the process
     *
     * @param process       the process containing the responder
     * @param receiverParty the receiverParty
     */
    protected boolean matchResponder(final Process process, final String receiverParty) {
        final Party responderParty = getResponderParty(process, receiverParty);
        if (responderParty != null) {
            return true;
        }
        return false;
    }

    protected Party getResponderParty(final Process process, final String receiverParty) {
        if (CollectionUtils.isEmpty(process.getResponderParties())) {
            return null;
        }
        for (final Party party : process.getResponderParties()) {
            if (equalsIgnoreCase(party.getName(), receiverParty)) {
                return party;
            }
        }
        return null;
    }

    protected void checkResponderMismatch(Process process, ProcessTypePartyExtractor processTypePartyExtractor, LegFilterCriteria legFilterCriteria) {
        if (matchResponder(process, processTypePartyExtractor.getReceiverParty())) {
            LOG.debug("Responder:[{}] matched for Process:[{}]", processTypePartyExtractor.getReceiverParty(), process.getName());
            return;
        }
        legFilterCriteria.appendProcessMismatchErrors(process, "Responder:[" + processTypePartyExtractor.getReceiverParty() + DOES_NOT_MATCH_END_STRING);
    }

    @Override
    public String findPullLegName(final String agreementName, final String senderParty,
                                  final String receiverParty, final String service, final String action, final String mpc, final Role initiatorRole, final Role responderRole) throws EbMS3Exception {
        final List<LegConfiguration> candidates = new ArrayList<>();
        ProcessTypePartyExtractor processTypePartyExtractor = processPartyExtractorProvider.getProcessTypePartyExtractor(
                ONE_WAY_PULL.getUri(), senderParty, receiverParty);
        List<Process> processes = this.getConfiguration().getBusinessProcesses().getProcesses();
        processes = processes.stream().filter(process -> matchAgreement(process, agreementName))
                .filter(process -> process.getMepBinding() != null && matchMepBinding(process.getMepBinding().getValue(), MessageExchangePattern.ONE_WAY_PULL.getUri()))
                .filter(process -> matchRole(process.getInitiatorRole(), initiatorRole))
                .filter(process -> matchRole(process.getResponderRole(), responderRole))
                .filter(process -> ONE_WAY_PULL.getUri().equals(process.getMepBinding().getValue()))
                .filter(process -> matchInitiator(process, processTypePartyExtractor.getSenderParty()))
                .filter(process -> matchResponder(process, processTypePartyExtractor.getReceiverParty())).collect(Collectors.toList());

        processes.stream().forEach(process -> candidates.addAll(process.getLegs()));
        if (candidates.isEmpty()) {
            LOG.businessError(DomibusMessageCode.BUS_LEG_NAME_NOT_FOUND, agreementName, senderParty, receiverParty, service, action, CURRENTLY_UNAVAILABLE, CURRENTLY_UNAVAILABLE);
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0001)
                    .message("No Candidates for Legs found")
                    .build();
        }
        Optional<LegConfiguration> optional = candidates.stream()
                .filter(candidate -> candidateMatches(candidate, service, action, mpc))
                .findFirst();
        String pullLegName = optional.isPresent() ? optional.get().getName() : null;
        if (pullLegName != null) {
            return pullLegName;
        }
        LOG.businessError(DomibusMessageCode.BUS_LEG_NAME_NOT_FOUND, agreementName, senderParty, receiverParty, service, action, CURRENTLY_UNAVAILABLE, CURRENTLY_UNAVAILABLE);
        throw EbMS3ExceptionBuilder.getInstance()
                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0001)
                .message("No matching leg found")
                .build();
    }

    protected boolean candidateMatches(LegConfiguration candidate, String service, String action, String mpc) {
        if (equalsIgnoreCase(candidate.getService().getName(), service)
                && equalsIgnoreCase(candidate.getAction().getName(), action)
                && equalsIgnoreCase(candidate.getDefaultMpc().getQualifiedName(), mpc)) {
            return true;
        }
        return false;
    }

    /**
     * From the list of processes in the pmode {@link Configuration}, filters the list of {@link Process} matching the input parameters
     * and then filters the list of {@link LegConfiguration} matching the input parameters.<br/>
     * If several candidate leg configurations match, returns only the first leg configuration that matches.<br/>
     * Meant for use with PUSH message exchange patterns as filtering with MEP and MPC are not considered.<br/>
     * If no processes or legs match, throws {@link EbMS3Exception} with details of all mismatches across processes and legs<br/>
     */
    @Override
    public String findLegName(final String agreementName, final String senderParty, final String receiverParty,
                              final String service, final String action, final Role initiatorRole, final Role responderRole, ProcessingType processingType, String mpc) throws EbMS3Exception {

        LegFilterCriteria legFilterCriteria = new LegFilterCriteria(agreementName, senderParty, receiverParty, initiatorRole, responderRole, service, action, processingType, mpc);

        final List<Process> matchingProcesses = filterMatchingProcesses(legFilterCriteria);
        if (matchingProcesses.isEmpty()) {
            String errorDetail = "None of the Processes matched with message metadata. Process mismatch details:\n" + legFilterCriteria.getProcessMismatchErrorDetails();
            LOG.businessError(DomibusMessageCode.BUS_LEG_NAME_NOT_FOUND, agreementName, senderParty, receiverParty, service, action, legFilterCriteria.getProcessMismatchErrorDetails(), legFilterCriteria.getLegConfigurationMismatchErrorDetails());
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0001)
                    .message(errorDetail)
                    .build();
        }

        final Set<LegConfiguration> matchingLegs = filterMatchingLegConfigurations(matchingProcesses, legFilterCriteria);
        if (matchingLegs.isEmpty()) {
            StringBuilder buildErrorDetail = new StringBuilder("No matching Legs found among matched Processes:[").append(listProcessNames(matchingProcesses)).append("]. Leg mismatch details:")
                    .append("\n").append(legFilterCriteria.getLegConfigurationMismatchErrorDetails());
            if (!legFilterCriteria.getProcessMismatchErrors().isEmpty()) {
                buildErrorDetail.append("\n").append("Other Process mismatch details:")
                        .append("\n").append(legFilterCriteria.getProcessMismatchErrorDetails());
            }
            String errorDetail = buildErrorDetail.toString();
            LOG.businessError(DomibusMessageCode.BUS_LEG_NAME_NOT_FOUND, agreementName, senderParty, receiverParty, service, action, legFilterCriteria.getProcessMismatchErrorDetails(), legFilterCriteria.getLegConfigurationMismatchErrorDetails());
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0001)
                    .message(errorDetail)
                    .build();
        }

        Optional<LegConfiguration> selectedLeg = matchingLegs.stream().findFirst();
        return selectedLeg.map(LegConfiguration::getName).orElse(null);
    }

    /**
     * From the list of {@link Process} retrieved from the pmode configuration, finds the mismatches with the message metadata
     * provided through a {@link LegFilterCriteria} and filters the processes that match - i.e; having no mismatch errors.
     * Incorrect reuse is guarded by returning empty list in case null is provided as input.
     *
     * @param legFilterCriteria
     * @return List of {@link Process} having no mimatches.
     */
    private List<Process> filterMatchingProcesses(LegFilterCriteria legFilterCriteria) {
        if (legFilterCriteria == null) {
            return new ArrayList<>();
        }
        List<Process> allProcesses = findAllProcesses();
        LOG.debug("All processes:");
        logProcesses(allProcesses);

        List<Process> candidateProcesses = filterProcessesByProcessingType(legFilterCriteria.getProcessingType(),
                allProcesses);
        LOG.debug("Filtered processes:");
        logProcesses(candidateProcesses);

        for (Process process : candidateProcesses) {
            ProcessTypePartyExtractor processTypePartyExtractor = processPartyExtractorProvider.getProcessTypePartyExtractor(process.getMepBinding().getValue(), legFilterCriteria.getSenderParty(), legFilterCriteria.getReceiverParty());
            checkAgreementMismatch(process, legFilterCriteria);
            checkInitiatorMismatch(process, processTypePartyExtractor, legFilterCriteria);
            checkResponderMismatch(process, processTypePartyExtractor, legFilterCriteria);
            checkInitiatorRoleMismatch(process, legFilterCriteria);
            checkResponderRoleMismatch(process, legFilterCriteria);
        }
        candidateProcesses.removeAll(legFilterCriteria.listProcessesWithMismatchErrors());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Names of matched processes:[{}]", listProcessNames(candidateProcesses));
        }
        return candidateProcesses;
    }

    private void logProcesses(List<Process> allProcesses) {
        if (LOG.isDebugEnabled()) {
            for (Process process : allProcesses) {
                LOG.debug("[{}]", process.getName());
            }
        }
    }

    private List<Process> filterProcessesByProcessingType(ProcessingType processingType, List<Process> candidateProcesses) {
        Set<String> processBinding = new HashSet<>();
        LOG.debug("Filter process by processing type:");
        if (processingType == null) {
            LOG.debug("ProcessingType is null, returning all processes.");
            return candidateProcesses;
        }
        if (processingType == ProcessingType.PULL) {
            processBinding.add(ONE_WAY_PULL.getUri());
            return filterProcess(processingType, candidateProcesses, processBinding);
        }
        processBinding.add(ONE_WAY_PUSH.getUri());
        processBinding.add(TWO_WAY_PUSH_PUSH.getUri());
        return filterProcess(processingType, candidateProcesses, processBinding);

    }

    private List<Process> filterProcess(ProcessingType processingType, List<Process> candidateProcesses, Set<String> processBinding) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("ProcessingType is:[{}], returning processes with:[{}]", processingType, String.join(", ", processBinding));
        }
        return candidateProcesses.
                stream().
                peek(process -> LOG.debug("Checking binding for:[{}]", process.getName())).
                filter(process -> compareMepBinding(process.getMepBinding(), processBinding)).collect(Collectors.toList());
    }

    private boolean compareMepBinding(Binding mepBinding, Set<String> bindings) {
        boolean sameMepBinding = mepBinding != null && mepBinding.getValue() != null && bindings.contains(mepBinding.getValue());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Compare process binding:[{}] to bindings List:[{}]-> mep matches:[{}]", mepBinding, String.join(", ", bindings), sameMepBinding);
        }
        return sameMepBinding;
    }

    private String listProcessNames(List<Process> candidateProcesses) {
        return (candidateProcesses == null) ? null : candidateProcesses.stream().map(Process::getName).collect(Collectors.joining(","));
    }

    /**
     * From a list of {@link Process} filter the set of matching {@link LegConfiguration} - i.e; Legs which do not have any mismatch errors.
     * The list of {@link Process} should have been prefiltered to ensure match with input message metadata.
     *
     * @param matchingProcessesList
     * @param legFilterCriteria
     * @return Set of {@link LegConfiguration} having no mismatch errors.
     */
    protected Set<LegConfiguration> filterMatchingLegConfigurations(List<Process> matchingProcessesList, LegFilterCriteria legFilterCriteria) {
        Set<LegConfiguration> candidateLegs = new LinkedHashSet<>();
        matchingProcessesList.forEach(process -> candidateLegs.addAll(process.getLegs()));
        for (LegConfiguration candidateLeg : candidateLegs) {
            checkServiceMismatch(candidateLeg, legFilterCriteria);
            checkActionMismatch(candidateLeg, legFilterCriteria);
            checkMpcMismatch(candidateLeg, legFilterCriteria);
        }

        candidateLegs.removeAll(legFilterCriteria.listLegConfigurationsWitMismatchErrors());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Names of matched legs: [{}]", listLegNames(candidateLegs));
        }
        return candidateLegs;
    }

    private String listLegNames(Set<LegConfiguration> candidateLegs) {
        return (candidateLegs == null) ? null : candidateLegs.stream().map(LegConfiguration::getName).collect(Collectors.joining(","));
    }

    protected void checkServiceMismatch(LegConfiguration candidateLeg, LegFilterCriteria legFilterCriteria) {
        if (equalsIgnoreCase(candidateLeg.getService().getName(), legFilterCriteria.getService())) {
            LOG.debug("Service:[{}] matched for Leg:[{}]", legFilterCriteria.getService(), candidateLeg.getName());
            return;
        }
        legFilterCriteria.appendLegMismatchErrors(candidateLeg, "Service:[" + legFilterCriteria.getService() + DOES_NOT_MATCH_END_STRING);
    }

    protected void checkActionMismatch(LegConfiguration candidateLeg, LegFilterCriteria legFilterCriteria) {
        if (equalsIgnoreCase(candidateLeg.getAction().getName(), legFilterCriteria.getAction())) {
            LOG.debug("Action:[{}] matched for Leg:[{}]", legFilterCriteria.getAction(), candidateLeg.getName());
            return;
        }
        legFilterCriteria.appendLegMismatchErrors(candidateLeg, "Action:[" + legFilterCriteria.getAction() + DOES_NOT_MATCH_END_STRING);
    }

    protected boolean checkMpcMismatch(LegConfiguration candidateLeg, LegFilterCriteria legFilterCriteria) {
        LOG.debug("Checking for mpc mismatch - Mpc:[{}]  Leg Mpc: [{}]  Leg name:[{}]", legFilterCriteria.getMpc(),
                candidateLeg.getDefaultMpc().getQualifiedName(), candidateLeg.getName());

        boolean defaultMpcFromLegEnabled = domibusPropertyProvider.getBooleanProperty(DOMIBUS_PMODE_LEGCONFIGURATION_MPC_ENABLED);
        // When the mpc is missing, check if Domibus is configured to fill it in from the leg
        if (StringUtils.isBlank(legFilterCriteria.getMpc()) && defaultMpcFromLegEnabled) {
            LOG.debug("Empty mpc matched for Leg:[{}] due to property [{}] set to [{}] ", candidateLeg.getName(), DOMIBUS_PMODE_LEGCONFIGURATION_MPC_ENABLED, defaultMpcFromLegEnabled);
            return true;
        }

        boolean mpcValidationEnabled = domibusPropertyProvider.getBooleanProperty(DOMIBUS_PMODE_LEGCONFIGURATION_MPC_VALIDATION_ENABLED);
        if (!mpcValidationEnabled) {
            LOG.debug("Mpc validation disabled");
            return true;
        }

        if (equalsIgnoreCase(candidateLeg.getDefaultMpc().getQualifiedName(), legFilterCriteria.getMpc())) {
            LOG.debug("Mpc:[{}] matched for Leg:[{}]", legFilterCriteria.getMpc(), candidateLeg.getName());
            return true;
        }
        LOG.debug("Mpc:[{}] does not match for Leg:[{}]", legFilterCriteria.getMpc(), candidateLeg.getName());
        legFilterCriteria.appendLegMismatchErrors(candidateLeg, "Mpc:[" + legFilterCriteria.getMpc() + DOES_NOT_MATCH_END_STRING);
        return false;
    }


    protected boolean matchMepBinding(final String processMepBiding, final String messageProcessingType) {
        if (Objects.equals(processMepBiding, messageProcessingType)) {
            LOG.debug("Mep binding do  match:processMepBiding[{}]==messageProcessingType[{}]", processMepBiding, messageProcessingType);
            return true;
        }

        LOG.trace("Mep binding do not match:processMepBiding[{}]!=messageProcessingType[{}]", processMepBiding, messageProcessingType);
        return false;
    }

    protected boolean matchRole(final Role processRole, final Role role) {
        boolean rolesEnabled = domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
        if (!rolesEnabled) {
            LOG.debug("Roles validation disabled");
            return true;
        }

        LOG.debug("Role is [{}], process role is [{}] ", role, processRole);
        if (Objects.equals(role, processRole)) {
            LOG.debug("Roles match");
            return true;
        }

        LOG.debug("Roles do not match");
        return false;
    }

    protected void checkInitiatorRoleMismatch(Process process, LegFilterCriteria legFilterCriteria) {
        if (matchRole(process.getInitiatorRole(), legFilterCriteria.getInitiatorRole())) {
            LOG.debug("InitiatorRole:[{}] matched for Process:[{}]", legFilterCriteria.getInitiatorRole(), process.getName());
            return;
        }
        legFilterCriteria.appendProcessMismatchErrors(process, "InitiatorRole:[" + legFilterCriteria.getInitiatorRole() + DOES_NOT_MATCH_END_STRING);
    }

    protected void checkResponderRoleMismatch(Process process, LegFilterCriteria legFilterCriteria) {
        if (matchRole(process.getResponderRole(), legFilterCriteria.getResponderRole())) {
            LOG.debug("ResponderRole:[{}] matched for Process:[{}]", legFilterCriteria.getResponderRole(), process.getName());
            return;
        }
        legFilterCriteria.appendProcessMismatchErrors(process, "ResponderRole:[" + legFilterCriteria.getResponderRole() + DOES_NOT_MATCH_END_STRING);
    }

    @Override
    public String findActionName(final String action) throws EbMS3Exception {
        for (final Action action1 : this.getConfiguration().getBusinessProcesses().getActions()) {
            if (equalsIgnoreCase(action1.getValue(), action)) {
                return action1.getName();
            }
        }
        throw EbMS3ExceptionBuilder.getInstance()
                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0001)
                .message("No matching action found [" + action + "]")
                .build();
    }

    @Override
    public Mpc findMpc(final String mpcValue) throws EbMS3Exception {
        for (final Mpc mpc : this.getConfiguration().getMpcs()) {
            if (equalsIgnoreCase(mpc.getQualifiedName(), mpcValue)) {
                return mpc;
            }
        }
        throw EbMS3ExceptionBuilder.getInstance()
                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0001)
                .message("No matching mpc found [" + mpcValue + "]")
                .build();
    }

    @Override
    public String findServiceName(final ServiceEntity service) throws EbMS3Exception {
        if (service == null) {
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0001)
                    .message("Service is not found in the message")
                    .build();
        }
        String type = service.getType();
        String value = service.getValue();
        return findServiceName(value, type);
    }

    public String findServiceName(String service, String serviceType) throws EbMS3Exception {
        for (final Service pmodeService : this.getConfiguration().getBusinessProcesses().getServices()) {
            if (serviceMatching(service, serviceType, pmodeService))
                return pmodeService.getName();
        }
        throw EbMS3ExceptionBuilder.getInstance()
                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0001)
                .message("No matching service found for type [" + serviceType + "] and value [" + service + "]")
                .build();
    }

    private boolean serviceMatching(String serviceValue, String serviceType, Service pmodeService) {
        return equalsIgnoreCase(serviceValue, pmodeService.getValue())
                &&
                equalsIgnoreCase(trimToEmpty(serviceType), trimToEmpty(pmodeService.getServiceType()));
    }

    @Override
    public String findPartyName(final PartyId partyId) throws EbMS3Exception {
        String partyIdType = partyId.getType();
        validateURI(partyId.getValue(), partyId.getType());
        String partyIdValue = partyId.getValue();
        return findPartyName(partyIdValue, partyIdType);
    }

    @Override
    public String findPartyName(String partyId, String partyIdType) throws EbMS3Exception {
        for (final Party party : this.getConfiguration().getBusinessProcesses().getParties()) {
            if (identifiersMatching(partyId, partyIdType, party.getIdentifiers())) {
                return party.getName();
            }
        }
        throw EbMS3ExceptionBuilder.getInstance()
                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0003)
                .message("No matching party found for type [" + partyIdType + "] and value [" + partyId + "]")
                .build();
    }

    protected boolean identifiersMatching(String partyId, String partyIdType, List<Identifier> identifiers) {
        for (final Identifier identifier : identifiers) {
            if (identifierMatching(partyId, partyIdType, identifier)) return true;
        }
        return false;
    }

    private boolean identifierMatching(String partyId, String partyIdType, Identifier identifier) {
        String identifierPartyIdType = getIdentifierPartyIdType(identifier);
        LOG.debug("Find party with type:[{}] and identifier:[{}] by comparing with pmode id type:[{}] and pmode identifier:[{}]", partyIdType, partyId, identifierPartyIdType, identifier.getPartyId());
        if (isPartyIdTypeMatching(partyIdType, identifierPartyIdType) && equalsIgnoreCase(partyId, identifier.getPartyId())) {
            LOG.trace("Party with type:[{}] and identifier:[{}] matched", partyIdType, partyId);
            return true;
        }
        return false;
    }

    /**
     * PartyIdType can be null or empty string
     */
    protected boolean isPartyIdTypeMatching(String partyIdType, String identifierPartyIdType) {
        return (isEmpty(partyIdType) && isEmpty(identifierPartyIdType)) || equalsIgnoreCase(partyIdType, identifierPartyIdType);
    }

    protected String getIdentifierPartyIdType(Identifier identifier) {
        if (identifier.getPartyIdType() != null &&
                StringUtils.isNotEmpty(identifier.getPartyIdType().getValue())) {
            return identifier.getPartyIdType().getValue();
        }
        return null;
    }

    protected void validateURI(String partyId, String partyIdType) throws EbMS3Exception {
        if (partyIdType == null) {
            return;
        }
        try {
            URI.create(partyIdType);
        } catch (final IllegalArgumentException e) {
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0003)
                    .message("no matching party found | PartyId " + partyId + " is not a valid URI [CORE]")
                    .cause(e)
                    .build();
        }
    }

    @Override
    public String findAgreement(final AgreementRefEntity agreementRef) throws EbMS3Exception {
        if (agreementRef == null || agreementRef.getValue() == null || agreementRef.getValue().isEmpty()) {
            return OPTIONAL_AND_EMPTY; // AgreementRef is optional
        }

        for (final Agreement agreement : this.getConfiguration().getBusinessProcesses().getAgreements()) {
            if (agreementsMatch(agreementRef, agreement)) {
                return agreement.getName();
            }
        }
        throw EbMS3ExceptionBuilder.getInstance()
                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0001)
                .message("No matching agreement found for type [" + agreementRef.getType() + "] and value [" + agreementRef.getValue() + "]")
                .build();
    }

    private boolean agreementsMatch(AgreementRefEntity agreementRef, Agreement agreement) {
        return equalsIgnoreCase(agreement.getValue(), agreementRef.getValue())
                &&
                equalsIgnoreCase(trimToEmpty(agreement.getType()), trimToEmpty(agreementRef.getType()));
    }

    @Override
    public Party getPartyByIdentifier(String partyIdentifier) {
        for (final Party party : this.getConfiguration().getBusinessProcesses().getParties()) {
            final List<Identifier> identifiers = party.getIdentifiers();
            for (Identifier identifier : identifiers) {
                if (equalsIgnoreCase(identifier.getPartyId(), partyIdentifier)) {
                    return party;
                }
            }
        }
        return null;
    }

    @Override
    public Party getSenderParty(final String pModeKey) {
        final String partyKey = this.getSenderPartyNameFromPModeKey(pModeKey);
        for (final Party party : this.getConfiguration().getBusinessProcesses().getParties()) {
            if (equalsIgnoreCase(party.getName(), partyKey)) {
                return party;
            }
        }
        throw new ConfigurationException("No matching sender party found with name: " + partyKey);
    }

    @Override
    public Party getReceiverParty(final String pModeKey) {
        final String partyKey = this.getReceiverPartyNameFromPModeKey(pModeKey);
        final Party receiverPartyByPartyName = getPartyByName(partyKey);
        if (receiverPartyByPartyName != null) {
            return receiverPartyByPartyName;
        }
        throw new ConfigurationException("No matching receiver party found with name: " + partyKey);
    }

    @Override
    public synchronized void removeReceiverParty(String partyName) {
        final List<Process> allProcesses = findAllProcesses();
        for (Process process : allProcesses) {
            final Party removedParty = process.removeResponder(partyName);
            if (removedParty != null) {
                LOG.info("Removed party [{}] from process [{}] ->responderParties [{}]", partyName, process.getName());
            }
        }
    }

    @Override
    public synchronized Party removeParty(String partyName) {
        //remove from businessProcesses->parties
        final List<Party> partyList = this.getConfiguration().getBusinessProcesses().getParties();
        final Iterator<Party> partyIterator = partyList.iterator();
        while (partyIterator.hasNext()) {
            Party party = partyIterator.next();
            if (StringUtils.equalsIgnoreCase(partyName, party.getName())) {
                partyIterator.remove();
                LOG.info("Removed party [{}] from the party list: businessProcesses->parties", partyName);
                return party;
            }
        }
        return null;
    }

    /**
     * Search for the party in the Pmode parties list
     */
    @Override
    public Party getPartyByName(final String partyName) {
        LOG.debug("Finding party by name [{}]", partyName);
        for (final Party party : this.getConfiguration().getBusinessProcesses().getParties()) {
            if (equalsIgnoreCase(party.getName(), partyName)) {
                LOG.debug("Found party by name [{}]", partyName);
                return party;
            }
        }
        LOG.debug("Could not find party by name [{}]", partyName);
        return null;
    }

    @Override
    public Service getService(final String pModeKey) {
        final String serviceKey = this.getServiceNameFromPModeKey(pModeKey);
        for (final Service service : this.getConfiguration().getBusinessProcesses().getServices()) {
            if (equalsIgnoreCase(service.getName(), serviceKey)) {
                return service;
            }
        }
        throw new ConfigurationException("no matching service found with name: " + serviceKey);
    }

    @Override
    public Action getAction(final String pModeKey) {
        final String actionKey = this.getActionNameFromPModeKey(pModeKey);
        for (final Action action : this.getConfiguration().getBusinessProcesses().getActions()) {
            if (equalsIgnoreCase(action.getName(), actionKey)) {
                return action;
            }
        }
        throw new ConfigurationException("no matching action found with name: " + actionKey);
    }

    @Override
    public Agreement getAgreement(final String pModeKey) {
        final String agreementKey = this.getAgreementRefNameFromPModeKey(pModeKey);
        for (final Agreement agreement : this.getConfiguration().getBusinessProcesses().getAgreements()) {
            if (equalsIgnoreCase(agreement.getName(), agreementKey)) {
                return agreement;
            }
        }
        throw new ConfigurationException("no matching agreement found with name: " + agreementKey);
    }

    @Override
    public LegConfiguration getLegConfiguration(final String pModeKey) {
        final String legKey = this.getLegConfigurationNameFromPModeKey(pModeKey);
        for (final LegConfiguration legConfiguration : this.getConfiguration().getBusinessProcesses().getLegConfigurations()) {
            if (equalsIgnoreCase(legConfiguration.getName(), legKey)) {
                return legConfiguration;
            }
        }
        throw new ConfigurationException("no matching legConfiguration found with name: " + legKey);
    }

    @Override
    public boolean isMpcExistant(final String mpc) {
        for (final Mpc mpc1 : this.getConfiguration().getMpcs()) {
            if (equalsIgnoreCase(mpc1.getName(), mpc)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getRetentionDownloadedByMpcName(final String mpcName) {
        for (final Mpc mpc1 : this.getConfiguration().getMpcs()) {
            if (equalsIgnoreCase(mpc1.getName(), mpcName)) {
                return mpc1.getRetentionDownloaded();
            }
        }

        LOG.error("No MPC with name: [{}] found. Assuming message retention of 0 for downloaded messages.", mpcName);

        return 0;
    }

    @Override
    public int getRetentionDownloadedByMpcURI(final String mpcURI) {
        Optional<Mpc> mpc = findMpcByQualifiedName(mpcURI);
        if (mpc.isPresent()) {
            return mpc.get().getRetentionDownloaded();
        }

        LOG.error("No MPC with name: [{}] found. Assuming message retention of 0 for downloaded messages.", mpcURI);

        return 0;
    }

    @Override
    public int getRetentionUndownloadedByMpcName(final String mpcName) {
        for (final Mpc mpc1 : this.getConfiguration().getMpcs()) {
            if (equalsIgnoreCase(mpc1.getName(), mpcName)) {
                return mpc1.getRetentionUndownloaded();
            }
        }

        LOG.error("No MPC with name: [{}] found. Assuming message retention of -1 for undownloaded messages.", mpcName);

        return -1;
    }

    @Override
    public int getRetentionUndownloadedByMpcURI(final String mpcURI) {
        Optional<Mpc> mpc = findMpcByQualifiedName(mpcURI);
        if (mpc.isPresent()) {
            return mpc.get().getRetentionUndownloaded();
        }

        LOG.error("No MPC with name: [{}] found. Assuming message retention of -1 for undownloaded messages.", mpcURI);

        return -1;
    }

    @Override
    public int getRetentionSentByMpcURI(final String mpcURI) {
        Optional<Mpc> mpc = findMpcByQualifiedName(mpcURI);
        if (mpc.isPresent()) {
            return mpc.get().getRetentionSent();
        }

        LOG.error("No MPC with name: [{}] found. Assuming message retention of -1 for sent messages.", mpcURI);

        return -1;
    }

    public int getMetadataRetentionOffsetByMpcURI(String mpcURI) {
        Optional<Mpc> mpc = findMpcByQualifiedName(mpcURI);
        if (mpc.isPresent()) {
            return mpc.get().getMetadataRetentionOffset();
        }

        LOG.error("No MPC with name: [{}] found. Assuming message metadata retention offset of -1 for downloaded messages.", mpcURI);

        return -1;
    }

    private Optional<Mpc> findMpcByQualifiedName(String mpcURI) {
        Set<Mpc> mpcSet = getConfiguration().getMpcs();
        if (CollectionUtils.isNotEmpty(mpcSet)) {
            return mpcSet.stream()
                    .filter(mpc -> equalsIgnoreCase(mpc.getQualifiedName(), mpcURI))
                    .findFirst();
        }
        return Optional.empty();
    }

    @Override
    public boolean isDeleteMessageMetadataByMpcURI(final String mpcURI) {
        for (final Mpc mpc1 : this.getConfiguration().getMpcs()) {
            if (equalsIgnoreCase(mpc1.getQualifiedName(), mpcURI)) {
                LOG.debug("Found MPC with name [{}] and isDeleteMessageMetadata [{}]", mpc1.getName(), mpc1.isDeleteMessageMetadata());
                return mpc1.isDeleteMessageMetadata();
            }
        }
        LOG.error("No MPC with name: [{}] found. Assuming delete message metadata is false.", mpcURI);
        return false;
    }

    @Override
    public int getRetentionMaxBatchByMpcURI(final String mpcURI, final int maxValue) {
        for (final Mpc mpc1 : this.getConfiguration().getMpcs()) {
            if (equalsIgnoreCase(mpc1.getQualifiedName(), mpcURI)) {
                int maxBatch = mpc1.getMaxBatchDelete();
                LOG.debug("Found MPC with name [{}] and maxBatchDelete [{}]", mpc1.getName(), maxBatch);
                if (maxBatch <= 0 || maxBatch > maxValue) {
                    LOG.debug("Using default maxBatch value [{}]", maxValue);
                    return maxValue;
                }
                return maxBatch;
            }
        }

        LOG.error("No MPC with name: [{}] found. Using default value for message retention batch of [{}].", mpcURI, maxValue);

        return maxValue;
    }

    @Override
    public List<String> getMpcList() {
        final List<String> result = new ArrayList<>();
        for (final Mpc mpc : this.getConfiguration().getMpcs()) {
            result.add(mpc.getName());
        }
        return result;
    }

    @Override
    public List<String> getMpcURIList() {
        final List<String> result = new ArrayList<>();
        for (final Mpc mpc : this.getConfiguration().getMpcs()) {
            result.add(mpc.getQualifiedName());
        }
        return result;
    }

    @Override
    public Role getBusinessProcessRole(String roleValue) throws EbMS3Exception {
        for (Role role : this.getConfiguration().getBusinessProcesses().getRoles()) {
            if (equalsIgnoreCase(role.getValue(), roleValue)) {
                LOG.debug("Found role [{}]", roleValue);
                return role;
            }
        }
        boolean rolesEnabled = domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
        if (rolesEnabled) {
            LOG.businessError(DomibusMessageCode.BUS_PARTY_ROLE_NOT_FOUND, roleValue);
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0003)
                    .message("No matching role found with value: " + roleValue)
                    .build();
        } else {
            LOG.debug("No Role with value [{}] has been found", roleValue);
        }

        return null;
    }

    @Override
    public void refresh() {
        synchronized (configurationLock) {
            this.configuration = null;

            this.pullProcessByMpcCache.clear();
            this.pullProcessesByInitiatorCache.clear();

            if (CollectionUtils.isNotEmpty(pModeEventListeners)) {
                //we call the pmode event listeners
                pModeEventListeners.stream().forEach(pModeEventListener -> {
                    try {
                        pModeEventListener.onRefreshPMode();
                    } catch (Exception e) {
                        LOG.error("Error in PMode event listener [{}]: onRefreshPMode", pModeEventListener.getName(), e);
                    }
                });
            }

            //add here a listener when clearing the pmode cache
            this.init(); //reloads the config
        }
    }

    @Override
    public boolean hasLegWithSplittingConfiguration() {
        final BusinessProcesses businessProcesses = getConfiguration().getBusinessProcesses();
        final Set<eu.domibus.common.model.configuration.LegConfiguration> legConfigurations = businessProcesses.getLegConfigurations();
        if (org.apache.commons.collections4.CollectionUtils.isEmpty(legConfigurations)) {
            LOG.debug("No splitting configuration found: no legs found");
            return false;
        }
        final long legsCountHavingSplittingConfiguration = legConfigurations.stream()
                .filter(legConfiguration -> legConfiguration.getSplitting() != null)
                .count();
        return legsCountHavingSplittingConfiguration > 0;
    }

    @Override
    public boolean isConfigurationLoaded() {
        if (this.configuration != null) return true;
        return configurationDAO.configurationExists();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public List<ValidationIssue> updatePModes(final byte[] bytes, String description) throws XmlProcessingException, PModeValidationException {
        return super.updatePModes(bytes, description);
    }

    @Override
    public List<Process> findPullProcessesByMessageContext(final MessageExchangeConfiguration messageExchangeConfiguration) {
        List<Process> allProcesses = findAllProcesses();
        List<Process> result = new ArrayList<>();
        for (Process process : allProcesses) {
            boolean pullProcess = isPullProcess(process);
            if (!pullProcess) {
                continue;
            }

            boolean hasLeg = hasLeg(process, messageExchangeConfiguration.getLeg());
            if (!hasLeg) {
                continue;
            }
            boolean hasInitiatorParty = hasInitiatorParty(process, messageExchangeConfiguration.getReceiverParty());
            if (!hasInitiatorParty) {
                continue;
            }
            boolean hasResponderParty = hasResponderParty(process, messageExchangeConfiguration.getSenderParty());
            if (!hasResponderParty) {
                continue;
            }
            result.add(process);
        }
        return result;
    }

    protected boolean isPullProcess(Process process) {
        if (process.getMepBinding() == null) {
            return false;
        }
        return StringUtils.equals(MessageExchangePattern.ONE_WAY_PULL.getUri(), process.getMepBinding().getValue());
    }

    protected boolean hasLeg(Process process, String legName) {
        return process.getLegs().stream().anyMatch(leg -> StringUtils.equals(leg.getName(), legName));
    }

    protected boolean hasInitiatorParty(Process process, String partyName) {
        return matchInitiator(process, partyName);
    }

    protected boolean hasResponderParty(Process process, String partyName) {
        return matchResponder(process, partyName);
    }

    protected boolean matchesParty(Set<Party> parties, String partyName) {
        return parties.stream().anyMatch(initiatorParty -> StringUtils.equals(initiatorParty.getName(), partyName));
    }

    @Override
    public List<Process> findPullProcessesByInitiator(final Party party) {
        final List<Process> processes = pullProcessesByInitiatorCache.get(party);
        if (processes == null) {
            return Lists.newArrayList();
        }
        // return list with no duplicates
        return Lists.newArrayList(new HashSet<>(processes));
    }

    @Override
    public List<Process> findPullProcessByMpc(final String mpc) {
        List<Process> processes = pullProcessByMpcCache.get(mpc);
        if (processes == null) {
            return Lists.newArrayList();
        }
        return processes;
    }

    @Override
    public List<Process> findAllProcesses() {
        try {
            return Lists.newArrayList(getConfiguration().getBusinessProcesses().getProcesses());
        } catch (IllegalArgumentException e) {
            return Lists.newArrayList();
        }
    }

    @Override
    public List<Party> findAllParties() {
        try {
            return Lists.newArrayList(getConfiguration().getBusinessProcesses().getParties());
        } catch (IllegalArgumentException e) {
            return Lists.newArrayList();
        }
    }

    @Override
    public List<String> findPartiesByInitiatorServiceAndAction(String initiatingPartyId, final String service, final String action, final List<MessageExchangePattern> meps) {
        return findPartiesByParameters(initiatingPartyId, Process::getInitiatorParties, Process::getResponderParties, service, action, meps);
    }

    @Override
    public List<String> findPartiesByResponderServiceAndAction(String responderPartyId, final String service, final String action, final List<MessageExchangePattern> meps) {
        return findPartiesByParameters(responderPartyId, Process::getResponderParties, Process::getInitiatorParties, service, action, meps);
    }

    protected List<String> findPartiesByParameters(String partyId, Function<Process, Set<Party>> getProcessPartiesByRoleFn, Function<Process, Set<Party>> getCorrespondingPartiesFn,
                                                   String service, String action, List<MessageExchangePattern> meps) {
        List<String> result = new ArrayList<>();
        List<Process> processes = filterProcessesByMep(meps).stream()
                .filter(proc -> getProcessPartiesByRoleFn.apply(proc).stream().
                        anyMatch(initParty -> initParty.getIdentifiers().stream().
                                anyMatch(id -> StringUtils.equals(id.getPartyId(), partyId))))
                .collect(Collectors.toList());
        for (Process process : processes) {
            for (LegConfiguration legConfiguration : process.getLegs()) {
                LOG.trace("Find Party in leg [{}]", legConfiguration.getName());
                if (legConfiguration.getService() != null && equalsIgnoreCase(legConfiguration.getService().getValue(), service)
                        && legConfiguration.getAction() != null && equalsIgnoreCase(legConfiguration.getAction().getValue(), action)) {
                    result.addAll(getProcessPartiesId(process, getCorrespondingPartiesFn));
                }
            }
        }
        return result.stream().distinct().collect(Collectors.toList());
    }

    protected List<String> getProcessPartiesId(Process process, Function<Process, Set<Party>> getProcessPartiesByRoleFn) {
        List<String> result = new ArrayList<>();
        Comparator<Identifier> comp = Comparator.comparing(Identifier::getPartyId);
        for (Party party : getProcessPartiesByRoleFn.apply(process)) {
            List<String> partyIds = party.getIdentifiers().stream()
                    .sorted(comp)
                    .map(Identifier::getPartyId)
                    .collect(Collectors.toList());
            result.addAll(partyIds);
        }
        return result;
    }


    protected List<Process> filterProcessesByMep(final List<MessageExchangePattern> meps) {
        List<Process> processes = this.getConfiguration().getBusinessProcesses().getProcesses();
        processes = processes.stream().filter(process -> isMEPMatch(process, meps)).collect(Collectors.toList());

        return processes;
    }

    protected boolean isMEPMatch(Process process, final List<MessageExchangePattern> meps) {
        if (CollectionUtils.isEmpty(meps)) { // process can have any mep
            return true;
        }

        if (process == null || process.getMepBinding() == null  // invalid process
                || process.getMepBinding().getValue() == null) {
            return false;
        }

        for (MessageExchangePattern mep : meps) {
            if (mep.getUri().equals(process.getMepBinding().getValue())) {
                LOG.trace("Found match for mep [{}]", mep.getUri());
                return true;
            }
        }

        return false;
    }

    @Override
    public String getPartyIdType(String partyIdentifier) {
        for (Party party : getConfiguration().getBusinessProcesses().getParties()) {
            String partyIdTypeHandleParty = getPartyIdTypeHandleParty(party, partyIdentifier);
            if (partyIdTypeHandleParty != null) {
                return partyIdTypeHandleParty;
            }
        }
        return null;
    }

    private String getPartyIdTypeHandleParty(Party party, String partyIdentifier) {
        for (Identifier identifier : party.getIdentifiers()) {
            if (equalsIgnoreCase(identifier.getPartyId(), partyIdentifier)) {
                return identifier.getPartyIdType().getValue();
            }
        }
        return null;
    }

    @Override
    public String getServiceType(String serviceValue) {
        for (Service service : getConfiguration().getBusinessProcesses().getServices()) {
            if (equalsIgnoreCase(service.getValue(), serviceValue)) {
                return service.getServiceType();
            }
        }
        return null;
    }

    protected List<Process> getProcessFromService(String serviceValue) {
        List<Process> result = new ArrayList<>();
        for (Process process : getConfiguration().getBusinessProcesses().getProcesses()) {
            for (LegConfiguration legConfiguration : process.getLegs()) {
                if (equalsIgnoreCase(legConfiguration.getService().getValue(), serviceValue)) {
                    result.add(process);
                }
            }
        }
        return result;
    }

    /**
     * Returns the initiator/responder role value of the first process found having the specified service value.
     *
     * @param roleType     the type of the role (either "initiator" or "responder")
     * @param serviceValue the service value to match
     * @return the role value
     */
    @Override
    public String getRole(String roleType, String serviceValue) {
        for (Process found : getProcessFromService(serviceValue)) {
            String roleHandleProcess = getRoleHandleProcess(found, roleType);
            if (roleHandleProcess != null) {
                return roleHandleProcess;
            }
        }
        return null;
    }

    @Nullable
    private String getRoleHandleProcess(Process found, String roleType) {
        for (Process process : getConfiguration().getBusinessProcesses().getProcesses()) {
            if (equalsIgnoreCase(process.getName(), found.getName())) {
                if (roleType.equalsIgnoreCase("initiator")) {
                    return process.getInitiatorRole().getValue();
                }
                if (roleType.equalsIgnoreCase("responder")) {
                    return process.getResponderRole().getValue();
                }
            }
        }
        return null;
    }

    /**
     * Returns the agreement ref of the first process found having the specified service value.
     *
     * @param serviceValue the service value to match
     * @return the agreement value
     */
    @Override
    public Agreement getAgreementRef(String serviceValue) {
        for (Process found : getProcessFromService(serviceValue)) {
            Agreement agreement = getAgreementRefHandleProcess(found);
            if (agreement != null) {
                return agreement;
            }
        }
        return null;
    }

    @Override
    public LegConfigurationPerMpc getAllLegConfigurations() {
        Map<String, List<LegConfiguration>> result = new HashMap<>();
        for (LegConfiguration legConfiguration : getConfiguration().getBusinessProcesses().getLegConfigurations()) {
            List<LegConfiguration> legs = result.computeIfAbsent(legConfiguration.getDefaultMpc().getName(), k -> new ArrayList<>());
            legs.add(legConfiguration);
        }
        return new LegConfigurationPerMpc(result);
    }

    @Override
    public int getMaxRetryTimeout() {
        final LegConfigurationPerMpc legConfigurationPerMpc = getAllLegConfigurations();
        List<LegConfiguration> legConfigurations = new ArrayList<>();
        legConfigurationPerMpc.values().stream().forEach(legConfigurations::addAll);

        int maxRetry = legConfigurations.stream()
                .map(legConfiguration -> legConfiguration.getReceptionAwareness().getRetryTimeout())
                .max(Comparator.naturalOrder())
                .orElse(-1);

        LOG.debug("Got max retryTimeout [{}]", maxRetry);
        return maxRetry;
    }

    @Override
    public String findMpcUri(final String mpcName) throws EbMS3Exception {
        for (final Mpc mpc : this.getConfiguration().getMpcs()) {
            if (equalsIgnoreCase(mpc.getName(), mpcName)) {
                return mpc.getQualifiedName();
            }
        }
        throw EbMS3ExceptionBuilder.getInstance()
                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0001)
                .message("No matching mpc found [" + mpcName + "]")
                .build();
    }

    private Agreement getAgreementRefHandleProcess(Process found) {
        for (Process process : getConfiguration().getBusinessProcesses().getProcesses()) {
            if (equalsIgnoreCase(process.getName(), found.getName())) {
                Agreement agreement = process.getAgreement();
                if (agreement != null) {
                    return agreement;
                }
            }
        }
        return null;
    }
}
