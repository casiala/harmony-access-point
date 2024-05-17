
package eu.domibus.core.ebms3.sender.client;

import eu.domibus.api.cache.DomibusLocalCacheService;
import eu.domibus.api.cxf.TLSReaderService;
import eu.domibus.api.pki.DomibusCertificateException;
import eu.domibus.api.property.DomibusConfigurationService;
import eu.domibus.common.DomibusCacheConstants;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.common.jaxb.JAXBContextCache;
import org.apache.cxf.common.jaxb.JAXBUtils;
import org.apache.cxf.common.util.PackageUtils;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.configuration.jsse.TLSClientParametersConfig;
import org.apache.cxf.configuration.security.KeyStoreType;
import org.apache.cxf.configuration.security.TLSClientParametersType;
import org.apache.cxf.staxutils.StaxUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static eu.domibus.ext.services.DomibusPropertyManagerExt.DOMAINS_HOME;

/**
 * @author Christian Koch, Stefan Mueller
 * @author Ion Perpegel
 */
@Service
public class TLSReaderServiceImpl implements TLSReaderService {
    public static final String CLIENT_AUTHENTICATION_XML = "clientauthentication.xml";

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(TLSReaderServiceImpl.class);

    public static final String REGEX_DOMIBUS_CONFIG_LOCATION = "\\Q${domibus.config.location}\\E";

    private static final String TLS_CACHE = "tlsCache";

    private Set<Class<?>> classes;

    private JAXBContext context;

    private final DomibusConfigurationService domibusConfigurationService;

    private final DomibusLocalCacheService domibusLocalCacheService;

    public TLSReaderServiceImpl(DomibusConfigurationService domibusConfigurationService, DomibusLocalCacheService domibusLocalCacheService) {
        this.domibusConfigurationService = domibusConfigurationService;
        this.domibusLocalCacheService = domibusLocalCacheService;
    }

    @Cacheable(cacheManager = DomibusCacheConstants.CACHE_MANAGER, value = TLS_CACHE, key = "#domainCode")
    @Override
    public TLSClientParameters getTlsClientParameters(String domainCode) {
        Optional<Path> path = getClientAuthenticationPath(domainCode);
        if (!path.isPresent()) {
            LOG.info("Could not load TLS Client parameters for domain [{}] since theere is no clientauthentication.xml file", domainCode);
            return null;
        }
        try {
            String fileContent = getFileContent(path);
            return (TLSClientParameters) TLSClientParametersConfig.createTLSClientParameters(fileContent);
        } catch (Exception e) {
            LOG.warn("Mutual authentication will not be supported for [{}]", path);
            LOG.trace("", e);
            return null;
        }
    }

    @Override
    @CacheEvict(value = TLS_CACHE, key = "#domainCode")
    public void reset(String domainCode) {
        LOG.trace("Evicting the TLS and dispatchClient caches.");
        this.domibusLocalCacheService.clearCache(DomibusLocalCacheService.DISPATCH_CLIENT);
    }

    @Override
    public Optional<TLSClientParametersType> getTlsTrustStoreConfiguration(String domainCode) {
        Optional<Path> path = getClientAuthenticationPath(domainCode);
        if (!path.isPresent()) {
            LOG.debug("The client authentication xml path for domain [{}] could not be found.", domainCode);
            return Optional.empty();
        }
        try {
            String fileContent = getFileContent(path);
            TLSClientParametersType params = getTLSParameters(fileContent);
            return Optional.of(params);
        } catch (Exception e) {
            throw new DomibusCertificateException("Could not process client authentication file for domain [" + domainCode + "]", e);
        }
    }

    private String getFileContent(Optional<Path> path) throws IOException {
        byte[] encoded = Files.readAllBytes(path.orElse(null));
        String config = new String(encoded, StandardCharsets.UTF_8);
        config = config.replaceAll(REGEX_DOMIBUS_CONFIG_LOCATION, domibusConfigurationService.getConfigLocation().replace('\\', '/'));
        return config;
    }

    /**
     * <p>Returns the path to the file that contains the TLS client configuration parameters.</p><br />
     *
     * <p>The file is searched in the Domibus configuration location under a file named {@value #CLIENT_AUTHENTICATION_XML} by default. The first lookup happens by prefixing
     * this default name with the domain code followed by an underscore character. If the file having this name does not exist, another lookup happens by using just the default
     * name, without any prefixes. An optional path is returned when neither of these two files is found.</p>
     *
     * @param domainCode The domain code used to prefix the default file name during the first lookup.
     * @return the path to an existing domain specific client authentication file or the path to an existing default client authentication file; otherwise, an empty
     * {@code Optional} path.
     */
    protected Optional<Path> getClientAuthenticationPath(String domainCode) {
        if (domainCode != null) {
            String domainSpecificFileName = StringUtils.stripToEmpty(domainCode) + "_" + CLIENT_AUTHENTICATION_XML;
            Path domainSpecificPath = Paths.get(domibusConfigurationService.getConfigLocation(), DOMAINS_HOME, domainCode, domainSpecificFileName);
            boolean domainSpecificPathExists = Files.exists(domainSpecificPath);
            LOG.debug("Client authentication file [{}] at the domain specific path [{}] exists [{}]", domainSpecificFileName, domainSpecificPath, domainSpecificPathExists);
            if (domainSpecificPathExists) {
                return Optional.of(domainSpecificPath);
            }
        }

        Path defaultPath = Paths.get(domibusConfigurationService.getConfigLocation(), CLIENT_AUTHENTICATION_XML);
        boolean defaultPathExists = Files.exists(defaultPath);
        LOG.debug("Client authentication file at the default path [{}] exists [{}]", defaultPath, defaultPathExists);
        if (defaultPathExists) {
            return Optional.of(defaultPath);
        }

        return Optional.empty();
    }

    private void persistTLSParameters(TLSClientParametersType tls, Path path) throws JAXBException, FileNotFoundException {
        JAXBContext jaxbContext = JAXBContext.newInstance(TLSClientParametersType.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

        //If we DO NOT have JAXB annotated class
        JAXBElement<TLSClientParametersType> jaxbElement =
                new JAXBElement<TLSClientParametersType>(
                        new QName("http://cxf.apache.org/transports/http/configuration", "tlsClientParameters", "http-conf"),
                        TLSClientParametersType.class,
                        tls);

        jaxbMarshaller.marshal(jaxbElement, new FileOutputStream(path.toString()));
    }

    private TLSClientParametersType getTLSParameters(String s) throws XMLStreamException, JAXBException {
        StringReader reader = new StringReader(s);
        XMLStreamReader data = StaxUtils.createXMLStreamReader(reader);

        try {
            JAXBElement<TLSClientParametersType> type = JAXBUtils.unmarshall(getContext(), data, TLSClientParametersType.class);
            return type.getValue();
        } finally {
            StaxUtils.close(data);
        }
    }

    private synchronized JAXBContext getContext() throws JAXBException {
        if (context == null || classes == null) {
            Set<Class<?>> c2 = new HashSet();
            JAXBContextCache.addPackage(c2, PackageUtils.getPackageName(TLSClientParametersType.class), TLSClientParametersConfig.class.getClassLoader());
            JAXBContextCache.CachedContextAndSchemas ccs = JAXBContextCache.getCachedContextAndSchemas(c2, (String) null, (Map) null, (Collection) null, false);
            classes = ccs.getClasses();
            context = ccs.getContext();
        }

        return context;
    }
}
