package eu.domibus.weblogic.security;


import eu.domibus.api.multitenancy.Domain;
import eu.domibus.api.multitenancy.DomainContextProvider;
import eu.domibus.api.multitenancy.DomainService;
import eu.domibus.api.property.DomibusConfigurationService;
import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.api.security.AuthRole;
import eu.domibus.api.security.DomibusUserDetails;
import eu.domibus.api.user.AtLeastOneDomainException;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.web.security.DomibusUserDetailsImpl;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;

import javax.security.auth.Subject;
import java.lang.reflect.InvocationTargetException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * {@link UserDetailsService} implementation for ECAS
 *
 * @author Catalin Enache
 * @since 4.1
 */
@Service
public class ECASUserDetailsService implements AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken>, UserDetailsService {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(ECASUserDetailsService.class);

    private static final String WEBLOGIC_SECURITY_CLASS = "weblogic.security.Security";

    private static final String WEBLOGIC_SECURITY_GET_METHOD = "getCurrentSubject";

    private static final String ECAS_USER = "eu.cec.digit.ecas.client.j2ee.weblogic.EcasUser";

    private static final String ECAS_GROUP = "eu.cec.digit.ecas.client.j2ee.weblogic.EcasGroup";

    public static final String ECAS_DOMIBUS_LDAP_GROUP_PREFIX_KEY = "domibus.security.ext.auth.provider.group.prefix";
    public static final String ECAS_DOMIBUS_USER_ROLE_MAPPINGS_KEY = "domibus.security.ext.auth.provider.user.role.mappings";
    public static final String ECAS_DOMIBUS_DOMAIN_MAPPINGS_KEY = "domibus.security.ext.auth.provider.domain.mappings";

    private static final String ECAS_DOMIBUS_MAPPING_PAIR_SEPARATOR = ";";
    private static final String ECAS_DOMIBUS_MAPPING_VALUE_SEPARATOR = "=";

    @Autowired
    private DomainService domainService;

    @Autowired
    private DomibusConfigurationService domibusConfigurationService;

    @Autowired
    private DomainContextProvider domainContextProvider;

    @Autowired
    private DomibusPropertyProvider domibusPropertyProvider;

    @Override
    public DomibusUserDetails loadUserDetails(PreAuthenticatedAuthenticationToken preAuthenticatedAuthenticationToken) throws UsernameNotFoundException {
        DomibusUserDetails userDetails = loadUserByUsername((String) preAuthenticatedAuthenticationToken.getPrincipal());
        LOG.debug("DomibusUserDetails username={}", userDetails.getUsername());
        return userDetails;
    }

    @Override
    public DomibusUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LOG.debug("loadUserByUsername - start");
        if (isWeblogicSecurity()) {
            DomibusUserDetails userDetails;
            try {
                userDetails = createUserDetails(username);
            } catch (Exception ex) {
                LOG.error("error during loadUserByUserName", ex);
                throw new UsernameNotFoundException("Cannot retrieve the user's details", ex);
            }
            validateDomains(userDetails);
            return userDetails;
        }

        throw new UsernameNotFoundException("Cannot find any user who has the name " + username);
    }

    /**
     * It reads the principals (LDAP groups) returned by ECAS and create UserDetails
     *
     * @param username
     * @return DomibusUserDetails object
     * @throws InvocationTargetException
     * @throws NoSuchMethodException
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     */
    protected DomibusUserDetails createUserDetails(final String username) throws InvocationTargetException, NoSuchMethodException, ClassNotFoundException, IllegalAccessException {
        LOG.debug("createUserDetails - start");
        List<AuthRole> authRoles = new LinkedList<>();
        Set<String> domainCodesFromLDAP = new HashSet<>();

        final String ldapGroupPrefix = domibusPropertyProvider.getProperty(ECAS_DOMIBUS_LDAP_GROUP_PREFIX_KEY);
        LOG.debug("createUserDetails - LDAP group prefix is: {}", ldapGroupPrefix);

        final Map<String, AuthRole> userRoleMappings = retrieveUserRoleMappings();
        final Map<String, String> domainMappings = retrieveDomainMappings();

        //extract user role and domain
        for (Principal principal : getPrincipals()) {
            LOG.debug("createUserDetails - principal name: {} and class: {}", principal.getName(), principal.getClass().getName());
            if (isUserGroupPrincipal(principal)) {
                LOG.debug("Found a user group principal: {}", principal);
                final String principalName = principal.getName();

                //only Domibus mapped ldap groups
                if (principalName.startsWith(ldapGroupPrefix)) {
                    //search for user roles
                    if (userRoleMappings.get(principalName) != null) {
                        authRoles.add(userRoleMappings.get(principalName));
                        LOG.debug("createUserDetails - userGroup added: {}", userRoleMappings.get(principalName));
                    } else if (domainMappings.get(principalName) != null) {
                        domainCodesFromLDAP.add(domainMappings.get(principalName));
                        LOG.debug("createUserDetails - domain added: {}", domainCodesFromLDAP);
                    }
                }
            } else {
                LOG.debug("createUserDetails - user group is not principal");
                if (isUserPrincipal(principal) && !username.equals(principal.getName())) {
                    LOG.error("Username {} does not match Principal {}", username, principal.getName());
                    throw new AccessDeniedException(
                            String.format("The provided username and the principal name do not match. username = %s, principal = %s", username, principal.getName()));
                }
            }
        }

        final GrantedAuthority highestAuthority = chooseHighestUserGroup(authRoles);
        final Domain domain = getFirstDomain(domainCodesFromLDAP);
        final Set<String> availableDomainCodes = getAvailableDomainCodes(domainCodesFromLDAP, highestAuthority);
        final List<GrantedAuthority> authorities = validateHighestAuthority(highestAuthority, domain);
        final String domainCode = getDomainCode(domain);

        DomibusUserDetails domibusUserDetails = new DomibusUserDetailsImpl(username, StringUtils.EMPTY, authorities);
        domibusUserDetails.setAvailableDomainCodes(availableDomainCodes);
        domibusUserDetails.setDefaultPasswordUsed(false);
        domibusUserDetails.setExternalAuthProvider(true);
        domibusUserDetails.setDaysTillExpiration(Integer.MAX_VALUE);
        domibusUserDetails.setDomain(domainCode);

        domainContextProvider.clearCurrentDomain();
        domainContextProvider.setCurrentDomainWithValidation(domainCode);

        LOG.debug("createUserDetails - end");
        return domibusUserDetails;
    }

    protected GrantedAuthority chooseHighestUserGroup(final List<AuthRole> userGroups) {
        SimpleGrantedAuthority simpleGrantedAuthority = null;
        if (userGroups.contains(AuthRole.ROLE_AP_ADMIN)) {
            simpleGrantedAuthority = new SimpleGrantedAuthority(AuthRole.ROLE_AP_ADMIN.name());
        } else if (userGroups.contains(AuthRole.ROLE_ADMIN)) {
            simpleGrantedAuthority = new SimpleGrantedAuthority(AuthRole.ROLE_ADMIN.name());
        } else if (userGroups.contains(AuthRole.ROLE_USER)) {
            simpleGrantedAuthority = new SimpleGrantedAuthority(AuthRole.ROLE_USER.name());
        }
        LOG.info("highest role is [{}]", simpleGrantedAuthority != null ? simpleGrantedAuthority : StringUtils.EMPTY);
        return simpleGrantedAuthority;
    }

    protected Domain getFirstDomain(Set<String> domainCodesFromLDAP) {
        if (domibusConfigurationService.isSingleTenantAware()) {
            LOG.info("assigned single tenancy default domain");
            return DomainService.DEFAULT_DOMAIN;
        }

        final String[] domainCodesFromLDAPArray = domainCodesFromLDAP.toArray(new String[0]);
        Domain defaultDomain = domainService.getDomains().stream()
                .filter(domain -> StringUtils.equalsAny(domain.getCode(), domainCodesFromLDAPArray))
                .findFirst()
                .orElse(null);
        LOG.info("assigned multitenancy default domain is [{}]", defaultDomain);
        return defaultDomain;
    }

    protected Set<String> getAvailableDomainCodes(Set<String> domainCodesFromLDAP, GrantedAuthority highestAuthority) {
        if (domibusConfigurationService.isMultiTenantAware() && hasSuperAdminUserPrivilege(highestAuthority)) {
            LOG.info("Adding all available domain codes to the super admin user in multitenancy");
            return domainService.getDomains()
                    .stream()
                    .map(Domain::getCode)
                    .collect(Collectors.toSet());
        }

        final String[] domainCodesFromLDAPArray = domainCodesFromLDAP.toArray(new String[0]);
        Set<String> availableDomainCodes = domainService.getDomains().stream()
                .map(Domain::getCode)
                .filter(code -> StringUtils.equalsAny(code, domainCodesFromLDAPArray))
                .collect(Collectors.toSet());

        LOG.info("userDetail availableDomainCodes={}", availableDomainCodes);
        return availableDomainCodes;
    }

    protected List<GrantedAuthority> validateHighestAuthority(GrantedAuthority applicableAuthority, Domain domain) {
        List<GrantedAuthority> authorities = Collections.emptyList();
        if (applicableAuthority != null) {
            if (hasSuperAdminUserPrivilege(applicableAuthority)) {
                if (domibusConfigurationService.isMultiTenantAware()) {
                    //we set the groups only if the privilege is that of a super admin user in a multitenancy scenario
                    LOG.debug("granted role is [{}]", applicableAuthority.getAuthority());
                    authorities = Collections.singletonList(applicableAuthority);
                } else {
                    LOG.warn("User has the super admin role but Domibus is not currently running in multitenancy mode");
                }
            } else if (domain != null) {
                //we set the groups only if the LDAP groups are mapping on both privileges and domain code
                LOG.debug("granted role is [{}]", applicableAuthority.getAuthority());
                authorities = Collections.singletonList(applicableAuthority);
            }
        }
        LOG.info("userDetail authorities={}", authorities);
        return authorities;
    }

    private String getDomainCode(Domain domain) {
        //for multitenancy we still set domain to DEFAULT even if there is no matching LDAP group
        final String domainCode = domain != null ? domain.getCode() : DomainService.DEFAULT_DOMAIN.getCode();
        LOG.info("createUserDetails - domain code set to [{}]", domainCode);
        return domainCode;
    }

    private boolean hasSuperAdminUserPrivilege(GrantedAuthority grantedAuthority) {
        return StringUtils.equals(grantedAuthority.getAuthority(), AuthRole.ROLE_AP_ADMIN.name());
    }

    protected boolean isWeblogicSecurity() {
        boolean weblogicSecurityLoaded = false;
        try {
            Class.forName(WEBLOGIC_SECURITY_CLASS, false, getClass().getClassLoader());
            weblogicSecurityLoaded = true;
        } catch (ClassNotFoundException e) {
            // Do nothing. It can happen when an app does not use a Weblogic security provider.
            LOG.error("Error loading a Weblogic Security class", e);
        }
        return weblogicSecurityLoaded;
    }

    protected Set<Principal> getPrincipals()
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, ClassNotFoundException {
        Subject subject = (Subject) Class.forName(WEBLOGIC_SECURITY_CLASS)
                .getMethod(WEBLOGIC_SECURITY_GET_METHOD, null)
                .invoke(null, null);
        return subject.getPrincipals();
    }

    private boolean isUserPrincipal(Principal principal) throws ClassNotFoundException {
        LOG.debug("isUserPrincipal class={}", principal.getClass().getName());
        return Class.forName(ECAS_USER).isInstance(principal);
    }

    protected boolean isUserGroupPrincipal(Principal principal) throws ClassNotFoundException {
        LOG.debug("isUserGroupPrincipal class={}", principal.getClass().getName());
        return Class.forName(ECAS_GROUP).isInstance(principal);
    }

    /**
     * @return Map of Domibus user roles and LDAP EU Login groups
     */
    protected Map<String, AuthRole> retrieveUserRoleMappings() {
        final String userRoleMappings = domibusPropertyProvider.getProperty(ECAS_DOMIBUS_USER_ROLE_MAPPINGS_KEY);
        if (StringUtils.isEmpty(userRoleMappings)) {
            throw new IllegalArgumentException("Domibus user role mappings to LDAP groups could not be empty");
        }
        LOG.debug("reading user role mappings property [{}] = [{}]", ECAS_DOMIBUS_USER_ROLE_MAPPINGS_KEY, userRoleMappings);

        return Stream.of(userRoleMappings.split(ECAS_DOMIBUS_MAPPING_PAIR_SEPARATOR))
                .map(str -> str.split(ECAS_DOMIBUS_MAPPING_VALUE_SEPARATOR))
                .collect(Collectors.toMap(str -> str[0], str -> AuthRole.valueOf(str[1])));
    }

    /**
     * @return Map of Domibus domains and LDAP EU Login groups
     */
    protected Map<String, String> retrieveDomainMappings() {
        final String domainMappings = domibusPropertyProvider.getProperty(ECAS_DOMIBUS_DOMAIN_MAPPINGS_KEY);
        if (StringUtils.isEmpty(domainMappings)) {
            throw new IllegalArgumentException("Domibus domain mappings to LDAP groups could not be empty");
        }
        LOG.debug("reading domain mappings property [{}] = [{}]", ECAS_DOMIBUS_DOMAIN_MAPPINGS_KEY, domainMappings);

        return Stream.of(domainMappings.split(ECAS_DOMIBUS_MAPPING_PAIR_SEPARATOR))
                .map(str -> str.split(ECAS_DOMIBUS_MAPPING_VALUE_SEPARATOR))
                .collect(Collectors.toMap(str -> str[0], str -> str[1]));
    }

    private void validateDomains(DomibusUserDetails userDetails) {
        if (CollectionUtils.isEmpty(userDetails.getAvailableDomainCodes())) {
            throw new AtLeastOneDomainException();
        }
    }
}
