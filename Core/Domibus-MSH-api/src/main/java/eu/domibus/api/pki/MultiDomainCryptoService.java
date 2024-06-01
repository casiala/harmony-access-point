package eu.domibus.api.pki;

import eu.domibus.api.multitenancy.Domain;
import eu.domibus.api.multitenancy.DomainsAware;
import eu.domibus.api.security.TrustStoreEntry;
import org.apache.wss4j.common.crypto.CryptoType;
import org.apache.wss4j.common.ext.WSSecurityException;

import javax.security.auth.callback.CallbackHandler;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Cosmin Baciu
 * @since 4.0
 */
public interface MultiDomainCryptoService extends DomainsAware {

    X509Certificate[] getX509Certificates(Domain domain, CryptoType cryptoType) throws WSSecurityException;

    String getX509Identifier(Domain domain, X509Certificate cert) throws WSSecurityException;

    PrivateKey getPrivateKey(Domain domain, X509Certificate certificate, CallbackHandler callbackHandler) throws WSSecurityException;

    PrivateKey getPrivateKey(Domain domain, PublicKey publicKey, CallbackHandler callbackHandler) throws WSSecurityException;

    PrivateKey getPrivateKey(Domain domain, String identifier, String password) throws WSSecurityException;

    void verifyTrust(Domain domain, X509Certificate[] certs, boolean enableRevocation, Collection<Pattern> subjectCertConstraints, Collection<Pattern> issuerCertConstraints) throws WSSecurityException;

    void verifyTrust(Domain domain, PublicKey publicKey) throws WSSecurityException;

    String getDefaultX509Identifier(Domain domain) throws WSSecurityException;

    String getPrivateKeyPassword(Domain domain, String privateKeyAlias);

    void replaceTrustStore(Domain domain, KeyStoreContentInfo storeInfo);

    void replaceKeyStore(Domain domain, KeyStoreContentInfo storeInfo);

    KeyStore getKeyStore(Domain domain);

    KeyStore getTrustStore(Domain domain);

    boolean isCertificateChainValid(Domain domain, String alias) throws DomibusCertificateException;

    X509Certificate getCertificateFromKeystore(Domain domain, String senderName) throws KeyStoreException;

    boolean addCertificate(Domain domain, final X509Certificate certificate, final String alias, final boolean overwrite);

    void addCertificate(Domain domain, List<CertificateEntry> certificates, final boolean overwrite);

    X509Certificate getCertificateFromTruststore(Domain domain, String senderName) throws KeyStoreException;

    boolean removeCertificate(Domain domain, String alias);

    void removeCertificate(Domain domain, List<String> aliases);

    void reset(Domain domain);

    void resetKeyStore(Domain domain);

    List<TrustStoreEntry> getKeyStoreEntries(Domain domain);

    KeyStoreContentInfo getKeyStoreContent(Domain domain);

    List<TrustStoreEntry> getTrustStoreEntries(Domain domain);

    KeyStoreContentInfo getTrustStoreContent(Domain domain);

    void saveStoresFromDBToDisk();

    void resetTrustStore(Domain domain);

    void resetSecurityProfiles(Domain domain);

    boolean isTrustStoreChangedOnDisk(Domain currentDomain);

    boolean isKeyStoreChangedOnDisk(Domain currentDomain);

    String getTrustStoreFileExtension();

    void refreshTrustedLists(Domain domain);
}
