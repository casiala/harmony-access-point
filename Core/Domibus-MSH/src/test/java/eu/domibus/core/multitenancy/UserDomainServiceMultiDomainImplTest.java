package eu.domibus.core.multitenancy;

import eu.domibus.api.multitenancy.DomainTaskExecutor;
import eu.domibus.api.security.AuthUtils;
import eu.domibus.api.cache.DomibusLocalCacheService;
import eu.domibus.core.multitenancy.dao.UserDomainDao;
import mockit.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class UserDomainServiceMultiDomainImplTest {

    @Mock
    @Injectable
    protected DomainTaskExecutor domainTaskExecutor;

    @Injectable
    protected UserDomainDao userDomainDao;

    @Injectable
    protected DomibusLocalCacheService domibusLocalCacheService;

    @Injectable
    protected AuthUtils authUtils;

    @Tested
    UserDomainServiceMultiDomainImpl userDomainServiceMultiDomainImpl;

    @Captor
    ArgumentCaptor argCaptor;

    @Test
    public void testGetDomainForUser() throws Exception {
        String user = "user1";
        String domain = "domain1";

        new Expectations() {{
            userDomainDao.findDomain(user);
            result = domain;
        }};

        String mockResult = userDomainServiceMultiDomainImpl.getDomainForUser(user);
        String result = mockExecutorSubmit();

        assertEquals(result, domain);
    }

    @Test
    public void testGetPreferredDomainForUser() throws Exception {
        String user = "user1";
        String domain = "domain1";

        new Expectations() {{
            userDomainDao.findPreferredDomain(user);
            result = domain;
        }};

        String mockResult = userDomainServiceMultiDomainImpl.getPreferredDomainForUser(user);
        String result = mockExecutorSubmit();

        assertEquals(result, domain);
    }

    @Test
    public void setDomainForUser() {
        String user = "user1";
        String domainCode = "domain1";

        userDomainServiceMultiDomainImpl.setDomainForUser(user, domainCode);

        new Verifications() {{
            userDomainServiceMultiDomainImpl.executeInContext(() -> userDomainDao.updateOrCreateUserDomain(user, domainCode));
        }};
    }

    @Test
    public void setPreferredDomainForUser() {
        String user = "user1";
        String domainCode = "domain1";

        userDomainServiceMultiDomainImpl.setPreferredDomainForUser(user, domainCode);

        new Verifications() {{
            userDomainServiceMultiDomainImpl.executeInContext(() -> userDomainDao.updateOrCreateUserPreferredDomain(user, domainCode));
        }};
    }

    private <T> T mockExecutorSubmit() throws Exception {
        Mockito.verify(domainTaskExecutor).submit((Callable) argCaptor.capture());
        Callable<T> callable = (Callable<T>) argCaptor.getValue();
        return callable.call();
    }

}
