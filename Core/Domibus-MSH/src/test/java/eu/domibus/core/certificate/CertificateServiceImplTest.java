package eu.domibus.core.certificate;

import com.google.common.collect.Lists;
import eu.domibus.api.crypto.CryptoException;
import eu.domibus.api.crypto.NoKeyStoreContentInformationException;
import eu.domibus.api.multitenancy.DomainContextProvider;
import eu.domibus.api.multitenancy.DomainTaskExecutor;
import eu.domibus.api.pki.*;
import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.api.property.encryption.PasswordDecryptionService;
import eu.domibus.api.security.TrustStoreEntry;
import eu.domibus.core.alerts.configuration.common.AlertConfigurationService;
import eu.domibus.core.alerts.configuration.generic.RepetitiveAlertConfiguration;
import eu.domibus.core.alerts.model.common.AlertType;
import eu.domibus.core.alerts.service.EventService;
import eu.domibus.core.audit.AuditService;
import eu.domibus.core.certificate.crl.CRLService;
import eu.domibus.core.exception.ConfigurationException;
import eu.domibus.core.util.SecurityUtilImpl;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.test.common.PKIUtil;
import mockit.*;
import mockit.integration.junit4.JMockit;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator;
import org.bouncycastle.util.io.pem.PemObjectGenerator;
import org.bouncycastle.util.io.pem.PemWriter;
import org.hamcrest.core.Is;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

import static eu.domibus.logging.DomibusMessageCode.SEC_CERTIFICATE_SOON_REVOKED;
import static eu.domibus.logging.DomibusMessageCode.SEC_DOMIBUS_CERTIFICATE_REVOKED;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.*;

/**
 * Created by Cosmin Baciu on 07-Jul-16.
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "ConstantConditions", "UnusedAssignment", "JUnitMalformedDeclaration"})
@RunWith(JMockit.class)
public class CertificateServiceImplTest {

    private static final String TEST_CERTIFICATE_CONTENT = "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";
    private static final String TEST_CERTIFICATE_CONTENT_PEM = "-----BEGIN CERTIFICATE-----\n" +
            TEST_CERTIFICATE_CONTENT + "\n" +
            "-----END CERTIFICATE-----";

    @Tested
    CertificateServiceImpl certificateService;

    @Injectable
    CRLService crlService;

    @Injectable
    private DomibusPropertyProvider domibusPropertyProvider;

    @Injectable
    CertificateDao certificateDao;

    @Injectable
    private EventService eventService;

    @Injectable
    CertificateHelper certificateHelper;

    @Injectable
    protected DomainTaskExecutor domainTaskExecutor;

    @Injectable
    private PasswordDecryptionService passwordDecryptionService;

    @Injectable
    private DomainContextProvider domainContextProvider;

    @Injectable
    AlertConfigurationService alertConfigurationService;

    @Injectable
    AuditService auditService;

    @Injectable
    KeystorePersistenceService keystorePersistenceService;

    @Injectable
    SecurityUtilImpl securityUtil;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    PKIUtil pkiUtil = new PKIUtil();

    @Before
    public void init() {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    @Test
    public void testIsCertificateChainValidPathV1() throws CertificateException {
        new Expectations() {{
            crlService.isCertificateRevoked((X509Certificate) any);
            result = false;
        }};

        String certStrPath1 = "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";

        final byte[] bytes = Base64.decodeBase64(certStrPath1);
        org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory certificateFactory = new org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory();
        List<? extends java.security.cert.Certificate> certificateChain = certificateFactory.engineGenerateCertPath(new ByteArrayInputStream(bytes)).getCertificates();
        System.out.println(certificateChain);

        Assert.assertTrue(certificateService.isCertificateChainValid(certificateChain));

        new Verifications() {{
            crlService.isCertificateRevoked((X509Certificate) any);
            times = 3;
        }};
    }


    @Test
    public void testIsCertificateChainValidPathV1Revoked() throws CertificateException {
        new Expectations() {{
            crlService.isCertificateRevoked((X509Certificate) any);
            result = true;
        }};

        String certStrPath1 = "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";

        final byte[] bytes = Base64.decodeBase64(certStrPath1);
        org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory certificateFactory = new org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory();
        List<? extends java.security.cert.Certificate> certificateChain = certificateFactory.engineGenerateCertPath(new ByteArrayInputStream(bytes)).getCertificates();
        System.out.println(certificateChain);

        Assert.assertFalse(certificateService.isCertificateChainValid(certificateChain));
    }

    @Test
    public void testCertificateChain() throws CertificateException {

        InputStream in = new ByteArrayInputStream(TEST_CERTIFICATE_CONTENT_PEM.getBytes());
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        final java.security.cert.Certificate certificate = certFactory.generateCertificate(in);
        System.out.println(certificate);

        Assert.assertNotNull(certificate);
    }

    @Test
    public void testIsCertificateChainValid(@Injectable final KeyStore trustStore) throws Exception {
        final String receiverAlias = "red_gw";

        final X509Certificate rootCertificate = pkiUtil.createCertificate(BigInteger.ONE, null);
        final X509Certificate receiverCertificate = pkiUtil.createCertificate(BigInteger.ONE, null);

        new Expectations(certificateService) {{
            trustStore.getCertificateChain(receiverAlias);
            result = new X509Certificate[]{receiverCertificate, rootCertificate};

            certificateService.isCertificateValid(rootCertificate);
            result = true;

            certificateService.isCertificateValid(receiverCertificate);
            result = true;
        }};

        boolean certificateChainValid = certificateService.isCertificateChainValid(trustStore, receiverAlias);
        assertTrue(certificateChainValid);

        new Verifications() {{ // a "verification block"
            // Verifies an expected invocation:
            certificateService.isCertificateValid(rootCertificate);
            times = 1;
            certificateService.isCertificateValid(receiverCertificate);
            times = 1;
        }};
    }

    @Test
    public void testIsCertificateChainValidWithNotValidCertificateRoot(@Injectable final KeyStore trustStore) throws Exception {
        final String receiverAlias = "red_gw";

        final X509Certificate rootCertificate = pkiUtil.createCertificate(BigInteger.ONE, null);
        final X509Certificate receiverCertificate = pkiUtil.createCertificate(BigInteger.ONE, null);

        new Expectations(certificateService) {{
            trustStore.getCertificateChain(receiverAlias);
            result = new X509Certificate[]{receiverCertificate, rootCertificate};

            certificateService.isCertificateValid(receiverCertificate);
            result = false;
        }};

        boolean certificateChainValid = certificateService.isCertificateChainValid(trustStore, receiverAlias);
        assertFalse(certificateChainValid);

        new Verifications() {{
            certificateService.isCertificateValid(receiverCertificate);
            times = 1;

            certificateService.isCertificateValid(rootCertificate);
            times = 0;
        }};
    }

    @Test
    public void testIsCertificateValid(@Injectable final X509Certificate certificate) {
        new Expectations(certificateService) {{
            certificateService.checkValidity(certificate);
            result = true;

            crlService.isCertificateRevoked(certificate);
            result = false;
        }};

        boolean certificateValid = certificateService.isCertificateValid(certificate);
        assertTrue(certificateValid);
    }

    @Test
    public void testIsCertificateValidWithExpiredCertificate(@Injectable final X509Certificate certificate) {
        new Expectations(certificateService) {{
            certificateService.checkValidity(certificate);
            result = false;
        }};

        boolean certificateValid = certificateService.isCertificateValid(certificate);
        assertFalse(certificateValid);
    }

    @Test
    public void testCheckValidityValidWithExpiredCertificate() throws Exception {
        X509Certificate x509Certificate = pkiUtil.createCertificate(BigInteger.ONE, Date.from(LocalDate.now().minusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant()),
                Date.from(LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()), null);
        boolean certificateValid = certificateService.checkValidity(x509Certificate);
        assertFalse(certificateValid);
    }

    @Test
    public void testCheckValidityWithNotYetValidCertificate() throws Exception {
        X509Certificate x509Certificate = pkiUtil.createCertificate(BigInteger.ONE, Date.from(LocalDate.now().plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant()),
                Date.from(LocalDate.now().plusDays(5).atStartOfDay(ZoneId.systemDefault()).toInstant()), null);

        boolean certificateValid = certificateService.checkValidity(x509Certificate);
        assertFalse(certificateValid);
    }

    @Test
    public void testGetTrustStoreEntries(@Mocked final KeyStore trustStore,
                                         @Mocked final Enumeration<String> aliasEnum,
                                         @Mocked final X509Certificate blueCertificate,
                                         @Mocked final X509Certificate redCertificate) throws KeyStoreException {
        final Date validFrom = Date.from(ZonedDateTime.now(ZoneOffset.UTC).toInstant());
        final Date validUntil = Date.from(ZonedDateTime.now(ZoneOffset.UTC).plusDays(10).toInstant());
        new Expectations() {{
            aliasEnum.hasMoreElements();
            returns(true, true, false);
            aliasEnum.nextElement();
            returns("blue_gw", "red_gw");

            trustStore.aliases();
            result = aliasEnum;

            blueCertificate.getSubjectDN().getName();
            result = "C=BE,O=eDelivery,CN=blue_gw";
            blueCertificate.getIssuerDN().getName();
            result = "C=BE,O=eDelivery,CN=blue_gw";
            blueCertificate.getNotBefore();
            result = validFrom;
            blueCertificate.getNotAfter();
            result = validUntil;

            redCertificate.getSubjectDN().getName();
            result = "C=BE,O=eDelivery,CN=red_gw";
            redCertificate.getIssuerDN().getName();
            result = "C=BE,O=eDelivery,CN=red_gw";
            redCertificate.getNotBefore();
            result = validFrom;
            redCertificate.getNotAfter();
            result = validUntil;

            trustStore.getCertificate("blue_gw");
            result = blueCertificate;
            trustStore.getCertificate("red_gw");
            result = redCertificate;
        }};
        final List<TrustStoreEntry> trustStoreEntries = certificateService.getStoreEntries(trustStore);
        assertEquals(2, trustStoreEntries.size());

        TrustStoreEntry trustStoreEntry = trustStoreEntries.get(0);
        assertEquals("blue_gw", trustStoreEntry.getName());
        assertEquals("C=BE,O=eDelivery,CN=blue_gw", trustStoreEntry.getSubject());
        assertEquals("C=BE,O=eDelivery,CN=blue_gw", trustStoreEntry.getIssuer());
        assertEquals(0, validFrom.compareTo(trustStoreEntry.getValidFrom()));
        assertEquals(0, validUntil.compareTo(trustStoreEntry.getValidUntil()));

        trustStoreEntry = trustStoreEntries.get(1);
        assertEquals("red_gw", trustStoreEntry.getName());
        assertEquals("C=BE,O=eDelivery,CN=red_gw", trustStoreEntry.getSubject());
        assertEquals("C=BE,O=eDelivery,CN=red_gw", trustStoreEntry.getIssuer());
        assertEquals(0, validFrom.compareTo(trustStoreEntry.getValidFrom()));
        assertEquals(0, validUntil.compareTo(trustStoreEntry.getValidUntil()));
    }


    @Test
    public void testGetTrustStoreEntriesWithKeyStoreException(@Injectable final KeyStore trustStore) throws KeyStoreException {

        new Expectations() {{
            trustStore.aliases();
            result = new KeyStoreException();
        }};
        assertEquals(0, certificateService.getStoreEntries(trustStore).size());
    }

    @Test
    public void saveCertificateAndLogRevocation(@Injectable KeyStore keyStore, @Injectable KeyStore trustStore) {

        certificateService.saveCertificateAndLogRevocation(trustStore, keyStore);

        new Verifications() {{
            certificateService.saveCertificateData(trustStore, keyStore);
            times = 1;
            certificateService.logCertificateRevocationWarning();
            times = 1;
        }};
    }

    @Test
    public void saveCertificateData(@Injectable KeyStore keyStore, @Injectable KeyStore trustStore) {
        final Certificate cert1 = new Certificate();
        final Certificate cert2 = new Certificate();
        final List<Certificate> certificates = Lists.newArrayList(cert1, cert2);
        new Expectations(certificateService) {{
            certificateService.groupAllKeystoreCertificates(trustStore, keyStore);
            result = certificates;
        }};
        certificateService.saveCertificateData(trustStore, keyStore);
        new Verifications() {{
            certificateDao.saveOrUpdate(withInstanceOf(Certificate.class));
            times = 2;
        }};
    }

    @Test
    public void logCertificateRevocationWarning(@Mocked final DomibusLogger LOG) {
        final Certificate soonRevokedCertificate = new Certificate();
        final Date now = new Date();
        final String soonRevokedAlias = "Cert1";
        soonRevokedCertificate.setNotAfter(now);
        soonRevokedCertificate.setAlias(soonRevokedAlias);
        soonRevokedCertificate.setCertificateType(CertificateType.PRIVATE);
        final List<Certificate> unNotifiedSoonRevokedCertificates = Lists.newArrayList(soonRevokedCertificate);

        final String revokedAlias = "Cert2";
        final Certificate revokedCertificate = new Certificate();
        revokedCertificate.setNotAfter(now);
        revokedCertificate.setAlias(revokedAlias);
        revokedCertificate.setCertificateType(CertificateType.PUBLIC);
        final List<Certificate> unNotifiedRevokedCertificates = Lists.newArrayList(revokedCertificate);

        new Expectations() {{
            certificateDao.getUnNotifiedSoonRevoked();
            result = unNotifiedSoonRevokedCertificates;
            certificateDao.getUnNotifiedRevoked();
            result = unNotifiedRevokedCertificates;
        }};
        certificateService.logCertificateRevocationWarning();

        new Verifications() {{
            LOG.securityWarn(SEC_CERTIFICATE_SOON_REVOKED, CertificateType.PRIVATE, soonRevokedAlias, now);
            times = 1;
            LOG.securityError(SEC_DOMIBUS_CERTIFICATE_REVOKED, CertificateType.PUBLIC, revokedAlias, now);
            times = 1;
            certificateDao.updateRevocation(soonRevokedCertificate);
            times = 1;
            certificateDao.updateRevocation(revokedCertificate);
            times = 1;
        }};
    }

    @Test
    public void retrieveCertificates(@Injectable final KeyStore keyStore, @Injectable final KeyStore trustStore) {

        Certificate certificate = new Certificate();
        certificate.setNotAfter(new Date());
        final List<Certificate> trustStoreCertificates = Lists.newArrayList(certificate);
        certificate = new Certificate();
        certificate.setNotAfter(new Date());
        final List<Certificate> keyStoreCertificates = Lists.newArrayList(certificate);

        new Expectations(certificateService) {{
            domibusPropertyProvider.getIntegerProperty(CertificateServiceImpl.REVOCATION_TRIGGER_OFFSET_PROPERTY);
            result = 15;
            times = 2;
            certificateService.extractCertificateFromKeyStore(trustStore);
            result = trustStoreCertificates;
            certificateService.extractCertificateFromKeyStore(keyStore);
            result = keyStoreCertificates;
        }};

        List<Certificate> certificates = certificateService.groupAllKeystoreCertificates(trustStore, keyStore);
        assertEquals(CertificateType.PUBLIC, certificates.get(0).getCertificateType());
        assertEquals(CertificateType.PRIVATE, certificates.get(1).getCertificateType());

    }

    @Test
    public void updateCertificateStatus() {
        new Expectations(certificateService) {{
            domibusPropertyProvider.getIntegerProperty(CertificateServiceImpl.REVOCATION_TRIGGER_OFFSET_PROPERTY);
            result = 15;
        }};

        Date now = new Date();

        Calendar c = Calendar.getInstance();
        c.setTime(now);
        c.add(Calendar.DATE, 16);
        CertificateStatus certificateStatus = certificateService.getCertificateStatus(c.getTime());
        assertEquals(CertificateStatus.OK, certificateStatus);

        c = Calendar.getInstance();
        c.setTime(now);
        c.add(Calendar.DATE, 14);
        certificateStatus = certificateService.getCertificateStatus(c.getTime());
        assertEquals(CertificateStatus.SOON_REVOKED, certificateStatus);

        c = Calendar.getInstance();
        c.setTime(now);
        c.add(Calendar.DATE, -1);
        certificateStatus = certificateService.getCertificateStatus(c.getTime());
        assertEquals(CertificateStatus.REVOKED, certificateStatus);
    }


    @Test
    public void extractCertificateFromKeyStore(@Injectable final KeyStore keyStore,
                                               @Injectable final Enumeration<String> aliases,
                                               @Injectable final X509Certificate x509Certificate) throws KeyStoreException, ParseException {
        final String keystoreAlias = "keystoreAlias";
        SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd");
        final Date notBefore = format.parse("2017/02/20");
        final Date notAfter = format.parse("2017/04/20");

        new Expectations() {{

            keyStore.aliases();
            result = aliases;

            aliases.hasMoreElements();
            times = 2;
            result = true;
            result = false;

            aliases.nextElement();
            times = 1;
            result = keystoreAlias;

            keyStore.getCertificate(keystoreAlias);
            result = x509Certificate;

            x509Certificate.getNotBefore();
            result = notBefore;

            x509Certificate.getNotAfter();
            result = notAfter;

        }};

        List<Certificate> certificates = certificateService.extractCertificateFromKeyStore(keyStore);
        assertEquals(1, certificates.size());
        assertEquals(certificates.get(0).getNotBefore(), notBefore);
        assertEquals(certificates.get(0).getNotAfter(), notAfter);
    }

    @SuppressWarnings("AccessStaticViaInstance")
    @Test
    public void sendCertificateImminentExpirationAlerts(final @Injectable RepetitiveAlertConfiguration imminentExpirationCertificateConfiguration, @Injectable final Certificate certificate) throws ParseException {

        SimpleDateFormat parser = new SimpleDateFormat("dd/MM/yyy HH:mm:ss");
        Date notAfter = parser.parse("23/10/1977 00:00:00");
        final int imminentExpirationDelay = 10;
        final int imminentExpirationFrequency = 14;
        final String alias = "blue_gw";
        final Date today = Date.from(ZonedDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant());
        final Date maxDate = Date.from(ZonedDateTime.now(ZoneOffset.UTC).plusDays(imminentExpirationDelay).toInstant());
        final Date notificationDate = Date.from(ZonedDateTime.now(ZoneOffset.UTC).minusDays(imminentExpirationFrequency).toInstant());
        List<Certificate> certificates = new ArrayList<>();
        certificates.add(certificate);
        new Expectations() {{

            imminentExpirationCertificateConfiguration.isActive();
            result = true;

            imminentExpirationCertificateConfiguration.getDelay();
            result = imminentExpirationDelay;

            imminentExpirationCertificateConfiguration.getFrequency();
            result = imminentExpirationFrequency;

            certificateDao.findImminentExpirationToNotifyAsAlert(withArgThat(greaterThan(notificationDate)), today, withArgThat(greaterThan(maxDate)));
            result = certificates;

            certificate.getAlias();
            result = alias;

            certificate.getNotAfter();
            result = notAfter;

        }};
        certificateService.sendCertificateImminentExpirationAlerts();
        new VerificationsInOrder() {{
            certificateDao.findImminentExpirationToNotifyAsAlert(withArgThat(greaterThan(notificationDate)), today, withArgThat(greaterThan(maxDate)));
            times = 1;
            certificateDao.saveOrUpdate(certificates.get(0));
            times = 1;
            eventService.enqueueImminentCertificateExpirationEvent(alias, notAfter);
            times = 1;
        }};
    }

    @SuppressWarnings("AccessStaticViaInstance")
    @Test
    public void sendCertificateExpiredAlerts(final @Injectable RepetitiveAlertConfiguration expiredCertificateConfiguration,
                                             @Injectable final Certificate certificate) throws ParseException {

        SimpleDateFormat parser = new SimpleDateFormat("dd/MM/yyy HH:mm:ss");
        Date notAfter = parser.parse("23/10/1977 00:00:00");
        final int revokedDuration = 10;
        final int revokedFrequency = 14;
        final String alias = "blue_gw";
        Date endNotification = Date.from(ZonedDateTime.now(ZoneOffset.UTC).minusDays(revokedDuration).toInstant());
        Date notificationDate = Date.from(ZonedDateTime.now(ZoneOffset.UTC).minusDays(revokedFrequency).toInstant());
        List<Certificate> certificates = new ArrayList<>();
        certificates.add(certificate);

        new Expectations() {{

            expiredCertificateConfiguration.isActive();
            result = true;

            expiredCertificateConfiguration.getDelay();
            result = revokedDuration;

            expiredCertificateConfiguration.getFrequency();
            result = revokedFrequency;

            certificateDao.findExpiredToNotifyAsAlert(withArgThat(greaterThan(notificationDate)), withArgThat(greaterThan(endNotification)));
            result = certificates;

            certificate.getAlias();
            result = alias;

            certificate.getNotAfter();
            result = notAfter;

        }};
        certificateService.sendCertificateExpiredAlerts();
        new VerificationsInOrder() {{
            certificateDao.findExpiredToNotifyAsAlert(withArgThat(greaterThan(notificationDate)), withArgThat(greaterThan(endNotification)));
            times = 1;
            certificateDao.saveOrUpdate(certificates.get(0));
            times = 1;
            eventService.enqueueCertificateExpiredEvent(alias, notAfter);
            times = 1;
        }};
    }

    @Test
    public void sendCertificateExpiredAlertsModuleInactive(@Injectable RepetitiveAlertConfiguration repetitiveAlertConfiguration) {
        new Expectations() {{
            alertConfigurationService.getConfiguration(AlertType.CERT_EXPIRED);
            result = repetitiveAlertConfiguration;

            repetitiveAlertConfiguration.isActive();
            result = false;
        }};
        certificateService.sendCertificateExpiredAlerts();
        new VerificationsInOrder() {{
        }};
    }

    @Test
    public void sendCertificateImminentExpirationAlertsModuleInactive(@Injectable RepetitiveAlertConfiguration imminentExpirationCertificateModuleConfiguration) {
        new Expectations() {{
            imminentExpirationCertificateModuleConfiguration.isActive();
            result = false;
        }};
        certificateService.sendCertificateImminentExpirationAlerts();
        new VerificationsInOrder() {{
        }};
    }

    @Test
    public void testPemFormatCertificateContent() {
        boolean result = this.certificateService.isPemFormat(TEST_CERTIFICATE_CONTENT_PEM);
        assertTrue(result);

        byte[] binaryCert = TEST_CERTIFICATE_CONTENT.getBytes();
        boolean nonPemResult = this.certificateService.isPemFormat(Arrays.toString(binaryCert));
        assertFalse(nonPemResult);
    }

    @Test
    public void testLoadCertificateFromString() {
        String certStr = TEST_CERTIFICATE_CONTENT_PEM;

        X509Certificate cert = this.certificateService.loadCertificate(certStr);
        Assert.assertNotNull(cert);

        String certStr2 = java.util.Base64.getMimeEncoder().encodeToString(certStr.getBytes());
        X509Certificate cert2 = this.certificateService.loadCertificate(certStr2);
        Assert.assertNotNull(cert2);

        X509Certificate cert3 = this.certificateService.loadCertificate(TEST_CERTIFICATE_CONTENT);
        Assert.assertNotNull(cert3);

        Assert.assertEquals(cert.toString(), cert2.toString());
        Assert.assertEquals(cert.toString(), cert3.toString());
    }

    @Test
    public void testConvertCertificateContent() {
        String subject = "OU=DEV, O=DIGIT, EMAILADDRESS=uumds@uumds.eu, C=BE, ST=Belgium, CN=UUMDS tests client certificate VALID";
        String fingerprint = "6bdfa1594a8b6ce48fe44ff6bb1989af7f3abd26c635ca304e8ee9036dfb36d7";

        TrustStoreEntry entry = this.certificateService.convertCertificateContent(TEST_CERTIFICATE_CONTENT_PEM);
        Assert.assertEquals(subject, entry.getSubject());
        Assert.assertEquals(fingerprint, entry.getFingerprints());
    }

    @Test(expected = DomibusCertificateException.class)
    public void serializeCertificateChainIntoPemFormatTest(@Injectable java.security.cert.Certificate certificate,
                                                           @Injectable PemWriter pw,
                                                           @Injectable StringWriter sw,
                                                           @Injectable JcaMiscPEMGenerator jcaMiscPEMGenerator,
                                                           @Injectable PemObjectGenerator gen
    ) throws IOException {
        List<java.security.cert.Certificate> certificates = new ArrayList<>();
        certificates.add(certificate);
        certificateService.serializeCertificateChainIntoPemFormat(certificates);
        new Verifications() {{
            pw.writeObject(gen);
            times = 1;
        }};
    }

    @Test
    public void throwsExceptionWhenFailingToPersistTheCurrentTrustStore(@Injectable KeyStoreContentInfo storeInfo,
                                                                        @Injectable KeystorePersistenceInfo persistenceInfo,
                                                                        @Injectable KeyStore trustStore) throws Exception {
        thrown.expect(CryptoException.class);
        thrown.expectMessage("Could not replace store");

        new Expectations(certificateService) {{
            certificateService.loadStore(storeInfo);
            result = trustStore;
            keystorePersistenceService.saveStore(storeInfo, persistenceInfo);
            result = new CryptoException("");
        }};

        // When
        certificateService.replaceStore(storeInfo, persistenceInfo, true);

        new Verifications() {{
            auditService.addStoreReplacedAudit(anyString);
            times = 0;
        }};
    }

    @Test
    public void throwsExceptionWhenFailingToRemoveExistingCertificateFromTheTrustStore(@Injectable KeystorePersistenceInfo persistenceInfo,
                                                                                       @Injectable KeyStore trustStore) throws KeyStoreException {

        thrown.expect(ConfigurationException.class);

        new Expectations(certificateService) {{
            certificateService.getStore(persistenceInfo);
            result = trustStore;
            trustStore.containsAlias("alias");
            result = true;
            trustStore.deleteEntry("alias");
            result = new KeyStoreException();
        }};

        // When
        certificateService.removeCertificate(persistenceInfo, "alias");
    }

    @Test
    public void persistsTheTrustStoreAfterRemovingCertificate(@Injectable KeyStore store,
                                                              @Injectable KeystorePersistenceInfo persistenceInfo) {

        final String alias = "alias";

        new Expectations(certificateService) {{
            certificateService.getStore(persistenceInfo);
            result = store;
            certificateService.doRemoveCertificate(store, alias);
            result = true;
        }};

        certificateService.removeCertificate(persistenceInfo, alias);

        new Verifications() {{
            keystorePersistenceService.saveStore(store, persistenceInfo);
            times = 1;
        }};
    }

    @Test
    public void doesNotPersistTheTrustStoreWhenRemovingCertificateThatDoesNotAlterItsContent(@Injectable KeyStore store,
                                                                                             @Injectable KeystorePersistenceInfo persistenceInfo) {

        final String alias = "alias";

        new Expectations(certificateService) {{
            certificateService.getStore(persistenceInfo);
            result = store;
            certificateService.doRemoveCertificate(store, alias);
            result = false;
        }};

        certificateService.removeCertificate(persistenceInfo, alias);

        new Verifications() {{
            keystorePersistenceService.saveStore(store, persistenceInfo);
            times = 0;
        }};
    }

    @Test
    public void throwsExceptionWhenFailingToOverwriteExistingCertificateIntoTheTrustStore(@Injectable X509Certificate certificate,
                                                                                          @Injectable KeyStore trustStore) throws KeyStoreException {
        thrown.expect(ConfigurationException.class);

        new Expectations() {{
            trustStore.containsAlias("alias");
            result = true;
            trustStore.deleteEntry("alias");
            result = new KeyStoreException();
        }};

        // When
        certificateService.doAddCertificate(trustStore, certificate, "alias", true);
    }

    @Test
    public void throwsExceptionWhenFailingToLoadTheCurrentTrustStore_NoSuchAlgorithmException(@Injectable KeyStoreContentInfo storeInfo,
                                                                                              @Injectable KeystorePersistenceInfo persistenceInfo,
                                                                                              @Injectable KeyStore store) {
        thrown.expect(CryptoException.class);
        thrown.expectCause(Is.isA(CryptoException.class));

        new Expectations(certificateService) {{
            certificateService.getStore(persistenceInfo);
            result = store;
            certificateService.loadStore(storeInfo);
            result = new CryptoException("");
        }};

        // When
        certificateService.replaceStore(storeInfo, persistenceInfo, true);

        new Verifications() {{
            auditService.addStoreReplacedAudit(storeInfo.getName());
            times = 0;
        }};
    }

    @Test
    public void throwsExceptionWhenFailingToPersistTheCurrentTrustStore_CertificateException(@Injectable KeyStoreContentInfo storeInfo,
                                                                                             @Injectable KeystorePersistenceInfo persistenceInfo,
                                                                                             @Injectable KeyStore store) {
        thrown.expect(CryptoException.class);
        thrown.expectMessage("Could not replace store");

        new Expectations(certificateService) {{
            certificateService.getStore(persistenceInfo);
            result = store;
            certificateService.loadStore(storeInfo);
            result = new CryptoException("");
        }};

        // When
        certificateService.replaceStore(storeInfo, persistenceInfo, true);

        new Verifications() {{
            auditService.addStoreReplacedAudit(storeInfo.getName());
            times = 0;
        }};
    }

    @Test
    public void doesNotPersistTheTrustStoreWhenAddingCertificateThatDoesNotAlterItsContent(@Injectable CertificateEntry certificateEntry,
                                                                                           @Injectable KeyStore store,
                                                                                           @Injectable KeystorePersistenceInfo persistenceInfo) {

        List<CertificateEntry> certificates = Arrays.asList(certificateEntry);

        new Expectations(certificateService) {{
            certificateService.getStore(persistenceInfo);
            result = store;
            certificateService.doAddCertificate(store, certificateEntry.getCertificate(), certificateEntry.getAlias(), false);
            result = false;
        }};

        certificateService.doAddCertificates(persistenceInfo, certificates, false);

        new Verifications() {{
            keystorePersistenceService.saveStore(store, persistenceInfo);
            times = 0;
        }};

    }

    @Test
    public void overwritesTrustedCertificateWhenAddingExistingCertificateIntoTheTrustStoreWithIntentionOfOverwritingIt(@Injectable X509Certificate certificate,
                                                                                                                       @Injectable KeyStore trustStore) throws KeyStoreException {
        final String alias = "alias";

        new Expectations() {{
            trustStore.containsAlias(alias);
            result = true;
        }};

        // When
        boolean result = certificateService.doAddCertificate(trustStore, certificate, alias, true);

        // Then
        Assert.assertTrue("Should have returned true when adding an existing certificate to the trust store with the intention of overwriting it", result);
        new Verifications() {{
            trustStore.deleteEntry(alias);
            trustStore.setCertificateEntry(alias, certificate);
        }};
    }

    @Test
    public void insertsTrustedCertificateWhenAddingNonExistingCertificateIntoTheTrustStore(@Injectable X509Certificate certificate,
                                                                                           @Injectable KeyStore trustStore) throws KeyStoreException {
        final String alias = "alias";
        new Expectations() {{
            trustStore.containsAlias(alias);
            result = false;
        }};

        // When
        boolean result = certificateService.doAddCertificate(trustStore, certificate, alias, true);

        // Then
        Assert.assertTrue("Should have returned true when adding a non-existing certificate to the trust store", result);
        new Verifications() {{
            trustStore.deleteEntry(alias);
            times = 0;
            trustStore.setCertificateEntry(alias, certificate);
        }};
    }

    @Test
    public void throwsExceptionWhenFailingToStoreTrustedCertificateIntoTheTrustStore(@Injectable X509Certificate certificate,
                                                                                     @Injectable KeyStore trustStore) throws KeyStoreException {
        thrown.expect(ConfigurationException.class);

        new Expectations() {{
            trustStore.containsAlias("alias");
            result = false;
            trustStore.setCertificateEntry("alias", certificate);
            result = new KeyStoreException();
        }};

        // When
        certificateService.doAddCertificate(trustStore, certificate, "alias", true);
    }

    @Test
    public void throwsExceptionWhenRemovingCertificateFromTheTrustStoreButFailingToCheckThePresenceOfItsAlias(@Injectable KeyStore trustStore) throws KeyStoreException {

        thrown.expect(CryptoException.class);
        thrown.expectMessage("Error while trying to get the alias from the store. This should never happen");

        new Expectations() {{
            trustStore.containsAlias("alias");
            result = new KeyStoreException();
        }};

        // When
        certificateService.doRemoveCertificate(trustStore, "alias");
    }

    @Test
    public void doesNothingWhenRemovingNonExistingCertificateFromTheTrustStore(@Injectable KeyStore trustStore) throws KeyStoreException {
        new Expectations() {{
            trustStore.containsAlias("alias");
            result = false;
        }};

        // When
        boolean result = certificateService.doRemoveCertificate(trustStore, "alias");

        // Then
        Assert.assertFalse("Should have returned false when removing a non-existing certificate from the trust store", result);
    }

    @Test
    public void removesTrustedCertificateWhenRemovingExistingCertificateFromTheTrustStore(@Injectable KeyStore trustStore) throws KeyStoreException {
        new Expectations() {{
            trustStore.containsAlias("alias");
            result = true;
        }};

        // When
        boolean result = certificateService.doRemoveCertificate(trustStore, "alias");

        // Then
        Assert.assertTrue("Should have returned true when removing an existing certificate from the trust store", result);
        new Verifications() {{
            trustStore.deleteEntry("alias");
        }};
    }

    @Test
    public void throwsExceptionWhenAddingCertificateIntoTheTrustStoreButFailingToCheckThePresenceOfItsAlias(@Injectable X509Certificate certificate,
                                                                                                            @Injectable KeyStore trustStore) throws KeyStoreException {
        thrown.expect(CryptoException.class);
        thrown.expectMessage("Error while trying to get the alias from the store. This should never happen");

        new Expectations() {{
            trustStore.containsAlias("alias");
            result = new KeyStoreException();
        }};

        // When
        certificateService.doAddCertificate(trustStore, certificate, "alias", true);
    }

    @Test
    public void returnsFalseWhenAddingExistingCertificateIntoTheTrustStoreWithoutIntentionOfOverwritingIt(@Injectable X509Certificate certificate,
                                                                                                          @Injectable KeyStore trustStore) throws KeyStoreException {
        new Expectations() {{
            trustStore.containsAlias("alias");
            result = true;
        }};

        // When
        boolean result = certificateService.doAddCertificate(trustStore, certificate, "alias", false);

        // Then
        Assert.assertFalse("Should have returned false when adding an existing certificate to the trust store without the intention of overwriting it", result);
    }


    @Test
    public void loadTrustStoreFromContent(@Injectable KeyStoreContentInfo storeInfo, @Injectable KeyStore keystore)
            throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {

        thrown.expect(CryptoException.class);
        thrown.expectCause(Is.isA(IOException.class));
        thrown.expectMessage("[DOM_001]:Could not load store named");

        new Expectations(certificateService) {{
            storeInfo.getType();
            result = "jks";
            certificateService.getNewKeystore(anyString);
            result = keystore;
            storeInfo.getPassword();
            result = "test123";
            keystore.load((InputStream) any, "test123".toCharArray());
            result = new IOException();
        }};

        // When
        certificateService.loadStore(storeInfo);
    }

    @Test(expected = NoKeyStoreContentInformationException.class)
    public void loadStore_throwsExceptionWhenNoKeystoreContentInformationAvailable() {
        certificateService.loadStore(null);
    }

    @Test
    public void doAddCertificates(@Injectable KeyStore store, @Injectable KeystorePersistenceInfo persistenceInfo,
                                  @Injectable CertificateEntry cert1, @Injectable CertificateEntry cert2) {
        List<CertificateEntry> certificates = Arrays.asList(cert1, cert2);
        boolean overwrite = true;

        new Expectations(certificateService) {{
            certificateService.getStore(persistenceInfo);
            result = store;
            certificateService.doAddCertificate(store, (X509Certificate) any, anyString, overwrite);
            result = true;
            keystorePersistenceService.saveStore(store, persistenceInfo);
        }};

        boolean added = certificateService.doAddCertificates(persistenceInfo, certificates, overwrite);

        assertTrue(added);
        new Verifications() {{
            keystorePersistenceService.saveStore(store, persistenceInfo);
        }};
    }

    @Test
    public void doAddCertificatesNotAdded(@Injectable KeyStore store, @Injectable KeystorePersistenceInfo persistenceInfo,
                                          @Injectable CertificateEntry cert1, @Injectable CertificateEntry cert2) {
        List<CertificateEntry> certificates = Arrays.asList(cert1, cert2);
        boolean overwrite = true;

        new Expectations(certificateService) {{
            certificateService.getStore(persistenceInfo);
            result = store;
            certificateService.doAddCertificate(store, (X509Certificate) any, anyString, overwrite);
            result = false;
        }};

        boolean added = certificateService.doAddCertificates(persistenceInfo, certificates, overwrite);

        assertFalse(added);
        new Verifications() {{
            keystorePersistenceService.saveStore(store, persistenceInfo);
            times = 0;
        }};
    }

    @Test
    public void doRemoveCertificates(@Injectable KeyStore store, @Injectable KeystorePersistenceInfo persistenceInfo) {
        final String alias1 = "alias1";
        final String alias2 = "alias2";
        List<String> certificates = Arrays.asList(alias1, alias2);

        new Expectations(certificateService) {{
            certificateService.getStore(persistenceInfo);
            result = store;
            certificateService.doRemoveCertificate(store, anyString);
            result = true;
            keystorePersistenceService.saveStore(store, persistenceInfo);
        }};

        boolean removed = certificateService.doRemoveCertificates(persistenceInfo, certificates);

        assertTrue(removed);
        new Verifications() {{
            keystorePersistenceService.saveStore(store, persistenceInfo);
        }};
    }

    @Test
    public void doRemoveCertificatesNotRemoved(@Injectable KeyStore store, @Injectable KeystorePersistenceInfo persistenceInfo) {

        final String trustStorePassword = "pwd";
        final String alias1 = "alias1";
        final String alias2 = "alias2";
        List<String> certificates = Arrays.asList(alias1, alias2);

        new Expectations(certificateService) {{
            certificateService.getStore(persistenceInfo);
            result = store;
            certificateService.doRemoveCertificate(store, anyString);
            result = false;
        }};

        boolean removed = certificateService.doRemoveCertificates(persistenceInfo, certificates);

        assertFalse(removed);
        new Verifications() {{
            keystorePersistenceService.saveStore(store, persistenceInfo);
            times = 0;
        }};
    }

    @Test
    public void testGetCertificatePolicyIdentifiers() throws Exception {
        String id1 = "1.3.6.1.4.1.7879.13.25";
        String id2 = "0.4.0.2042.1.1";
        X509Certificate certificate = pkiUtil.createCertificate(BigInteger.ONE, null, Arrays.asList(id1, id2));
        List<String> list = certificateService.getCertificatePolicyIdentifiers(certificate);

        assertEquals(2, list.size());
        assertEquals(id1, list.get(0));
        assertEquals(id2, list.get(1));
    }

    @Test
    public void testGetCertificatePolicyIdentifiersWithNoPolicyExtension() throws Exception {
        X509Certificate certificate = pkiUtil.createCertificate(BigInteger.ONE, null, null);
        List<String> list = certificateService.getCertificatePolicyIdentifiers(certificate);

        assertEquals(0, list.size());
    }

}
