package eu.domibus.core.security.configuration;

import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.api.security.AuthRole;
import eu.domibus.web.filter.CookieFilter;
import eu.domibus.web.filter.SetDomainFilter;
import eu.domibus.web.header.ServerHeaderWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.ws.rs.HttpMethod;

import static eu.domibus.api.property.DomibusPropertyMetadataManagerSPI.*;

/**
 * Abstract class for Domibus security configuration
 * <p>
 * It extends {@link WebSecurityConfigurerAdapter} class and declares abstract methods
 * which need to be overridden by each implementation. Common code is exposed in non abstract methods.
 *
 * @author Catalin Enache
 * @since 4.1
 */
public abstract class AbstractWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

    public static final String DOMIBUS_EXTERNAL_API_PREFIX = "/ext";
    public static final String PLUGIN_API_PREFIX = "/api";

    @Autowired
    RequestMatcher csrfURLMatcher;

    @Autowired
    SetDomainFilter setDomainFilter;

    @Autowired
    Http403ForbiddenEntryPoint http403ForbiddenEntryPoint;

    @Autowired
    CookieFilter cookieFilter;

    @Autowired
    ServerHeaderWriter serverHeaderWriter;

    @Autowired
    protected DomibusPropertyProvider domibusPropertyProvider;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        configureHttpSecurityCommon(http);
        configureHttpSecurity(http);
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        configureWebSecurityCommon(web);
        configureWebSecurity(web);
    }

    @Override
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        configureAuthenticationManagerBuilder(auth);
    }


    /**
     * configure {@link HttpSecurity} - to be implemented
     *
     * @param http
     * @throws Exception
     */
    protected abstract void configureHttpSecurity(HttpSecurity http) throws Exception;

    /**
     * configure {@link WebSecurity} - to be implemented
     *
     * @param web
     * @throws Exception
     */
    protected abstract void configureWebSecurity(WebSecurity web) throws Exception;

    /**
     * configure {@link AuthenticationManagerBuilder} to be implemented
     *
     * @param auth an {@link AuthenticationManagerBuilder}
     */
    protected abstract void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception;


    /**
     * common web security common configuration
     *
     * @param web {@link WebSecurity} to configure
     */
    private void configureWebSecurityCommon(WebSecurity web) {
        web
                .ignoring().antMatchers("/services/**")
                .and()
                .ignoring().antMatchers(DOMIBUS_EXTERNAL_API_PREFIX + "/**")
                .and()
                .ignoring().antMatchers(PLUGIN_API_PREFIX + "/**");
    }

    /**
     * common http security config
     *
     * @param httpSecurity
     */
    private void configureHttpSecurityCommon(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .csrf().csrfTokenRepository(csrfTokenRepository()).requireCsrfProtectionMatcher(csrfURLMatcher)
                .and()
                .authorizeRequests()
                .antMatchers("/", "/index.html", "/login",
                        "/rest/security/authentication",
                        "/rest/application/name",
                        "/rest/application/fourcornerenabled",
                        "/rest/application/extauthproviderenabled",
                        "/rest/application/multitenancy",
                        "/rest/application/supportteam",
                        "/rest/security/user").permitAll()
                .antMatchers("/rest/userdomains/**").authenticated()
                .antMatchers("/rest/application/info").authenticated()
                .antMatchers("/rest/domains/**").hasAnyAuthority(AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers(HttpMethod.PUT, "/rest/security/user/password").authenticated()
                .antMatchers("/rest/pmode/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/party/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/truststore/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/keystore/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/tlstruststore/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/messagefilters/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/jms/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/user/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/plugin/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/audit/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/alerts/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/testservice/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/logging/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers(HttpMethod.GET, "/rest/configuration/properties/" + DOMIBUS_UI_CSV_MAX_ROWS).authenticated()
                .antMatchers(HttpMethod.GET, "/rest/configuration/properties/" + DOMIBUS_UI_MESSAGE_LOGS_DEFAULT_INTERVAL).authenticated()
                .antMatchers(HttpMethod.GET, "/rest/configuration/properties/" + DOMIBUS_UI_MESSAGE_LOGS_LANDING_PAGE).authenticated()
                .antMatchers(HttpMethod.GET, "/rest/configuration/properties/" + DOMIBUS_UI_MESSAGE_LOGS_SEARCH_ADVANCED_ENABLED).authenticated()
                .antMatchers("/rest/configuration/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/metrics/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/message/restore/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/message/failed/restore/**").hasAnyAuthority(AuthRole.ROLE_ADMIN.name(), AuthRole.ROLE_AP_ADMIN.name())
                .antMatchers("/rest/**").authenticated()
                .and()
                .exceptionHandling().and()
                .headers().addHeaderWriter(serverHeaderWriter).frameOptions().deny().contentTypeOptions()
                .and().xssProtection().xssProtectionEnabled(true)
                .and().contentSecurityPolicy("default-src 'self'; script-src 'self'; child-src 'none'; connect-src 'self'; img-src * 'self' data: https:; style-src 'self' 'unsafe-inline'; frame-ancestors 'self'; form-action 'self';").and().and()
                .httpBasic().authenticationEntryPoint(http403ForbiddenEntryPoint)
                .and()
                .addFilterBefore(setDomainFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(cookieFilter, SetDomainFilter.class);
    }

    private CookieCsrfTokenRepository csrfTokenRepository() {
        final CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();

        // the XSRF-TOKEN cookie needs to be read by the Angular code in our domibus admin console, so we cannot mark it as 'httpOnly'
        Boolean secure = domibusPropertyProvider.getBooleanProperty(DOMIBUS_UI_SESSION_SECURE);
        repository.setSecure(secure);

        return repository;
    }

}
