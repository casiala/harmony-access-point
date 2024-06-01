package eu.domibus.web.rest;

import eu.domibus.api.multitenancy.Domain;
import eu.domibus.api.multitenancy.DomainContextProvider;
import eu.domibus.api.multitenancy.DomainService;
import eu.domibus.api.multitenancy.UserDomainService;
import eu.domibus.api.security.DomibusUserDetails;
import eu.domibus.core.converter.DomibusCoreMapper;
import eu.domibus.core.util.WarningUtil;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.logging.DomibusMessageCode;
import eu.domibus.web.rest.error.ErrorHandlerService;
import eu.domibus.web.rest.ro.*;
import eu.domibus.web.security.AuthenticationService;
import eu.domibus.web.security.DomibusCookieClearingLogoutHandler;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

import static eu.domibus.core.spring.DomibusSessionConfiguration.SESSION_COOKIE_NAME;
import static eu.domibus.web.rest.error.ErrorMessages.DEFAULT_MESSAGE_FOR_AUTHENTICATION_ERRORS;

/**
 * @author Cosmin Baciu, Catalin Enache
 * @since 3.3
 */
@RestController
@RequestMapping(value = "/rest/security")
@Validated
public class AuthenticationResource {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(AuthenticationResource.class);

    public static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

    protected final AuthenticationService authenticationService;

    protected final DomainContextProvider domainContextProvider;

    protected final UserDomainService userDomainService;

    protected final DomainService domainService;

    protected final DomibusCoreMapper coreMapper;

    protected final ErrorHandlerService errorHandlerService;

    protected final CompositeSessionAuthenticationStrategy sas;

    public AuthenticationResource(AuthenticationService authenticationService, DomainContextProvider domainContextProvider,
                                  UserDomainService userDomainService, DomainService domainService, DomibusCoreMapper coreMapper,
                                  ErrorHandlerService errorHandlerService, CompositeSessionAuthenticationStrategy sas) {
        this.authenticationService = authenticationService;
        this.domainContextProvider = domainContextProvider;
        this.userDomainService = userDomainService;
        this.domainService = domainService;
        this.coreMapper = coreMapper;
        this.errorHandlerService = errorHandlerService;
        this.sas = sas;
    }

    @ExceptionHandler({AccountStatusException.class})
    public ResponseEntity<ErrorRO> handleAccountStatusException(AccountStatusException ex) {
        return errorHandlerService.createResponse(ex, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({AuthenticationException.class})
    public ResponseEntity<ErrorRO> handleAuthenticationException(AuthenticationException ex) {
        return errorHandlerService.createResponse(ex, HttpStatus.FORBIDDEN);
    }

    @PostMapping(value = "authentication")
    public UserRO authenticate(@RequestBody @Valid LoginRO loginRO, HttpServletResponse response, HttpServletRequest request) {

        String domainCode = userDomainService.getDomainForUser(loginRO.getUsername());
        LOG.debug("Determined domain [{}] for user [{}]", domainCode, loginRO.getUsername());

        if (StringUtils.isNotBlank(domainCode)) {   //domain user
            domainContextProvider.setCurrentDomainWithValidation(domainCode);
        } else {                    //ap user
            domainContextProvider.clearCurrentDomain();
            domainCode = userDomainService.getPreferredDomainForUser(loginRO.getUsername());
            if (StringUtils.isBlank(domainCode)) {
                LOG.securityWarn(DomibusMessageCode.SEC_CONSOLE_LOGIN_UNKNOWN_USER, loginRO.getUsername());
                throw new BadCredentialsException(DEFAULT_MESSAGE_FOR_AUTHENTICATION_ERRORS);
            }

            LOG.debug("Determined preferred domain [{}] for user [{}]", domainCode, loginRO.getUsername());
            domainService.validateDomain(domainCode);
        }

        LOG.debug("Authenticating user [{}]", loginRO.getUsername());
        final DomibusUserDetails principal = authenticationService.authenticate(loginRO.getUsername(), loginRO.getPassword(), domainCode);
        if (principal.isDefaultPasswordUsed()) {
            LOG.warn(WarningUtil.warnOutput(principal.getUsername() + " is using default password."));
        }

        sas.onAuthentication(SecurityContextHolder.getContext().getAuthentication(), request, response);

        return createUserRO(principal, loginRO.getUsername());
    }

    @DeleteMapping(value = "authentication")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            LOG.debug("Cannot perform logout: no user is authenticated");
            return;
        }

        LOG.debug("Logging out user [" + auth.getName() + "]");
        new DomibusCookieClearingLogoutHandler(SESSION_COOKIE_NAME, CSRF_COOKIE_NAME).logout(request, response, null);
        LOG.debug("Cleared cookies");
        new SecurityContextLogoutHandler().logout(request, response, auth);
        LOG.debug("Logged out");
    }

    /**
     * Method used by admin console to check if the current session is still active
     * if the user has proper authentication rights and valid session it succeeds
     * otherwise the method is not called because the infrastructure throws 401 or 403
     *
     * @return always true
     */
    @GetMapping(value = "user/connected")
    public boolean isUserConnected() {
        return true;
    }

    @GetMapping(value = "user")
    public UserRO getUser() {
        LOG.debug("get user - start");
        DomibusUserDetails domibusUserDetails = authenticationService.getLoggedUser();

        return domibusUserDetails != null ? createUserRO(domibusUserDetails, domibusUserDetails.getUsername()) : null;
    }

    /**
     * Retrieve the current domain of the current user (in multi-tenancy mode)
     *
     * @return the current domain
     */
    @GetMapping(value = "user/domain")
    public DomainRO getCurrentDomain() {
        LOG.debug("Getting current domain");
        Domain domain = domainContextProvider.getCurrentDomainSafely();
        return coreMapper.domainToDomainRO(domain);
    }

    /**
     * Set the current domain of the current user (in multi-tenancy mode)
     *
     * @param domainCode the code of the new current domain
     */
    @PutMapping(value = "user/domain")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setCurrentDomain(@RequestBody @Valid String domainCode) {
        LOG.debug("Setting current domain " + domainCode);
        authenticationService.changeDomain(domainCode);
    }

    /**
     * Set the password of the current user
     *
     * @param param the object holding the current and new passwords of the current user
     */
    @PutMapping(value = "user/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody @Valid ChangePasswordRO param) {
        authenticationService.changePassword(param.getCurrentPassword(), param.getNewPassword());
    }

    private UserRO createUserRO(DomibusUserDetails principal, String username) {
        //Parse Granted authorities to a list of string authorities
        List<String> authorities = new ArrayList<>();
        for (GrantedAuthority grantedAuthority : principal.getAuthorities()) {
            authorities.add(grantedAuthority.getAuthority());
        }

        UserRO userRO = new UserRO();
        userRO.setUsername(username);
        userRO.setAuthorities(authorities);
        userRO.setDefaultPasswordUsed(principal.isDefaultPasswordUsed());
        userRO.setDaysTillExpiration(principal.getDaysTillExpiration());
        userRO.setExternalAuthProvider(principal.isExternalAuthProvider());
        return userRO;
    }

}
