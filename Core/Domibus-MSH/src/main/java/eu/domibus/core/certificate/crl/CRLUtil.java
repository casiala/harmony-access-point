package eu.domibus.core.certificate.crl;

import eu.domibus.api.util.HttpUtil;
import eu.domibus.common.DomibusCacheConstants;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.*;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.Callable;

import static eu.domibus.api.cache.DomibusLocalCacheService.CRL_BY_URL;

/**
 * Created by Cosmin Baciu on 11-Jul-16.
 */
@Service
public class CRLUtil {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(CRLUtil.class);

    /**
     * LDAP attribute for CRL
     */
    private static final String LDAP_CRL_ATTRIBUTE = "certificateRevocationList;binary";

    private final HttpUtil httpUtil;

    private final CacheManager cacheManager;

    public CRLUtil(HttpUtil httpUtil, @Qualifier(DomibusCacheConstants.CACHE_MANAGER) CacheManager cacheManager) {
        this.httpUtil = httpUtil;
        this.cacheManager = cacheManager;
    }

    public Object getCachedOrEvaluate(String cacheName, Object cacheKey, Callable<Object> valueProvider) {
        Cache cache = cacheManager.getCache(cacheName);
        if(cache != null) {
            if(LOG.isDebugEnabled()) {
                LOG.debug(String.format("Searching cache [{%s}] for key [{%s}]... [{%s}]", cacheName, cacheKey, cache.get(cacheKey) == null ? "not found" : "found"));
            }
            return cache.get(cacheKey, valueProvider);
        }
        try {
            if(LOG.isDebugEnabled()) {
                LOG.debug(String.format("No cache [{%s}] found", cacheName));
            }
            return valueProvider.call();
        } catch (Exception e) {
            throw new DomibusCRLException(e);
        }
    }

    /**
     * Entry point for downloading certificates from either http(s), classpath source or LDAP
     *
     * @param crlURL   the CRL url
     * @param useCache whether to use the CRL cache or not
     * @return {@link X509CRL} certificate to download
     * @throws DomibusCRLException runtime exception in case of error
     * @see CRLUtil#downloadCRLFromWebOrClasspath(String)
     * @see CRLUtil#downloadCRLfromLDAP(String)
     */
    public X509CRL downloadCRL(String crlURL, boolean useCache) throws DomibusCRLException {
        if(useCache){
            return (X509CRL) getCachedOrEvaluate(CRL_BY_URL, crlURL, () -> downloadCRL(crlURL));
        }
        return downloadCRL(crlURL);
    }

    private X509CRL downloadCRL(String crlURL) {
        if (CRLUrlType.LDAP.canHandleURL(crlURL)) {
            return downloadCRLfromLDAP(crlURL);
        } else {
            return downloadCRLFromWebOrClasspath(crlURL);
        }
    }

    /**
     * Downloads CRL from the given URL. Supports loading the crl using http, https, ftp based, classpath
     */
    protected X509CRL downloadCRLFromWebOrClasspath(String crlURL) throws DomibusCRLException {
        LOG.debug("Downloading CRL from url [{}]", crlURL);

        URL url;
        try {
            url = getCrlURL(crlURL);
        } catch (MalformedURLException e) {
            throw new DomibusCRLException(e);
        }

        if (url == null) {
            throw new DomibusCRLException("Could not get the CRL for distribution point [" + crlURL + "]");
        }

        try (InputStream crlStream = getCrlInputStream(url)) {
            LOG.debug("Downloaded [{}] [{}]", url, crlStream.available());
            CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
            return (X509CRL) cf.generateCRL(crlStream);
        } catch (final Exception exc) {
            throw new DomibusCRLException("Can not download CRL from pki distribution point: " + crlURL, exc);
        }
    }


    /**
     * Downloads CRL from an ldap:// address e.g. ldap://ldap.example.com/dc=identity-ca,dc=example,dc=com
     *
     * @param ldapURL ldap url address to download from
     * @return {@link X509CRL} the certificate
     * @throws DomibusCRLException runtime exception in case of error
     */
    X509CRL downloadCRLfromLDAP(String ldapURL) throws DomibusCRLException {
        LOG.debug("Downloading CRL from LDAP url [{}]", ldapURL);

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapURL);

        InputStream inStream = null;
        try {
            DirContext ctx = new InitialDirContext(env);
            Attributes attributes = ctx.getAttributes(StringUtils.EMPTY);
            Attribute attribute = attributes.get(LDAP_CRL_ATTRIBUTE);
            byte[] value = (byte[]) attribute.get();
            if ((value == null) || (value.length == 0)) {
                throw new DomibusCRLException("error downloading CRL from '" + ldapURL + "'");
            } else {
                inStream = new ByteArrayInputStream(value);
                CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
                return (X509CRL) cf.generateCRL(inStream);
            }
        } catch (NamingException | CertificateException | NoSuchProviderException | CRLException e) {
            throw new DomibusCRLException("Cannot download CRL from '" + ldapURL + "'", e);
        } finally {
            IOUtils.closeQuietly(inStream);
        }
    }

    public BigInteger parseCertificateSerial(String serial) {
        return new BigInteger(serial.trim().replaceAll("\\s", ""), 16);
    }

    protected InputStream getCrlInputStream(URL crlURL) throws IOException, NoSuchAlgorithmException, KeyManagementException {
        InputStream result;
        if (CRLUrlType.HTTP.canHandleURL(crlURL.toString()) || CRLUrlType.HTTPS.canHandleURL(crlURL.toString())) {
            result = httpUtil.downloadURL(crlURL.toString());
        } else {
            result = crlURL.openStream();
        }
        return result;
    }

    public URL getCrlURL(String crlURL) throws MalformedURLException {
        return CRLUrlType.isURLSupported(crlURL) ? new URL(crlURL) : getResourceFromClasspath(crlURL);
    }

    public URL getResourceFromClasspath(String url) {
        return Thread.currentThread().getContextClassLoader().getResource(url);
    }

    /**
     * Extracts all CRL distribution point URLs from the "CRL Distribution Point" extension of X.509 pki.
     * If the CRL distribution point extension is unavailable, returns an empty list.
     *
     * @param cert a X509 certificate
     * @return the list of CRL urls of this certificate
     */
    public List<String> getCrlDistributionPoints(X509Certificate cert) {
        byte[] crldpExt = cert.getExtensionValue(org.bouncycastle.asn1.x509.Extension.cRLDistributionPoints.getId());
        if (crldpExt == null) {
            return new ArrayList<>();
        }

        ASN1Primitive derObjCrlDP = null;
        try (ASN1InputStream oAsnInStream = new ASN1InputStream(new ByteArrayInputStream(crldpExt))) {
            derObjCrlDP = oAsnInStream.readObject();
        } catch (IOException e) {
            throw new DomibusCRLException("Error while extracting CRL distribution point URLs", e);
        }

        DEROctetString dosCrlDP = (DEROctetString) derObjCrlDP;
        byte[] crldpExtOctets = dosCrlDP.getOctets();

        ASN1Primitive derObj2 = null;
        try (ASN1InputStream oAsnInStream2 = new ASN1InputStream(new ByteArrayInputStream(crldpExtOctets))) {
            derObj2 = oAsnInStream2.readObject();
        } catch (IOException e) {
            throw new DomibusCRLException("Error while extracting CRL distribution point URLs", e);
        }

        CRLDistPoint distPoint = CRLDistPoint.getInstance(derObj2);
        List<String> crlUrls = new ArrayList<>();
        for (DistributionPoint dp : distPoint.getDistributionPoints()) {
            DistributionPointName dpn = dp.getDistributionPoint();
            // Look for URIs in fullName
            if (dpn != null && dpn.getType() == DistributionPointName.FULL_NAME) {
                GeneralName[] genNames = GeneralNames.getInstance(dpn.getName()).getNames();
                // Look for an URI
                for (int index = 0; index < genNames.length; index++) {
                    if (genNames[index].getTagNo() == GeneralName.uniformResourceIdentifier) {
                        String url = DERIA5String.getInstance(genNames[index].getName()).getString();
                        crlUrls.add(url);
                    }
                }
            }
        }
        return crlUrls;
    }
}