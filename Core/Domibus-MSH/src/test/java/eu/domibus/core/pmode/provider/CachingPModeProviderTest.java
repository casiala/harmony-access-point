package eu.domibus.core.pmode.provider;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import eu.domibus.api.cache.DomibusLocalCacheService;
import eu.domibus.api.cluster.SignalService;
import eu.domibus.api.ebms3.Ebms3Constants;
import eu.domibus.api.ebms3.MessageExchangePattern;
import eu.domibus.api.jms.JMSManager;
import eu.domibus.api.model.*;
import eu.domibus.api.multitenancy.Domain;
import eu.domibus.api.multitenancy.DomainContextProvider;
import eu.domibus.api.multitenancy.DomainService;
import eu.domibus.api.pmode.PModeEventListener;
import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.api.util.xml.XMLUtil;
import eu.domibus.common.ErrorCode;
import eu.domibus.common.model.configuration.Process;
import eu.domibus.common.model.configuration.*;
import eu.domibus.core.ebms3.EbMS3Exception;
import eu.domibus.core.ebms3.EbMS3ExceptionBuilder;
import eu.domibus.core.exception.ConfigurationException;
import eu.domibus.core.message.MessageExchangeConfiguration;
import eu.domibus.core.message.pull.MpcService;
import eu.domibus.core.message.pull.PullProcessValidator;
import eu.domibus.core.pmode.*;
import eu.domibus.core.pmode.validation.PModeValidationService;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.test.common.PojoInstaciatorUtil;
import mockit.Expectations;
import mockit.FullVerifications;
import mockit.Injectable;
import mockit.Tested;
import mockit.integration.junit4.JMockit;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Topic;
import javax.persistence.EntityManager;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static eu.domibus.api.pmode.PModeConstants.PMODEKEY_SEPARATOR;
import static eu.domibus.api.property.DomibusPropertyMetadataManagerSPI.DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED;
import static org.junit.Assert.*;

/**
 * @author Arun Raj, Soumya Chandran
 * @since 3.3
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
@RunWith(JMockit.class)
public class CachingPModeProviderTest {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(CachingPModeProviderTest.class);

    private static final String VALID_PMODE_CONFIG_URI = "samplePModes/domibus-configuration-valid.xml";
    private static final String VALID_PMODE_TEST_CONFIG_URI = "samplePModes/domibus-configuration-valid-testservice.xml";
    private static final String PULL_PMODE_CONFIG_URI = "samplePModes/domibus-pmode-with-pull-processes.xml";
    private static final String DEFAULT_MPC_URI = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/defaultMpc";
    private static final String ANOTHER_MPC_URI = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/anotherMpc";
    private static final String DEFAULTMPC = "defaultMpc";
    private static final String ANOTHERMPC = "anotherMpc";
    private static final String NONEXISTANTMPC = "NonExistantMpc";

    // Values for findLeg tests
    final String senderParty = "blue_gw";
    final String receiverParty = "red_gw";
    final String agreement = "agreement1110";
    final String service = "noSecService";
    final String action = "noSecAction";
    final Role initiatorRole = new Role("defaultInitiatorRole", "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator");
    final Role responderRole = new Role("defaultResponderRole", "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/responder");
    final ProcessTypePartyExtractor pushProcessPartyExtractor = new PushProcessPartyExtractor(senderParty, receiverParty);

    @Injectable
    ConfigurationDAO configurationDAO;

    @Injectable
    ConfigurationRawDAO configurationRawDAO;

    @Injectable
    EntityManager entityManager;

    @Injectable
    JAXBContext jaxbContextConfig;

    @Injectable
    JMSManager jmsManager;

    @Injectable
    XMLUtil xmlUtil;

    @Injectable
    PModeValidationService pModeValidationService;

    @Injectable
    Configuration configuration;

    @Injectable
    DomainContextProvider domainContextProvider;

    @Injectable
    ProcessPartyExtractorProvider processPartyExtractorProvider;

    @Injectable
    Topic clusterCommandTopic;

    @Injectable
    Domain domain = DomainService.DEFAULT_DOMAIN;

    @Injectable
    SignalService signalService;

    @Injectable
    List<PModeEventListener> pModeEventListeners;

    @Injectable
    PullProcessValidator pullProcessValidator;

    @Injectable
    DomibusPropertyProvider domibusPropertyProvider;

    @Tested
    CachingPModeProvider cachingPModeProvider;

    @Injectable
    private MpcService mpcService;

    @Injectable
    UserMessage userMessage;
    @Injectable
    PartyId partyId1;

    @Injectable
    Mpc mpc;

    @Injectable
    ServiceEntity serviceEntity;
    @Injectable
    AgreementRefEntity agreementRef;

    @Injectable
    Process process;
    @Injectable
    ProcessTypePartyExtractor processTypePartyExtractor;
    @Injectable
    LegFilterCriteria legFilterCriteria;
    @Injectable
    Role role1;
    @Injectable
    LegConfiguration legConfiguration;

    @Injectable
    private DomibusLocalCacheService domibusLocalCacheService;

    public Configuration loadSamplePModeConfiguration(String samplePModeFileRelativeURI) throws JAXBException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        LOG.debug("Inside sample PMode configuration");
        InputStream xmlStream = getClass().getClassLoader().getResourceAsStream(samplePModeFileRelativeURI);
        JAXBContext jaxbContext = JAXBContext.newInstance(Configuration.class);

        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        configuration = (Configuration) unmarshaller.unmarshal(xmlStream);
        Method m = configuration.getClass().getDeclaredMethod("preparePersist");
        m.setAccessible(true);
        m.invoke(configuration);

        return configuration;
    }

    @Test
    public void testIsMpcExistant() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);

        new Expectations() {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        assertEquals(Boolean.TRUE, cachingPModeProvider.isMpcExistant(DEFAULTMPC.toUpperCase()));
    }

    @Test
    public void testIsMpcExistantNOK() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        assertEquals(Boolean.FALSE, cachingPModeProvider.isMpcExistant(NONEXISTANTMPC));
    }

    @Test
    public void testGetRetentionDownloadedByMpcName() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        assertEquals(3, cachingPModeProvider.getRetentionDownloadedByMpcName(ANOTHERMPC.toLowerCase()));
    }

    @Test
    public void testGetRetentionDownloadedByMpcNameNOK() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        assertEquals(0, cachingPModeProvider.getRetentionDownloadedByMpcName(NONEXISTANTMPC));
    }

    @Test
    public void testGetRetentionUnDownloadedByMpcName() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        assertEquals(5, cachingPModeProvider.getRetentionUndownloadedByMpcName(ANOTHERMPC.toUpperCase()));
    }

    @Test
    public void testGetRetentionUnDownloadedByMpcNameNOK() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        assertEquals(-1, cachingPModeProvider.getRetentionUndownloadedByMpcName(NONEXISTANTMPC));
    }

    @Test
    public void testGetMpcURIList() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        List<String> result = cachingPModeProvider.getMpcURIList();
        Assert.assertTrue("URI list should contain DefaultMpc URI", result.contains(DEFAULT_MPC_URI));
        Assert.assertTrue("URI list should contain AnotherMpc URI", result.contains(ANOTHER_MPC_URI));
    }

    @Test
    public void testFindPartyName() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getParties();
            result = configuration.getBusinessProcesses().getParties();
        }};

        try {
            cachingPModeProvider.findPartyName(partyId1);
            Assert.fail("Expected EbMS3Exception due to invalid URI character present!!");
        } catch (Exception e) {
            Assert.assertTrue("Expected EbMS3Exception", e instanceof EbMS3Exception);
        }
    }

    @Test
    public void testFindPartyName_EmptyPartyType() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException, EbMS3Exception {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        configuration.getBusinessProcesses().getParties().forEach(pmodeParty -> pmodeParty.getIdentifiers().forEach(pmodePartyIdentifier -> pmodePartyIdentifier.setPartyIdType(null)));
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getParties();
            result = configuration.getBusinessProcesses().getParties();

            partyId1.getValue();
            result = "urn:oasis:names:tc:ebcore:partyid-type:unregistered:domibus-blue";

            partyId1.getType();
            result = "";
        }};

        Assert.assertEquals("blue_gw", cachingPModeProvider.findPartyName(partyId1));
    }

    @Test
    public void testRefresh() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.configurationDAO.configurationExists();
            result = true;

            cachingPModeProvider.configurationDAO.readEager();
            result = configuration;
        }};

        cachingPModeProvider.refresh();
        assertEquals(configuration, cachingPModeProvider.getConfiguration());
    }

    @Test
    public void testGetBusinessProcessRoleFail() throws Exception {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getRoles();
            result = configuration.getBusinessProcesses().getRoles();
            domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
            result = true;
        }};

        try {
            cachingPModeProvider.getBusinessProcessRole("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/notMyInitiator");
            fail();
        } catch (EbMS3Exception cex) {
            assertEquals("No matching role found with value: http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/notMyInitiator", cex.getMessage());
        }
    }

    @Test
    public void testGetBusinessProcessRoleOk() throws Exception {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getRoles();
            result = configuration.getBusinessProcesses().getRoles();
        }};
        //TODO Use Mocking instead of real Instances
        Role expectedRole = new Role();
        expectedRole.setName("defaultInitiatorRole");
        expectedRole.setValue("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator");

        Role role = cachingPModeProvider.getBusinessProcessRole("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator");
        assertEquals(expectedRole, role);
    }

    @Test
    public void testGetBusinessProcessRoleNOk() throws Exception {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getRoles();
            result = configuration.getBusinessProcesses().getRoles();
        }};

        Role role = cachingPModeProvider.getBusinessProcessRole("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator123");
        Assert.assertNull(role);
    }

    @Test
    public void testRetrievePullProcessBasedOnInitiator() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(PULL_PMODE_CONFIG_URI);
        final Set<Party> parties = new HashSet<>(configuration.getBusinessProcesses().getParties());
        final Party red_gw = getPartyByName(parties, "red_gw");
        final Party blue_gw = getPartyByName(parties, "blue_gw");

        new Expectations() {{
            configurationDAO.configurationExists();
            result = true;
            configurationDAO.readEager();
            result = configuration;
        }};
        cachingPModeProvider.init();

        List<Process> pullProcessesByInitiator = cachingPModeProvider.findPullProcessesByInitiator(red_gw);
        assertEquals(5, pullProcessesByInitiator.size());

        pullProcessesByInitiator = cachingPModeProvider.findPullProcessesByInitiator(blue_gw);
        assertEquals(0, pullProcessesByInitiator.size());
    }

    @Test
    public void testRetrievePullProcessBasedOnPartyNotInInitiator() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(PULL_PMODE_CONFIG_URI);
        final Set<Party> parties = new HashSet<>(configuration.getBusinessProcesses().getParties());
        final Party white_gw = getPartyByName(parties, "white_gw");
        new Expectations() {{
            configurationDAO.configurationExists();
            result = true;
            configurationDAO.readEager();
            result = configuration;
        }};
        cachingPModeProvider.init();
        List<Process> pullProcessesByInitiator = cachingPModeProvider.findPullProcessesByInitiator(white_gw);
        Assert.assertNotNull(pullProcessesByInitiator);
    }

    @Test
    public void testRetrievePullProcessBasedOnMpc() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(PULL_PMODE_CONFIG_URI);
        final String mpcName = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/defaultMPCOne";
        final String emptyMpc = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/defaultMPCTwo";
        new Expectations() {{
            configurationDAO.configurationExists();
            result = true;
            configurationDAO.readEager();
            result = configuration;
        }};
        cachingPModeProvider.init();
        List<Process> pullProcessesByMpc = cachingPModeProvider.findPullProcessByMpc(mpcName);
        assertEquals(1, pullProcessesByMpc.size());
        assertEquals("tc13Process", pullProcessesByMpc.iterator().next().getName());
        pullProcessesByMpc = cachingPModeProvider.findPullProcessByMpc(emptyMpc);
        assertEquals(1, pullProcessesByMpc.size());
        assertEquals("tc14Process", pullProcessesByMpc.iterator().next().getName());


    }

    @Test
    public void testFindPartyIdByServiceAndAction() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        // Given
        String initiatingPartyId = "domibus-blue";
        List<String> expectedList = new ArrayList<>();
        expectedList.add("domibus-red");
        expectedList.add("domibus-blue");
        expectedList.add("urn:oasis:names:tc:ebcore:partyid-type:unregistered:holodeck-b2b");
        configuration = loadSamplePModeConfiguration(VALID_PMODE_TEST_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getProcesses();
            result = configuration.getBusinessProcesses().getProcesses();
        }};

        // When

        List<String> partyIdByServiceAndAction = cachingPModeProvider.findPartiesByInitiatorServiceAndAction(initiatingPartyId, Ebms3Constants.TEST_SERVICE, Ebms3Constants.TEST_ACTION, null);

        // Then
        assertEquals(expectedList.size(), partyIdByServiceAndAction.size());
        assertTrue(CollectionUtils.containsAll(expectedList, partyIdByServiceAndAction));
    }

    @Test
    public void testFindPushToPartyIdByServiceAndAction() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        // Given
        String initiatingPartyId = "domibus-blue";
        List<String> expectedList = new ArrayList<>();
        expectedList.add("domibus-red");
        expectedList.add("domibus-blue");
        configuration = loadSamplePModeConfiguration(VALID_PMODE_TEST_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getProcesses();
            result = configuration.getBusinessProcesses().getProcesses();
        }};

        List<MessageExchangePattern> meps = new ArrayList<>();
        meps.add(MessageExchangePattern.ONE_WAY_PUSH);

        // When
        List<String> partyIdByServiceAndAction = cachingPModeProvider.findPartiesByInitiatorServiceAndAction(initiatingPartyId, Ebms3Constants.TEST_SERVICE, Ebms3Constants.TEST_ACTION, meps);

        // Then
        assertEquals(expectedList.size(), partyIdByServiceAndAction.size());
        assertTrue(CollectionUtils.containsAll(expectedList, partyIdByServiceAndAction));
    }

    private Party getPartyByName(Set<Party> parties, final String partyName) {
        final Collection<Party> filter = Collections2.filter(parties, party -> partyName.equals(party != null ? party.getName() : null));
        return Lists.newArrayList(filter).get(0);
    }

    @Test
    public void testGetPartyIdType() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        // Given
        String partyIdentifier = "urn:oasis:names:tc:ebcore:partyid-type:unregistered:domibus-de";
        configuration = loadSamplePModeConfiguration(VALID_PMODE_TEST_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getParties();
            result = configuration.getBusinessProcesses().getParties();
        }};

        // When
        String partyIdType = cachingPModeProvider.getPartyIdType(partyIdentifier);

        // Then
        Assert.assertTrue(StringUtils.isEmpty(partyIdType));
    }

    @Test
    public void testGetPartyIdTypeNull() {
        // Given
        String partyIdentifier = "empty";

        // When
        String partyIdType = cachingPModeProvider.getPartyIdType(partyIdentifier);

        // Then
        Assert.assertNull(partyIdType);
    }

    @Test
    public void testGetServiceType() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        //Given
        String serviceValue = "bdx:noprocess";
        configuration = loadSamplePModeConfiguration(VALID_PMODE_TEST_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getServices();
            result = configuration.getBusinessProcesses().getServices();
        }};

        // When
        String serviceType = cachingPModeProvider.getServiceType(serviceValue);

        // Then
        Assert.assertTrue(Sets.newHashSet("tc1", "tc2", "tc3").contains(serviceType));
    }

    @Test
    public void testGetServiceTypeNull() {
        // Given
        String serviceValue = "serviceValue";

        // When
        String serviceType = cachingPModeProvider.getServiceType(serviceValue);

        // Then
        Assert.assertNull(serviceType);
    }

    @Test
    public void testGetProcessFromService() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        // Given
        configuration = loadSamplePModeConfiguration(VALID_PMODE_TEST_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getProcesses();
            result = configuration.getBusinessProcesses().getProcesses();
        }};

        // When
        List<Process> processFromService = cachingPModeProvider.getProcessFromService(Ebms3Constants.TEST_SERVICE);

        // Then
        assertEquals(3, processFromService.size());
        assertEquals("testService", processFromService.get(1).getName());
    }

    @Test
    public void testGetProcessFromServiceNull() {
        // Given
        String serviceValue = "serviceValue";

        // When
        List<Process> processFromService = cachingPModeProvider.getProcessFromService(serviceValue);

        // Then
        Assert.assertTrue(processFromService.isEmpty());
    }

    @Test
    public void testGetRoleInitiator() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        // Given
        configuration = loadSamplePModeConfiguration(VALID_PMODE_TEST_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getProcesses();
            result = configuration.getBusinessProcesses().getProcesses();

            cachingPModeProvider.getProcessFromService(Ebms3Constants.TEST_SERVICE);
            result = getTestProcess(configuration.getBusinessProcesses().getProcesses());

        }};

        // When
        String initiator = cachingPModeProvider.getRole("INITIATOR", Ebms3Constants.TEST_SERVICE);

        // Then
        assertEquals("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator", initiator);
    }

    @Test
    public void testGetRoleResponder() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        // Given
        configuration = loadSamplePModeConfiguration(VALID_PMODE_TEST_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getProcesses();
            result = configuration.getBusinessProcesses().getProcesses();

            cachingPModeProvider.getProcessFromService(Ebms3Constants.TEST_SERVICE);
            result = getTestProcess(configuration.getBusinessProcesses().getProcesses());

        }};

        // When
        String responder = cachingPModeProvider.getRole("RESPONDER", Ebms3Constants.TEST_SERVICE);

        // Then
        assertEquals("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/responder", responder);
    }

    @Test
    public void testGetRoleNull() {
        // Given
        String serviceValue = "serviceValue";

        // When
        String role = cachingPModeProvider.getRole("", serviceValue);

        // Then
        Assert.assertNull(role);
    }

    @Test
    public void testAgreementRef() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        // Given
        configuration = loadSamplePModeConfiguration(VALID_PMODE_TEST_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getProcesses();
            result = configuration.getBusinessProcesses().getProcesses();

            cachingPModeProvider.getProcessFromService(Ebms3Constants.TEST_SERVICE);
            result = getTestProcess(configuration.getBusinessProcesses().getProcesses());

        }};

        // When
        Agreement agreementRef = cachingPModeProvider.getAgreementRef(Ebms3Constants.TEST_SERVICE);

        // Then
        assertEquals("TestServiceAgreement", agreementRef.getValue());
        assertEquals("TestServiceAgreementType", agreementRef.getType());
    }

    @Test
    public void testAgreementRefNull() {
        // Given
        String serviceValue = "serviceValue";

        // When
        Agreement agreementRef = cachingPModeProvider.getAgreementRef(serviceValue);

        // Then
        Assert.assertNull(agreementRef);
    }

    @Test
    public void testFindPullLegExeption() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        // Given
        configuration = loadSamplePModeConfiguration(PULL_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getProcesses();
            result = configuration.getBusinessProcesses().getProcesses();
        }};

        try {
            cachingPModeProvider.findPullLegName("agreementName", "senderParty", "receiverParty", "service", "action", "mpc", new Role("rn", "rv"), new Role("rn", "rv"));
            fail();
        } catch (EbMS3Exception exc) {
            assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0001, exc.getErrorCode());
        }
    }

    @Test
    public void testFindPullLeg() throws EbMS3Exception, InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        // Given
        configuration = loadSamplePModeConfiguration(PULL_PMODE_CONFIG_URI);
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getProcesses();
            result = configuration.getBusinessProcesses().getProcesses();
            cachingPModeProvider.matchAgreement((Process) any, anyString);
            result = true;
            cachingPModeProvider.matchInitiator((Process) any, anyString);
            result = true;
            cachingPModeProvider.matchResponder((Process) any, anyString);
            result = true;
            cachingPModeProvider.candidateMatches(withAny(new LegConfiguration()), anyString, anyString, anyString);
            result = true;
        }};

        String legName = cachingPModeProvider.findPullLegName("", "somesender", "somereceiver", "someservice", "someaction", "somempc", new Role("rn", "rv"), new Role("rn", "rv"));
        Assert.assertNotNull(legName);
    }

    @Test
    public void testFindPullLegNoCandidate() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        // Given
        configuration = loadSamplePModeConfiguration(PULL_PMODE_CONFIG_URI);
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getProcesses();
            result = configuration.getBusinessProcesses().getProcesses();
            cachingPModeProvider.matchAgreement((Process) any, anyString);
            result = false;
        }};

        try {
            cachingPModeProvider.findPullLegName("", "somesender", "somereceiver", "someservice", "someaction", "somempc", new Role("rn", "rv"), new Role("rn", "rv"));
            fail();
        } catch (EbMS3Exception exc) {
            assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0001, exc.getErrorCode());
        }
    }

    @Test
    public void testFindPullLegNoMatchingCandidate() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        // Given
        configuration = loadSamplePModeConfiguration(PULL_PMODE_CONFIG_URI);
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getProcesses();
            result = configuration.getBusinessProcesses().getProcesses();
            cachingPModeProvider.matchAgreement((Process) any, anyString);
            result = true;
            cachingPModeProvider.matchInitiator((Process) any, anyString);
            result = true;
            cachingPModeProvider.matchResponder((Process) any, anyString);
            result = true;
            cachingPModeProvider.candidateMatches(withAny(new LegConfiguration()), anyString, anyString, anyString);
            result = false;
        }};

        try {
            cachingPModeProvider.findPullLegName("", "somesender", "somereceiver", "someservice", "someaction", "somempc", new Role("rn", "rv"), new Role("rn", "rv"));
            fail();
        } catch (EbMS3Exception exc) {
            assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0001, exc.getErrorCode());
        }
    }

    @Test
    public void testGetGatewayParty() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);

        new Expectations() {{
            cachingPModeProvider.getConfiguration().getParty();
            result = configuration.getParty();
        }};

        assertEquals(configuration.getParty(), cachingPModeProvider.getGatewayParty());
    }

    @Test
    public void testMatchAgreement() {
        Process process = PojoInstaciatorUtil.instanciate(Process.class, "mep[name:twoway]", "mepBinding[name:push]", "agreement[name:a1,value:v1,type:t1]");
        Assert.assertTrue(cachingPModeProvider.matchAgreement(process, "a1"));
    }

    @Test
    public void testMatchRole() {
        new Expectations() {{
            domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
            result = true;
        }};
        Process process = PojoInstaciatorUtil.instanciate(Process.class, "mep[name:oneway]", "mepBinding[name:push]", "agreement[name:a1,value:v1,type:t1]", "initiatorRole[name:myInitiatorRole,value:iRole]", "responderRole[name:myResponderRole,value:rRole]");
        Assert.assertFalse(cachingPModeProvider.matchRole(process.getResponderRole(), new Role("myResponderRole", "notrRole")));
        Assert.assertFalse(cachingPModeProvider.matchRole(process.getInitiatorRole(), new Role("myInitiatorRole", "notiRole")));
        Assert.assertTrue(cachingPModeProvider.matchRole(process.getResponderRole(), new Role("myResponderRole", "rRole")));
    }

    @Test
    public void testMatchInitiator() {
        Process process = PojoInstaciatorUtil.instanciate(Process.class, "initiatorParties{[name:initiator1];[name:initiator2]}");
        ProcessTypePartyExtractor processTypePartyExtractor = new PullProcessPartyExtractor(null, "initiator1");
        Assert.assertTrue(cachingPModeProvider.matchInitiator(process, processTypePartyExtractor.getSenderParty()));
    }

    @Test
    public void testMatchInitiatorNot() {
        Process process = PojoInstaciatorUtil.instanciate(Process.class, "initiatorParties{[name:initiator1];[name:initiator2]}");
        ProcessTypePartyExtractor processTypePartyExtractor = new PullProcessPartyExtractor(null, "nobodywho");
        Assert.assertFalse(cachingPModeProvider.matchInitiator(process, processTypePartyExtractor.getSenderParty()));
    }

    @Test
    public void testMatchInitiatorAllowEmpty() {
        new Expectations() {{
            pullProcessValidator.allowDynamicInitiatorInPullProcess();
            result = true;
        }};
        Process process = PojoInstaciatorUtil.instanciate(Process.class, "mep[name:twoway]");
        ProcessTypePartyExtractor processTypePartyExtractor = new PullProcessPartyExtractor(null, "nobodywho");
        Assert.assertTrue(cachingPModeProvider.matchInitiator(process, processTypePartyExtractor.getSenderParty()));
    }

    @Test
    public void testMatchInitiatorNotAllowEmpty() {
        new Expectations() {{
            pullProcessValidator.allowDynamicInitiatorInPullProcess();
            result = false;
        }};
        Process process = PojoInstaciatorUtil.instanciate(Process.class, "mep[name:twoway]");
        ProcessTypePartyExtractor processTypePartyExtractor = new PullProcessPartyExtractor(null, "nobodywho");
        Assert.assertFalse(cachingPModeProvider.matchInitiator(process, processTypePartyExtractor.getSenderParty()));
    }

    @Test
    public void testMatchResponder() {
        Process process = PojoInstaciatorUtil.instanciate(Process.class, "responderParties{[name:responder1];[name:responder2]}");
        ProcessTypePartyExtractor processTypePartyExtractor = new PullProcessPartyExtractor("responder1", null);
        Assert.assertTrue(cachingPModeProvider.matchResponder(process, processTypePartyExtractor.getReceiverParty()));
    }

    @Test
    public void testMatchResponderNot() {
        Process process = PojoInstaciatorUtil.instanciate(Process.class, "responderParties{[name:responder1];[name:responder2]}");
        ProcessTypePartyExtractor processTypePartyExtractor = new PullProcessPartyExtractor("nobody", null);
        Assert.assertFalse(cachingPModeProvider.matchResponder(process, processTypePartyExtractor.getReceiverParty()));
    }

    @Test
    public void testMatchResponderEmpty() {
        Process process = PojoInstaciatorUtil.instanciate(Process.class, "mep[name:twoway]");
        ProcessTypePartyExtractor processTypePartyExtractor = new PullProcessPartyExtractor("nobody", null);
        Assert.assertFalse(cachingPModeProvider.matchResponder(process, processTypePartyExtractor.getReceiverParty()));
    }

    @Test
    public void testCandidateMatches() {
        LegConfiguration candidate = PojoInstaciatorUtil.instanciate(LegConfiguration.class, "service[name:s1]", "action[name:a1]", "defaultMpc[qualifiedName:mpc_qn]");
        Assert.assertTrue(cachingPModeProvider.candidateMatches(candidate, "s1", "a1", "mpc_qn"));
    }

    @Test
    public void testCandidateNotMatches() {
        LegConfiguration candidate = PojoInstaciatorUtil.instanciate(LegConfiguration.class, "service[name:s1]", "action[name:a1]", "defaultMpc[qualifiedName:mpc_qn]");
        Assert.assertFalse(cachingPModeProvider.candidateMatches(candidate, "s2", "a2", "mpc_qn"));
    }

    @Test
    public void testFindMpcUri() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, EbMS3Exception {
        String expectedMpc = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/defaultMpc";
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        String mpcURI = cachingPModeProvider.findMpcUri("defaultMpc");

        assertEquals(expectedMpc, mpcURI);
    }

    @Test(expected = EbMS3Exception.class)
    public void testFindMpcUriException() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, EbMS3Exception {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        cachingPModeProvider.findMpcUri("no_mpc");
    }

    private Process getTestProcess(Collection<Process> processes) {
        for (Process process : processes) {
            if (process.getName().equals("testService")) {
                return process;
            }
        }
        return null;
    }

    @Test
    public void testgetMpcList() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        cachingPModeProvider.getMpcList();
        Assert.assertNotNull(cachingPModeProvider.getMpcList());
    }

    @Test
    public void testFindLegNameOK() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException, EbMS3Exception {
        final String expectedLegName = "pushNoSecnoSecAction";
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);

        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses();
            result = configuration.getBusinessProcesses();

            domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
            result = true;

            processPartyExtractorProvider.getProcessTypePartyExtractor(MessageExchangePattern.ONE_WAY_PUSH.getUri(), senderParty, receiverParty);
            result = pushProcessPartyExtractor;

        }};
        String legName = cachingPModeProvider.findLegName(agreement, senderParty, receiverParty, service, action, initiatorRole, responderRole, null, null);
        assertEquals(expectedLegName, legName);
    }

    @Test
    public void testFindLegNameMissingInitiatorRole() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        final Role notMyInitiatorRole = new Role("defaultInitiatorRole", "notMyInitiator");
        final String expectedErrorMsgStart = "None of the Processes matched with message metadata. Process mismatch details:";
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);

        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses();
            result = configuration.getBusinessProcesses();

            domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
            result = true;

            processPartyExtractorProvider.getProcessTypePartyExtractor(MessageExchangePattern.ONE_WAY_PUSH.getUri(), senderParty, receiverParty);
            result = pushProcessPartyExtractor;

            domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
            result = true;
        }};

        try {
            cachingPModeProvider.findLegName(agreement, senderParty, receiverParty, service, action, notMyInitiatorRole, responderRole, null, null);
            fail("Expected EbMS3Exception to be thrown with InitiatorRole mismatch details!");
        } catch (EbMS3Exception ex) {
            assertTrue("Expected error message to begin with:" + expectedErrorMsgStart, StringUtils.startsWith(ex.getErrorDetail(), expectedErrorMsgStart));
            assertTrue("Expected error message to contain Role details.", StringUtils.contains(ex.getErrorDetail(), "InitiatorRole:[" + notMyInitiatorRole + "] does not match"));
        }
    }

    @Test
    public void testFindLegNameMismatchResponderRole() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        final Role notMyResponderRole = new Role("defaultResponderRole", "notMyResponder");
        final String expectedErrorMsgStart = "None of the Processes matched with message metadata. Process mismatch details:";
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);

        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses();
            result = configuration.getBusinessProcesses();

            domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
            result = true;

            processPartyExtractorProvider.getProcessTypePartyExtractor(MessageExchangePattern.ONE_WAY_PUSH.getUri(), senderParty, receiverParty);
            result = pushProcessPartyExtractor;

            domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
            result = true;
        }};
        try {
            cachingPModeProvider.findLegName(agreement, senderParty, receiverParty, service, action, initiatorRole, notMyResponderRole, null, null);
            fail("Expected EbMS3Exception to be thrown with ResponderRole mismatch details!");
        } catch (EbMS3Exception ex) {
            assertTrue("Expected error message to begin with:" + expectedErrorMsgStart, StringUtils.startsWith(ex.getErrorDetail(), expectedErrorMsgStart));
            assertTrue("Expected error message to contain Role details.", StringUtils.contains(ex.getErrorDetail(), "ResponderRole:[" + notMyResponderRole + "] does not match"));
        }
    }

    @Test
    public void testFindLegNameMismatchInitiatorResponder() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        final String expectedErrorMsgStart = "None of the Processes matched with message metadata. Process mismatch details:";
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        String incorrectSender = "BadSender";
        String incorrectReceiver = "BadReceiver";

        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses();
            result = configuration.getBusinessProcesses();

            domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
            result = true;

            processPartyExtractorProvider.getProcessTypePartyExtractor(MessageExchangePattern.ONE_WAY_PUSH.getUri(), incorrectSender, incorrectReceiver);
            result = new PushProcessPartyExtractor(incorrectSender, incorrectReceiver);

            domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
            result = true;
        }};
        try {
            cachingPModeProvider.findLegName(agreement, incorrectSender, incorrectReceiver, service, action, initiatorRole, responderRole, null, null);
            fail("Expected EbMS3Exception to be thrown with Initiator and Responder mismatch details!");
        } catch (EbMS3Exception ex) {
            assertTrue("Expected error message to begin with:" + expectedErrorMsgStart, StringUtils.startsWith(ex.getErrorDetail(), expectedErrorMsgStart));
            assertTrue("Expected error message to contain Sender details.", StringUtils.contains(ex.getErrorDetail(), "Initiator:[" + incorrectSender + "] does not match"));
            assertTrue("Expected error message to contain Receiver details.", StringUtils.contains(ex.getErrorDetail(), "Responder:[" + incorrectReceiver + "] does not match"));
        }
    }

    @Test
    public void testFindLegNameProcessMismatchCombinationErrors() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        final String expectedErrorMsgStart = "None of the Processes matched with message metadata. Process mismatch details:";
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        String incorrectAgreement = "IncorrectAgreement";
        String incorrectSender = "BadSender";
        String incorrectReceiver = "BadReceiver";
        Role incorrectInitiatorRole = new Role("defaultInitiatorRole", "notMyInitiator");
        Role incorrectResponderRole = new Role("defaultResponderRole", "notMyResponder");


        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses();
            result = configuration.getBusinessProcesses();

            domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
            result = true;

            processPartyExtractorProvider.getProcessTypePartyExtractor(MessageExchangePattern.ONE_WAY_PUSH.getUri(), incorrectSender, incorrectReceiver);
            result = new PushProcessPartyExtractor(incorrectSender, incorrectReceiver);

            domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
            result = true;
        }};
        try {
            cachingPModeProvider.findLegName(incorrectAgreement, incorrectSender, incorrectReceiver, service, action, incorrectInitiatorRole, incorrectResponderRole, null, null);
            fail("Expected EbMS3Exception to be thrown with all mismatch details!");
        } catch (EbMS3Exception ex) {
            assertTrue("Expected error message to begin with:" + expectedErrorMsgStart, StringUtils.startsWith(ex.getErrorDetail(), expectedErrorMsgStart));
            assertTrue("Expected error message to contain Agreement details.", StringUtils.contains(ex.getErrorDetail(), "Agreement:[" + incorrectAgreement + "] does not match"));
            assertTrue("Expected error message to contain Initiator details.", StringUtils.contains(ex.getErrorDetail(), "Initiator:[" + incorrectSender + "] does not match"));
            assertTrue("Expected error message to contain Responder details.", StringUtils.contains(ex.getErrorDetail(), "Responder:[" + incorrectReceiver + "] does not match"));
            //Validations on InitiatorRoole and ResponderRole cannot be enforced due to 255 characters limit in EbMS3Exception:getErrorDetail()
        }
    }

    @Test
    public void testFindLegNameEmptyLegCandidate() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        String expectedErrorMsgStart = "No matching Legs found among matched Processes:";
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        configuration.getBusinessProcesses().getLegConfigurations().clear();
        configuration.getBusinessProcesses().getProcesses().forEach(process1 -> process1.getLegs().clear());

        new Expectations() {
            {
                cachingPModeProvider.getConfiguration().getBusinessProcesses();
                result = configuration.getBusinessProcesses();

                domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
                result = true;

                processPartyExtractorProvider.getProcessTypePartyExtractor(MessageExchangePattern.ONE_WAY_PUSH.getUri(), senderParty, receiverParty);
                result = pushProcessPartyExtractor;
            }
        };

        try {
            cachingPModeProvider.findLegName(agreement, senderParty, receiverParty, service, action, initiatorRole, responderRole, null, null);
            fail("Expected EbMS3Exception to be thrown with Leg mismatch details!");
        } catch (EbMS3Exception ex) {
            assertTrue("Expected error message to begin with:" + expectedErrorMsgStart, StringUtils.startsWith(ex.getErrorDetail(), expectedErrorMsgStart));
        }
    }

    @Test
    public void testFindLegNameServiceActionMismatch() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        final String service = "MismatchService";
        final String action = "IncorrectAction";
        final String expectedErrorMsgStart = "No matching Legs found among matched Processes:";

        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses();
            result = configuration.getBusinessProcesses();

            domibusPropertyProvider.getBooleanProperty(DOMIBUS_PARTYINFO_ROLES_VALIDATION_ENABLED);
            result = true;

            processPartyExtractorProvider.getProcessTypePartyExtractor(MessageExchangePattern.ONE_WAY_PUSH.getUri(), senderParty, receiverParty);
            result = pushProcessPartyExtractor;

        }};
        try {
            cachingPModeProvider.findLegName(agreement, senderParty, receiverParty, service, action, initiatorRole, responderRole, null, null);
            fail("Expected EbMS3Exception to be thrown with Service and Action mismatch details!");
        } catch (EbMS3Exception ex) {
            assertTrue("Expected error message to start with:" + expectedErrorMsgStart, StringUtils.startsWith(ex.getErrorDetail(), expectedErrorMsgStart));
            assertTrue("Expected error message to contain Service details.", StringUtils.contains(ex.getErrorDetail(), "Service:[" + service + "] does not match"));
            assertTrue("Expected error message to contain Action details.", StringUtils.contains(ex.getErrorDetail(), "Action:[" + action + "] does not match"));
        }
    }

    @Test
    public void testFindActionName() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);

        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getActions();
            result = configuration.getBusinessProcesses().getActions();
        }};
        try {
            cachingPModeProvider.findActionName("action");
        } catch (EbMS3Exception ex) {
            assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0001, ex.getErrorCode());
        }
    }

    @Test
    public void testFindMpc() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};
        try {
            cachingPModeProvider.findMpc("no_mpc");
        } catch (EbMS3Exception ex) {
            assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0001, ex.getErrorCode());
        }
    }

    @Test
    public void testFindServiceName() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getServices();
            result = configuration.getBusinessProcesses().getServices();
        }};
        try {
            cachingPModeProvider.findServiceName(serviceEntity);
        } catch (EbMS3Exception ex) {
            assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0001, ex.getErrorCode());
        }
    }

    @Test
    public void testFindAgreement() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations(cachingPModeProvider) {{
            agreementRef.getValue();
            result = "test";
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getAgreements();
            result = configuration.getBusinessProcesses().getAgreements();
        }};
        try {
            cachingPModeProvider.findAgreement(agreementRef);
        } catch (EbMS3Exception ex) {
            assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0001, ex.getErrorCode());
        }
    }

    @Test
    public void testGetPartyByIdentifier() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getParties();
            result = configuration.getBusinessProcesses().getParties();
        }};
        Assert.assertNull(cachingPModeProvider.getPartyByIdentifier("test"));
    }

    @Test
    public void testGetSenderParty() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        String partyKey = "red_gw";
        String pModeKey = "test";
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getSenderPartyNameFromPModeKey(pModeKey);
            result = partyKey;
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getParties();
            result = configuration.getBusinessProcesses().getParties();
        }};
        try {
            cachingPModeProvider.getSenderParty("test");
        } catch (ConfigurationException ex) {
            assertEquals(ex.getMessage(), "no matching sender party found with name:" + partyKey);
        }
    }

    @Test
    public void testGetReceiverParty() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        String partyKey = "red_gw";
        String pModeKey = "test";
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getReceiverPartyNameFromPModeKey(pModeKey);
            result = partyKey;
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getParties();
            result = configuration.getBusinessProcesses().getParties();
        }};
        Party receiverParty = cachingPModeProvider.getReceiverParty(pModeKey);
        assertNotNull(receiverParty);
    }

    @Test
    public void testGetReceiverParty_notFound() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        String partyKey = "notfound";
        String pModeKey = "test";
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getReceiverPartyNameFromPModeKey(pModeKey);
            result = partyKey;
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getParties();
            result = configuration.getBusinessProcesses().getParties();
        }};
        try {
            cachingPModeProvider.getReceiverParty(pModeKey);
            fail();
        } catch (ConfigurationException ex) {
            assertEquals(ex.getMessage(), "No matching receiver party found with name: " + partyKey);
        }
    }

    @Test
    public void testGetService() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        String serviceKey = "testService2";
        String pModeKey = "test";
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getServiceNameFromPModeKey(pModeKey);
            result = serviceKey;
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getServices();
            result = configuration.getBusinessProcesses().getServices();
        }};
        Service service = cachingPModeProvider.getService(pModeKey);
        assertNotNull(service);
    }

    @Test
    public void testGetService_fail() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        String serviceKey = "serviceNotFound";
        String pModeKey = "test";
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getServiceNameFromPModeKey(pModeKey);
            result = serviceKey;
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getServices();
            result = configuration.getBusinessProcesses().getServices();
        }};
        try {
            cachingPModeProvider.getService(pModeKey);
            fail();

        } catch (ConfigurationException ex) {
            assertEquals(ex.getMessage(), "no matching service found with name: " + serviceKey);
        }
    }

    @Test
    public void testGetAction() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        String actionKey = "tc1Action";
        String pModeKey = "test";
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getActionNameFromPModeKey(pModeKey);
            result = actionKey;
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getActions();
            result = configuration.getBusinessProcesses().getActions();
        }};
        Action action = cachingPModeProvider.getAction(pModeKey);
        assertNotNull(action);
    }

    @Test
    public void testGetAction_notFound() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        String actionKey = "actionKeyNotFound";
        String pModeKey = "test";
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getActionNameFromPModeKey(pModeKey);
            result = actionKey;
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getActions();
            result = configuration.getBusinessProcesses().getActions();
        }};
        try {
            cachingPModeProvider.getAction(pModeKey);
            fail();
        } catch (ConfigurationException ex) {
            assertEquals(ex.getMessage(), "no matching action found with name: " + actionKey);
        }
    }

    @Test
    public void testGetAgreement() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        String agreementKey = "agreementEmpty";
        String pModeKey = "test";
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getAgreementRefNameFromPModeKey(pModeKey);
            result = agreementKey;
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getAgreements();
            result = configuration.getBusinessProcesses().getAgreements();
        }};
        Agreement agreement = cachingPModeProvider.getAgreement(pModeKey);
        assertNotNull(agreement);
    }

    @Test
    public void testGetAgreement_failed() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        String agreementKey = "agreementKeyNotFound";
        String pModeKey = "test";
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getAgreementRefNameFromPModeKey(pModeKey);
            result = agreementKey;
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getAgreements();
            result = configuration.getBusinessProcesses().getAgreements();
        }};
        try {
            cachingPModeProvider.getAgreement(pModeKey);
            fail();
        } catch (ConfigurationException ex) {
            assertEquals(ex.getMessage(), "no matching agreement found with name: " + agreementKey);
        }
    }

    @Test
    public void testGetLegConfiguration() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        String legKey = "pushTestcase1tc1Action";
        String pModeKey = "test";
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getLegConfigurationNameFromPModeKey(pModeKey);
            result = legKey;
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getLegConfigurations();
            result = configuration.getBusinessProcesses().getLegConfigurations();
        }};
        LegConfiguration legConfiguration = cachingPModeProvider.getLegConfiguration(pModeKey);
        assertNotNull(legConfiguration);
    }

    @Test
    public void testGetLegConfiguration_failed() throws JAXBException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        String legKey = "legKeyNotFound";
        String pModeKey = "test";
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.getLegConfigurationNameFromPModeKey(pModeKey);
            result = legKey;
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getLegConfigurations();
            result = configuration.getBusinessProcesses().getLegConfigurations();
        }};
        try {
            cachingPModeProvider.getLegConfiguration(pModeKey);
            fail();

        } catch (ConfigurationException ex) {
            assertEquals(ex.getMessage(), "no matching legConfiguration found with name: " + legKey);
        }
    }

    @Test
    public void testGetRetentionDownloadedByMpcURI() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        assertEquals(0, cachingPModeProvider.getRetentionDownloadedByMpcURI(ANOTHERMPC.toLowerCase()));
    }

    @Test
    public void testGetRetentionUndownloadedByMpcURI() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        assertEquals(-1, cachingPModeProvider.getRetentionUndownloadedByMpcURI(NONEXISTANTMPC));
    }

    @Test
    public void testGetRetentionSentByMpcURI() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        Assert.assertEquals(-1, cachingPModeProvider.getRetentionSentByMpcURI(NONEXISTANTMPC));
        Assert.assertEquals(-1, cachingPModeProvider.getRetentionSentByMpcURI(DEFAULT_MPC_URI));
    }


    @Test
    public void getRetentionMaxBatchByMpcURI() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        Assert.assertEquals(10, cachingPModeProvider.getRetentionMaxBatchByMpcURI(NONEXISTANTMPC, 10));
        Assert.assertEquals(10, cachingPModeProvider.getRetentionMaxBatchByMpcURI(DEFAULT_MPC_URI, 10));
    }

    @Test
    public void isDeleteMessageMetadataByMpcURI() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, JAXBException {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);
        new Expectations() {{
            cachingPModeProvider.getConfiguration().getMpcs();
            result = configuration.getMpcs();
        }};

        Assert.assertFalse(cachingPModeProvider.isDeleteMessageMetadataByMpcURI(NONEXISTANTMPC));
        Assert.assertFalse(cachingPModeProvider.isDeleteMessageMetadataByMpcURI(DEFAULT_MPC_URI));
    }

    @Test
    public void findUserMessageExchangeContextPush() throws EbMS3Exception {
        String legName = "NoSecNoEnc";

        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.findAgreement(userMessage.getAgreementRef());
            result = agreement;

            cachingPModeProvider.findSenderParty(userMessage);
            result = senderParty;

            cachingPModeProvider.findReceiverParty(userMessage, false, senderParty, false);
            result = receiverParty;

            cachingPModeProvider.findSenderRole(userMessage);
            result = initiatorRole;

            cachingPModeProvider.findReceiverRole(userMessage);
            result = responderRole;

            userMessage.getService();
            result = serviceEntity;

            cachingPModeProvider.findServiceName(serviceEntity);
            result = service;

            userMessage.getActionValue();
            result = action;

            cachingPModeProvider.findActionName(action);
            result = action;

            cachingPModeProvider.findLegName(agreement, senderParty, receiverParty, service, action, initiatorRole, responderRole, null, null);
            result = legName;
        }};

        MessageExchangeConfiguration messageExchangeConfiguration = cachingPModeProvider.findUserMessageExchangeContext(userMessage, MSHRole.SENDING, false, null);
        assertEquals(senderParty + PMODEKEY_SEPARATOR + receiverParty + PMODEKEY_SEPARATOR + service + PMODEKEY_SEPARATOR + action + PMODEKEY_SEPARATOR + agreement + PMODEKEY_SEPARATOR + legName, messageExchangeConfiguration.getPmodeKey());

    }

    @Test
    public void testFindUserMessageExchangeContextSenderNotProvided() {

        MSHRole mshRole1 = MSHRole.SENDING;
        new Expectations(cachingPModeProvider) {{
            userMessage.getPartyInfo().getFrom().getFromPartyId();
            result = partyId1;
        }};
        try {
            cachingPModeProvider.findUserMessageExchangeContext(userMessage, mshRole1, true, null);
            Assert.fail("expected error that sender party is missing");
        } catch (EbMS3Exception ex) {
            assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0003, ex.getErrorCode());
            assertEquals("Sender party could not be found for the value  " + partyId1, ex.getErrorDetail());
            assertEquals(mshRole1, ex.getMshRole());
        }
    }

    @Test
    public void findSenderParty() {

        new Expectations() {{
            userMessage.getPartyInfo().getFrom().getFromPartyId();
            result = null;
        }};
        try {
            cachingPModeProvider.findSenderParty(userMessage);
            Assert.fail("expected error that sender party is missing");
        } catch (EbMS3Exception ex) {
            assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0003, ex.getErrorCode());
            assertEquals("Mandatory field From PartyId is not provided.", ex.getErrorDetail());
        }
    }

    @Test
    public void findSenderParty_IdNotFound() throws EbMS3Exception {
        final Set<PartyId> fromPartyId = new HashSet<>();
        PartyId partyId1 = new PartyId();
        partyId1.setValue("domibus-blue");
        fromPartyId.add(partyId1);

        Exception expectedException = EbMS3ExceptionBuilder.getInstance()
                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0003)
                .message("No matching party found for type [] and value []")
                .build();
        new Expectations(cachingPModeProvider) {{
            userMessage.getPartyInfo().getFrom().getFromPartyId();
            result = fromPartyId;

            cachingPModeProvider.findPartyName(partyId1);
            result = expectedException;
        }};
        try {
            cachingPModeProvider.findSenderParty(userMessage);
            Assert.fail("expected error:" + expectedException.getMessage());
        } catch (EbMS3Exception e) {
            assertEquals(expectedException, e);
        }
    }

    @Test
    public void testFindUserMessageExchangeContextReceiverNotProvided() throws EbMS3Exception {

        MSHRole mshRole1 = MSHRole.SENDING;

        new Expectations(cachingPModeProvider) {{
            userMessage.getPartyInfo().getTo().getToPartyId();
            result = null;

            cachingPModeProvider.findSenderParty(userMessage);
            result = senderParty;
        }};

        try {
            cachingPModeProvider.findUserMessageExchangeContext(userMessage, mshRole1, true, null);
            Assert.fail("expected error that receiver party is missing");
        } catch (EbMS3Exception ex) {
            assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0003, ex.getErrorCode());
            assertEquals("Mandatory field To PartyId is not provided.", ex.getErrorDetail());
            assertEquals(mshRole1, ex.getMshRole());
        }
    }

    @Test
    public void findReceiverParty_IdNotFound() throws EbMS3Exception {
        final Set<PartyId> toPartyId = new HashSet<>();
        PartyId partyId1 = new PartyId();
        partyId1.setValue("domibus-red");
        toPartyId.add(partyId1);

        Exception expectedException = EbMS3ExceptionBuilder.getInstance()
                .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0003)
                .message("No matching party found for type [] and value []")
                .build();
        new Expectations(cachingPModeProvider) {{
            userMessage.getPartyInfo().getTo().getToPartyId();
            result = toPartyId;

            cachingPModeProvider.findPartyName(partyId1);
            result = expectedException;
        }};
        try {
            cachingPModeProvider.findReceiverParty(userMessage, false, senderParty, false);
            Assert.fail("expected error:" + expectedException.getMessage());
        } catch (EbMS3Exception e) {
            assertEquals(expectedException, e);
        }
    }

    @Test
    public void findSenderRole_RoleNotProvided() {
        new Expectations(cachingPModeProvider) {{
            userMessage.getPartyInfo().getFrom().getRoleValue();
            result = " ";
        }};
        try {
            cachingPModeProvider.findSenderRole(userMessage);
            Assert.fail("expected error that sender role should be provided");
        } catch (EbMS3Exception e) {
            assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0003, e.getErrorCode());
            assertEquals("Mandatory field Sender Role is not provided.", e.getErrorDetail());
        }
    }

    @Test
    public void findSenderRole_OK() throws EbMS3Exception {
        new Expectations(cachingPModeProvider) {{
            userMessage.getPartyInfo().getFrom().getRoleValue();
            result = initiatorRole.getValue();

            cachingPModeProvider.getBusinessProcessRole(initiatorRole.getValue());
            result = initiatorRole;
        }};
        assertEquals(cachingPModeProvider.findSenderRole(userMessage), initiatorRole);
        new FullVerifications() {
        };
    }

    @Test
    public void findReceiverRole_RoleNotProvided() {
        new Expectations(cachingPModeProvider) {{
            userMessage.getPartyInfo().getTo().getRoleValue();
            result = " ";
        }};
        try {
            cachingPModeProvider.findReceiverRole(userMessage);
            Assert.fail("expected error that responder role should be provided");
        } catch (EbMS3Exception e) {
            assertEquals(ErrorCode.EbMS3ErrorCode.EBMS_0003, e.getErrorCode());
            assertEquals("Mandatory field Receiver Role is not provided.", e.getErrorDetail());
        }
    }

    @Test
    public void findReceiverRole_OK() throws EbMS3Exception {
        new Expectations(cachingPModeProvider) {{
            userMessage.getPartyInfo().getTo().getRoleValue();
            result = responderRole.getValue();

            cachingPModeProvider.getBusinessProcessRole(responderRole.getValue());
            result = responderRole;
        }};

        assertEquals(cachingPModeProvider.findReceiverRole(userMessage), responderRole);

        new FullVerifications() {
        };
    }

    @Test
    public void checkAgreementMismatch() {
        new Expectations() {{
            legFilterCriteria.getAgreementName();
            result = agreement;
        }};
        cachingPModeProvider.checkAgreementMismatch(process, legFilterCriteria);

        new FullVerifications() {{
            cachingPModeProvider.matchAgreement(process, agreement);
            final String errorString;
            legFilterCriteria.appendProcessMismatchErrors(process, errorString = withCapture());
            assertTrue(errorString.contains(agreement));
        }};
    }

    @Test
    public void checkInitiatorMismatch() {
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.matchInitiator(process, anyString);
            result = false;

            processTypePartyExtractor.getSenderParty();
            result = senderParty;
        }};
        cachingPModeProvider.checkInitiatorMismatch(process, processTypePartyExtractor, legFilterCriteria);
        new FullVerifications() {{
            final String errorString;
            legFilterCriteria.appendProcessMismatchErrors(process, errorString = withCapture());
            assertTrue(errorString.contains(senderParty));
        }};
    }

    @Test
    public void checkResponderMismatch() {
        new Expectations(cachingPModeProvider) {{
            cachingPModeProvider.matchResponder(process, anyString);
            result = false;

            processTypePartyExtractor.getReceiverParty();
            result = receiverParty;
        }};
        cachingPModeProvider.checkResponderMismatch(process, processTypePartyExtractor, legFilterCriteria);
        new FullVerifications() {{
            final String errorString;
            legFilterCriteria.appendProcessMismatchErrors(process, errorString = withCapture());
            assertTrue(errorString.contains(receiverParty));
        }};
    }

    @Test
    public void checkInitiatorRoleMismatch() {
        new Expectations(cachingPModeProvider) {{
            process.getInitiatorRole();
            result = role1;

            legFilterCriteria.getInitiatorRole();
            result = initiatorRole;

            cachingPModeProvider.matchRole(role1, initiatorRole);
            result = false;
        }};
        cachingPModeProvider.checkInitiatorRoleMismatch(process, legFilterCriteria);
        new FullVerifications() {{
            final String errorString;
            legFilterCriteria.appendProcessMismatchErrors(process, errorString = withCapture());
            assertTrue(errorString.contains(initiatorRole.toString()));
        }};
    }

    @Test
    public void checkResponderRoleMismatch() {
        new Expectations(cachingPModeProvider) {{
            process.getResponderRole();
            result = role1;

            legFilterCriteria.getResponderRole();
            result = responderRole;

            cachingPModeProvider.matchRole(role1, responderRole);
            result = false;
        }};
        cachingPModeProvider.checkResponderRoleMismatch(process, legFilterCriteria);
        new FullVerifications() {{
            final String errorString;
            legFilterCriteria.appendProcessMismatchErrors(process, errorString = withCapture());
            assertTrue(errorString.contains(responderRole.toString()));
        }};
    }

    @Test
    public void checkServiceMismatch() {
        new Expectations() {{
            legConfiguration.getService().getName();
            result = "anotherServiceName";

            legFilterCriteria.getService();
            result = service;
        }};
        cachingPModeProvider.checkServiceMismatch(legConfiguration, legFilterCriteria);
        new FullVerifications() {{
            final String errorString;
            legFilterCriteria.appendLegMismatchErrors(legConfiguration, errorString = withCapture());
            assertTrue(errorString.contains(service));
        }};
    }

    @Test
    public void checkActionMismatch() {
        new Expectations() {{
            legConfiguration.getAction().getName();
            result = "anotherActionName";

            legFilterCriteria.getAction();
            result = action;
        }};
        cachingPModeProvider.checkActionMismatch(legConfiguration, legFilterCriteria);
        new FullVerifications() {{
            final String errorString;
            legFilterCriteria.appendLegMismatchErrors(legConfiguration, errorString = withCapture());
            assertTrue(errorString.contains(action));
        }};
    }

    @Test
    public void isPartyIdTypeMatching() {
        assertTrue(cachingPModeProvider.isPartyIdTypeMatching(null, null));
        assertTrue(cachingPModeProvider.isPartyIdTypeMatching("", null));
        assertTrue(cachingPModeProvider.isPartyIdTypeMatching(null, ""));
        assertTrue(cachingPModeProvider.isPartyIdTypeMatching("", ""));
        assertTrue(cachingPModeProvider.isPartyIdTypeMatching("testidType", "TESTIDTYPE"));
        assertFalse(cachingPModeProvider.isPartyIdTypeMatching("testidType1", "TESTIDTYPE2"));

    }

    @Test
    public void getAllLegConfigurations() throws Exception {
        configuration = loadSamplePModeConfiguration(VALID_PMODE_CONFIG_URI);

        new Expectations() {{
            cachingPModeProvider.getConfiguration().getBusinessProcesses().getLegConfigurations();
            result = configuration.getBusinessProcesses().getLegConfigurations();
        }};
        LegConfigurationPerMpc allLegConfigurations = cachingPModeProvider.getAllLegConfigurations();
        assertEquals(1, allLegConfigurations.entrySet().size());

        new FullVerifications() {
        };
    }
}
