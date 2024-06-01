package eu.domibus.security;

import eu.domibus.api.property.DomibusPropertyMetadataManagerSPI;
import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.test.AbstractIT;
import eu.domibus.api.exceptions.DomibusCoreException;
import eu.domibus.api.multitenancy.DomainContextProvider;
import eu.domibus.api.user.UserManagementException;
import eu.domibus.common.JPAConstants;
import eu.domibus.core.user.ui.User;
import eu.domibus.core.user.ui.UserDao;
import eu.domibus.core.user.ui.UserRole;
import eu.domibus.core.user.ui.UserRoleDao;
import eu.domibus.core.user.ui.security.ConsoleUserSecurityPolicyManager;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * @author Ion Perpegel
 * @since 4.1
 */
@Transactional
public class ConsoleUserSecurityPolicyManagerTestIT extends AbstractIT {

    @Autowired
    ConsoleUserSecurityPolicyManager userSecurityPolicyManager;

    @Autowired
    private UserRoleDao userRoleDao;

    @Autowired
    protected UserDao userDao;

    @Autowired
    DomibusPropertyProvider domibusPropertyProvider;

    @Autowired
    protected DomainContextProvider domainContextProvider;

    @PersistenceContext(unitName = JPAConstants.PERSISTENCE_UNIT_NAME)
    protected EntityManager entityManager;


    private User initTestUser(String userName) {
        UserRole userRole = userRoleDao.findByName("ROLE_USER");
        if (userRole == null) {
            userRole = new UserRole("ROLE_USER");
            entityManager.persist(userRole);
        }
        User user = new User();
        user.setUserName(userName);
        user.setPassword("Password-0");
        user.addRole(userRole);
        user.setEmail("test@mailinator.com");
        user.setActive(true);
        userDao.create(user);
        return user;
    }

    @Test
    @Transactional
    @Rollback
    public void testPasswordReusePolicy_shouldPass() {
        User user = initTestUser("testUser1");
        userSecurityPolicyManager.changePassword(user, "Password-1111111");
        userSecurityPolicyManager.changePassword(user, "Password-2222222");
        userSecurityPolicyManager.changePassword(user, "Password-3333333");
        userSecurityPolicyManager.changePassword(user, "Password-4444444");
        userSecurityPolicyManager.changePassword(user, "Password-5555555");
        userSecurityPolicyManager.changePassword(user, "Password-6666666");
        userSecurityPolicyManager.changePassword(user, "Password-1111111");
    }

    @Test(expected = DomibusCoreException.class)
    @Transactional
    @Rollback
    public void testPasswordReusePolicy_shouldFail() {
        User user = initTestUser("testUser2");
        userSecurityPolicyManager.changePassword(user, "Password-1111111");
        userSecurityPolicyManager.changePassword(user, "Password-2222222");
        userSecurityPolicyManager.changePassword(user, "Password-3333333");
        userSecurityPolicyManager.changePassword(user, "Password-4444444");
        userSecurityPolicyManager.changePassword(user, "Password-5555555");
        userSecurityPolicyManager.changePassword(user, "Password-1111111");
    }

    @Test(expected = DomibusCoreException.class)
    @Transactional
    @Rollback
    public void testPasswordComplexity_blankPasswordShouldFail() {
        User user = initTestUser("testUser3");
        userSecurityPolicyManager.changePassword(user, "");
    }

    @Test(expected = DomibusCoreException.class)
    @Transactional
    @Rollback
    public void testPasswordComplexity_shortPasswordShouldFail() {

        final String initialValue = domibusPropertyProvider.getProperty(DomibusPropertyMetadataManagerSPI.DOMIBUS_PASSWORD_POLICY_PATTERN);

        try {
            domibusPropertyProvider.setProperty(DomibusPropertyMetadataManagerSPI.DOMIBUS_PASSWORD_POLICY_PATTERN, "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[~`!@#$%^&+=\\\\-_<>.,?:;*/()|\\\\[\\\\]{}'\"\\\\\\\\]).{16,32}$");
            User user = initTestUser("testUser4");
            userSecurityPolicyManager.changePassword(user, "Aa-1");
        } finally {
            domibusPropertyProvider.setProperty(DomibusPropertyMetadataManagerSPI.DOMIBUS_PASSWORD_POLICY_PATTERN, initialValue);
        }


    }

    @Test(expected = UserManagementException.class)
    @Transactional
    @Rollback
    public void test_validateUniqueUser() {
        User user = initTestUser("testUser_Unique");
        userSecurityPolicyManager.validateUniqueUser(user);
    }
}
