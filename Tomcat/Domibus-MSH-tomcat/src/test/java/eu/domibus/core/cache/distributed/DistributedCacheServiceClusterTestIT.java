package eu.domibus.core.cache.distributed;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import eu.domibus.test.AbstractIT;
import eu.domibus.api.cache.DomibusCacheException;
import eu.domibus.api.cache.distributed.DistributedCacheService;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@TestPropertySource(properties = {"domibus.deployment.clustered=true"})
public class DistributedCacheServiceClusterTestIT extends AbstractIT {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(DistributedCacheServiceClusterTestIT.class);

    @Autowired
    DistributedCacheService distributedCacheService;

    @Autowired
    HazelcastInstance hazelcastInstance;

    @Before
    public void beforeTest() {
        distributedCacheService.getDistributedCacheNames().stream().forEach(mapName -> hazelcastInstance.getMap(mapName).destroy());
    }

    @Test
    public void testCreateCache() {
        distributedCacheService.createCache("mycache1");
        distributedCacheService.createCache("mycache2", 1, 2, 3);
        distributedCacheService.createCache("mycache3", 1, 1, 1, 1, 1, 1);

        //in a cluster deployment, distributed cache is created
        assertNotNull(hazelcastInstance);
    }


    @Test
    public void addEntryWhenCacheIsAlreadyExisting() {
        final int cacheSize = 10;
        final int timeToLiveSeconds = 60;
        final int maxIdleSeconds = 100;
        final int nearCacheSize = 2000;
        final int nearCacheTimeToLiveSeconds = 120;
        final int nearCacheMaxIdleSeconds = 50;
        distributedCacheService.createCache("myCustomCache3", cacheSize, timeToLiveSeconds, maxIdleSeconds, nearCacheSize, nearCacheTimeToLiveSeconds, nearCacheMaxIdleSeconds);

        distributedCacheService.addEntryInCache("myCustomCache3", "mykey", "myvalue");
    }

    @Test
    public void addEntryWhenCacheDoesNotExists() {
        try {
            distributedCacheService.addEntryInCache("myCustomCache1", "mykey", "myvalue");
            fail("Adding to cache should have failed due to not existing cache");
        } catch (DomibusCacheException e) {
            final String message = "Normal exception when adding entries to a cache which was not previously created";
            LOG.info(message);
            LOG.trace(message, e);
        }
    }

    @Test
    public void evictEntryFromCache() {
        final String cacheName = "myCustomCache4";
        distributedCacheService.createCache(cacheName);

        final String key = "mykey";
        final String value = "myvalue";
        distributedCacheService.addEntryInCache(cacheName, key, value);
        final Object entryFromCache = distributedCacheService.getEntryFromCache(cacheName, key);
        assertEquals(value, entryFromCache);
        distributedCacheService.evictEntryFromCache(cacheName, key);
        assertNull(distributedCacheService.getEntryFromCache(cacheName, key));
    }

    @Test
    public void getCacheNames() {
        distributedCacheService.createCache("cacheName1");
        distributedCacheService.createCache("cacheName2");
        final List<String> distributedCacheNames = distributedCacheService.getDistributedCacheNames();
        assertNotNull(distributedCacheNames);
        assertEquals(2, distributedCacheNames.size());
    }

    @Test
    public void getCacheNamesDuplicate() {
        distributedCacheService.createCache("cacheName1");
        distributedCacheService.createCache("cacheName1");
        final List<String> distributedCacheNames = distributedCacheService.getDistributedCacheNames();
        assertNotNull(distributedCacheNames);
        assertEquals(1, distributedCacheNames.size());
    }

    @Test
    public void getEntriesFromCache() {
        final String cacheName = "entries1";
        distributedCacheService.createCache(cacheName);
        distributedCacheService.addEntryInCache(cacheName, "key11", "value11");
        final Map<String, Object> entriesFromCache = distributedCacheService.getEntriesFromCache(cacheName);
        assertNotNull(entriesFromCache);
        assertEquals(1, entriesFromCache.size());
        assertEquals("value11", entriesFromCache.get("key11"));
    }

    @Test
    public void testNearCacheIsUsed() {
        final String cacheName = "nearCache1";
        distributedCacheService.createCache(cacheName);
        final String key = "key11";
        distributedCacheService.addEntryInCache(cacheName, key, "value11");
        //the first time we get the value it's added in the near cache
        final Object entryFromCache = distributedCacheService.getEntryFromCache(cacheName, key);
        assertNotNull(entryFromCache);
        assertEquals("value11", entryFromCache);

        //the value is retrieved from the near cache
        distributedCacheService.getEntryFromCache(cacheName, key);
        distributedCacheService.getEntryFromCache(cacheName, key);
        distributedCacheService.getEntryFromCache(cacheName, key);


        IMap<String, Object> iMap = hazelcastInstance.getMap(cacheName);
        final long nearCacheHits = iMap.getLocalMapStats().getNearCacheStats().getHits();
        assertTrue(nearCacheHits == 3);
    }
}
