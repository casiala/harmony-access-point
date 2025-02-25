package eu.domibus.core.crypto.api;

import eu.domibus.api.crypto.CryptoException;
import eu.domibus.api.pki.CertificateEntry;
import eu.domibus.api.pki.DomibusCertificateException;
import eu.domibus.api.pki.KeyStoreContentInfo;
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
 * New exceptions thrown in here have to be handled in eu.domibus.core.crypto.DomainCryptoServiceInterceptor
 *
 * @author Cosmin Baciu
 * @since 4.0
 */
public interface DomainCryptoService {

    /* START - Methods required to be implemented by the org.apache.wss4j.common.crypto.CryptoBase */
    X509Certificate[] getX509Certificates(CryptoType cryptoType) throws WSSecurityException;

    String getX509Identifier(X509Certificate cert) throws WSSecurityException;

    PrivateKey getPrivateKey(X509Certificate certificate, CallbackHandler callbackHandler) throws WSSecurityException;

    PrivateKey getPrivateKey(PublicKey publicKey, CallbackHandler callbackHandler) throws WSSecurityException;

    PrivateKey getPrivateKey(String identifier, String password) throws WSSecurityException;

    void verifyTrust(PublicKey publicKey) throws WSSecurityException;

    void verifyTrust(X509Certificate[] certs, boolean enableRevocation, Collection<Pattern> subjectCertConstraints, Collection<Pattern> issuerCertConstraints) throws WSSecurityException;

    String getDefaultX509Identifier() throws WSSecurityException;
    /* END - Methods required to be implemented by the org.apache.wss4j.common.crypto.CryptoBase */

    String getPrivateKeyPassword(String alias);

    void replaceTrustStore(KeyStoreContentInfo storeInfo);

    KeyStore getKeyStore();

    KeyStore getTrustStore();

    X509Certificate getCertificateFromKeyStore(String alias) throws KeyStoreException;

    boolean isCertificateChainValid(String alias) throws DomibusCertificateException;

    boolean addCertificate(X509Certificate certificate, String alias, boolean overwrite);

    void addCertificate(List<CertificateEntry> certificates, boolean overwrite);

    X509Certificate getCertificateFromTrustStore(String alias) throws KeyStoreException;

    boolean removeCertificate(String alias);

    void removeCertificate(List<String> aliases);

    List<TrustStoreEntry> getKeyStoreEntries();

    KeyStoreContentInfo getKeyStoreContent();

    List<TrustStoreEntry> getTrustStoreEntries();

    KeyStoreContentInfo getTrustStoreContent();

    void replaceKeyStore(KeyStoreContentInfo storeInfo);

    void resetKeyStore();

    void resetTrustStore();

    void resetStores();

    void resetSecurityProfiles();

    boolean isTrustStoreChangedOnDisk();

    boolean isKeyStoreChangedOnDisk();

    void refreshTrustedLists();
}
