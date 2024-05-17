package eu.domibus.core.cache;

import eu.domibus.api.exceptions.DomibusCoreErrorCode;
import eu.domibus.api.exceptions.DomibusCoreException;
import eu.domibus.common.DomibusCacheConstants;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.spi.CachingProvider;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static eu.domibus.api.property.DomibusPropertyMetadataManagerSPI.DOMIBUS_CACHE_LOCATION;
import static eu.domibus.ext.services.DomibusPropertyManagerExt.PLUGINS_CONFIG_HOME;
import static eu.domibus.ext.services.DomibusPropertyManagerExt.EXTENSIONS_CONFIG_HOME;


/**
 * @author Cosmin Baciu
 * @author Catalin Enache
 * @since 4.0
 */
@Configuration
@EnableCaching
public class DomibusCacheConfiguration {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(DomibusCacheConfiguration.class);

    public static final String CONFIG_EHCACHE_EHCACHE_DEFAULT_XML = "config/ehcache/ehcache-default.xml";

    @Value("${domibus.config.location}/internal/ehcache.xml")
    protected String externalEhCacheFile;

    @Value("${domibus.config.location}/" + PLUGINS_CONFIG_HOME)
    protected String pluginsConfigLocation;

    @Value("${domibus.config.location}/" + EXTENSIONS_CONFIG_HOME)
    protected String extensionsConfigLocation;

    @Value("${" + DOMIBUS_CACHE_LOCATION + "}")
    private String cacheLocation;

    protected String defaultEhCacheFile = CONFIG_EHCACHE_EHCACHE_DEFAULT_XML;

    @Primary//in a cluster deployment, we have two cache managers(EhCache and Hazelcast); EhCache is the default cache manager
    @Bean(name = DomibusCacheConstants.CACHE_MANAGER)
    public org.springframework.cache.CacheManager cacheManager() throws Exception {
        System.setProperty(DOMIBUS_CACHE_LOCATION, cacheLocation);
        EhcacheCachingProvider provider = new EhcacheCachingProvider(); //NOSONAR : if this would be closed here (with try-with-resources or in a finally block), it would crash with IllegalStateException everywhere it'll be used further
        ClassLoader classLoader = getClass().getClassLoader();
        DomibusCacheRegionFactory.setBeanClassLoader(classLoader); // this is needed when using Ehcache caching provider for second level cache

        //default cache
        final ClassPathResource classPathResource = new ClassPathResource(defaultEhCacheFile);

        CacheManager cacheManager = provider.getCacheManager(
                classPathResource.getURL().toURI(),
                classLoader);

        //external cache file
        if (externalCacheFileExists()) {
            mergeExternalCacheConfiguration(provider, cacheManager);
        }

        //plugins
        addPluginsCacheConfiguration(provider, cacheManager, pluginsConfigLocation, extensionsConfigLocation);

        return new JCacheCacheManager(cacheManager);
    }

    protected boolean externalCacheFileExists() {
        return new File(externalEhCacheFile).exists();
    }

    /**
     * Get the configuration defined in the external ehcache.xml file and merge it into the default ehcache-default.xml configuration.
     * Any existing cache entry is overridden, otherwise a new cache entry is created.
     *
     * @param cacheManager
     */
    protected void mergeExternalCacheConfiguration(CachingProvider provider, CacheManager cacheManager) {
        LOG.debug("External ehCache file exists [{}]. Overriding the default ehCache configuration", externalEhCacheFile);

        //external cache file
        CacheManager cacheManagerExternal = provider.getCacheManager(
                new File(externalEhCacheFile).toURI(),
                getClass().getClassLoader());

        overridesDefaultCache(cacheManager, cacheManagerExternal);
        cacheManagerExternal.close();
    }

    /**
     * Adds plugins ehcache config files (both default-ehcache.xml and ehcache.xml)
     *
     * @param cacheManager Domibus core cache Manager
     * @param pluginsConfigLocation plugins config location
     */
    protected void addPluginsCacheConfiguration(CachingProvider cachingProvider, CacheManager cacheManager,
                                                final String pluginsConfigLocation, final String extensionsConfigLocation) {
        List<Resource> pluginDefaultEhcacheList = new ArrayList<>();
        pluginDefaultEhcacheList.addAll(readPluginEhcacheFiles("classpath*:config/ehcache/*-plugin-default-ehcache.xml"));
        pluginDefaultEhcacheList.addAll(readPluginEhcacheFiles("classpath*:config/ehcache/*-extension-default-ehcache.xml"));

        List<Resource> pluginEhcacheList = new ArrayList<>();
        pluginEhcacheList.addAll(readPluginEhcacheFiles("file:///" + pluginsConfigLocation + "/*-plugin-ehcache.xml"));
        pluginEhcacheList.addAll(readPluginEhcacheFiles("file:///" + extensionsConfigLocation + "/*-extension-ehcache.xml"));

        if (CollectionUtils.isEmpty(pluginDefaultEhcacheList) && CollectionUtils.isEmpty(pluginEhcacheList)) {
            LOG.debug("no ehcache config files found for plugins");
            return;
        }

        //create a new cache
        CacheManager cacheManagerPlugins = cachingProvider.getCacheManager();

        //default ehcache files
        pluginDefaultEhcacheList.forEach(resource -> applyPluginCacheConfig(cachingProvider, cacheManagerPlugins, resource));

        //ehcache files
        pluginEhcacheList.forEach(resource -> applyPluginCacheConfig(cachingProvider, cacheManagerPlugins, resource));

        //add to Domibus cache
        List<String> cacheNames =
                StreamSupport.stream(cacheManagerPlugins.getCacheNames().spliterator(), false)
                        .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(cacheNames)) {
            LOG.debug("no ehcache caches will be merged for plugins");
            return;
        }
        for (String cacheName : cacheNames) {
            if (cacheExists(cacheManager, cacheName)) {
                final String errorMessage = "Plugin cache \"" + cacheName + "\" already exists in Domibus";
                throw new DomibusCoreException(DomibusCoreErrorCode.DOM_001, errorMessage);
            }
            final javax.cache.configuration.Configuration config = cacheManagerPlugins.getCache(cacheName).getConfiguration(javax.cache.configuration.Configuration.class);
            cacheManager.createCache(cacheName, config);
        }
    }



    /**
     * Retrieves the location of default-ehcache.xml and ehcache.xml files
     *
     * @param locationPattern
     * @return {@code List<Resource>}
     */
    protected List<Resource> readPluginEhcacheFiles(String locationPattern) {
        List<Resource> resourceList = new ArrayList<>();

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            resourceList = Arrays.asList(resolver.getResources(locationPattern));
        } catch (IOException e) {
            LOG.error("Error while reading [{}] files", locationPattern, e);
        }
        return resourceList;
    }

    /**
     * Applies the plugin caches defined in either *plugin-default-ehcache.xml either *plugin-ehcache.xml file
     *
     * @param cacheManager Domibus core cacheManager
     * @param resource     new Resource file
     */
    protected void applyPluginCacheConfig(CachingProvider cachingProvider , @NotNull CacheManager cacheManager, @NotNull Resource resource) {
        final String configurationFileName = resource.getFilename();
        if (configurationFileName == null) {
            LOG.info("unable to add plugin ehcache config from [{}]", resource);
            return;
        }
        LOG.debug("Adding the following plugin ehcache file [{}]", configurationFileName);
        try {
            CacheManager pluginCacheManager = cachingProvider.getCacheManager(
                    resource.getURL().toURI(),
                    getClass().getClassLoader());
            overridesDefaultCache(cacheManager, pluginCacheManager);
            pluginCacheManager.close();
        } catch (IOException | URISyntaxException e) {
            LOG.error("Error while loading cache from [{}]", configurationFileName, e);
        }
    }

    /**
     * Overrides the caches found in defaultCacheManager by the caches from cacheManager
     *
     * @param defaultCacheManager
     * @param cacheManager
     */
    protected void overridesDefaultCache(@NotNull CacheManager defaultCacheManager, @NotNull CacheManager cacheManager) {
        for (String cacheName : cacheManager.getCacheNames()) {
            if (cacheExists(defaultCacheManager, cacheName)) {
                LOG.debug("Overriding cache [{}] in default cache manager", cacheName);
                defaultCacheManager.destroyCache(cacheName);
            }

            Cache cache = cacheManager.getCache(cacheName);
            javax.cache.configuration.Configuration config = cache.getConfiguration(javax.cache.configuration.Configuration.class);
            defaultCacheManager.createCache(cacheName, config);
            LOG.debug("Added [{}] into the default cache manager", cacheName);
        }
    }


    protected boolean cacheExists(CacheManager cacheManager, String cacheName) {
        List<String> cacheNames =
                StreamSupport.stream(cacheManager.getCacheNames().spliterator(), false)
                        .collect(Collectors.toList());

        return cacheNames.contains(cacheName);
    }
}
