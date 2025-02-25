package eu.domibus.core.spring;

import eu.domibus.core.logging.LogbackLoggingConfigurator;
import eu.domibus.core.metrics.DomibusAdminServlet;
import eu.domibus.core.plugin.classloader.PluginClassLoader;
import eu.domibus.core.property.DomibusConfigLocationProvider;
import eu.domibus.core.property.DomibusPropertiesPropertySource;
import eu.domibus.core.property.DomibusPropertyConfiguration;
import eu.domibus.web.spring.DomibusWebConfiguration;
import mockit.*;
import mockit.integration.junit4.JMockit;
import org.apache.cxf.transport.servlet.CXFServlet;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static eu.domibus.core.property.DomibusPropertiesPropertySource.UPDATED_PROPERTIES_NAME;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Cosmin Baciu
 * @since 4.2
 */
@SuppressWarnings("TestMethodWithIncorrectSignature")
@RunWith(JMockit.class)
public class DomibusApplicationInitializerTest {

    @Tested
    DomibusApplicationInitializer domibusApplicationInitializer;

    @Test
    public void onStartup(@Injectable ServletContext servletContext,
                          @Mocked DomibusConfigLocationProvider domibusConfigLocationProvider,
                          @Mocked AnnotationConfigWebApplicationContext annotationConfigWebApplicationContext,
                          @Mocked ServletRegistration.Dynamic dispatcher,
                          @Mocked DispatcherServlet dispatcherServlet,
                          @Mocked FilterRegistration.Dynamic springSecurityFilterChain,
                          @Mocked ServletRegistration.Dynamic cxfServlet) throws ServletException, IOException {
        String domibusConfigLocation = Paths.get("/home/domibus").normalize().toString();

        new Expectations(domibusApplicationInitializer) {{
            new DomibusConfigLocationProvider();
            result = domibusConfigLocationProvider;

            domibusConfigLocationProvider.getDomibusConfigLocation(servletContext);
            result = domibusConfigLocation;

            domibusApplicationInitializer.configureLogging(domibusConfigLocation);
            domibusApplicationInitializer.configureMetrics(servletContext);

            new AnnotationConfigWebApplicationContext();
            result = annotationConfigWebApplicationContext;
            times = 2;

            new DispatcherServlet(annotationConfigWebApplicationContext);
            result = dispatcherServlet;


            servletContext.addServlet("dispatcher", dispatcherServlet);
            result = dispatcher;

            domibusApplicationInitializer.configurePropertySources(annotationConfigWebApplicationContext, domibusConfigLocation);

            servletContext.addFilter("springSecurityFilterChain", DelegatingFilterProxy.class);
            result = springSecurityFilterChain;

            servletContext.addServlet("CXF", CXFServlet.class);

        }};

        domibusApplicationInitializer.onStartup(servletContext);

        new Verifications() {{
            annotationConfigWebApplicationContext.register(DomibusRootConfiguration.class, DomibusSessionConfiguration.class);
            annotationConfigWebApplicationContext.register(DomibusWebConfiguration.class);

            List<EventListener> list = new ArrayList<>();
            servletContext.addListener(withCapture(list));
            Assert.assertEquals(2, list.size());
            assertThat(
                    list.stream().map(EventListener::getClass).collect(Collectors.toList()),
                    CoreMatchers.<Class<?>>hasItems(
                            DomibusContextLoaderListener.class,
                            RequestContextListener.class));

            dispatcher.setLoadOnStartup(1);
            dispatcher.addMapping("/");

            servletContext.setSessionTrackingModes(withAny(new HashSet<>()));
            springSecurityFilterChain.addMappingForUrlPatterns(null, false, "/*");

            cxfServlet.setLoadOnStartup(1);
            cxfServlet.addMapping("/services/*");
        }};
    }

    @Test
    public void onStartup_exception(@Injectable ServletContext servletContext,
                                    @Mocked DomibusConfigLocationProvider domibusConfigLocationProvider,
                                    @Mocked AnnotationConfigWebApplicationContext annotationConfigWebApplicationContext) throws IOException {
        String domibusConfigLocation = Paths.get("/home/domibus").normalize().toString();

        new Expectations(domibusApplicationInitializer) {{
            new DomibusConfigLocationProvider();
            result = domibusConfigLocationProvider;

            domibusConfigLocationProvider.getDomibusConfigLocation(servletContext);
            result = domibusConfigLocation;

            domibusApplicationInitializer.configureLogging(Paths.get(domibusConfigLocation).normalize().toString());
            domibusConfigLocationProvider.getDomibusExtensionsLocation(servletContext);

            new AnnotationConfigWebApplicationContext();
            result = annotationConfigWebApplicationContext;
            times = 1;

            domibusApplicationInitializer.configurePropertySources(annotationConfigWebApplicationContext, domibusConfigLocation);
            result = new IOException("ERROR");

        }};

        try {
            domibusApplicationInitializer.onStartup(servletContext);
            Assert.fail();
        } catch (ServletException e) {
            Assert.assertThat(e.getCause(), CoreMatchers.instanceOf(IOException.class));
        }

        new FullVerifications() {{

            annotationConfigWebApplicationContext.register(DomibusRootConfiguration.class, DomibusSessionConfiguration.class);
            annotationConfigWebApplicationContext.setClassLoader((PluginClassLoader)any);
        }};
    }

    @Test
    public void configureMetrics(@Mocked ServletContext servletContext,
                                 @Injectable ServletRegistration.Dynamic servlet) {
        new Expectations() {{
            servletContext.addServlet("metrics", DomibusAdminServlet.class);
            result = servlet;
        }};

        domibusApplicationInitializer.configureMetrics(servletContext);

        new Verifications() {{
            List<EventListener> list = new ArrayList<>();
            servletContext.addListener(withCapture(list));
            Assert.assertEquals(2, list.size());

            servlet.addMapping("/metrics/*");
            times = 1;
        }};
    }

    @Test
    public void createPluginClassLoader() {
        String domibusConfigLocation = "/home/domibus";

        File pluginsLocation = new File(domibusConfigLocation + DomibusApplicationInitializer.PLUGINS_LOCATION);
        File extensionsLocation = new File(domibusConfigLocation + DomibusApplicationInitializer.EXTENSIONS_LOCATION);

        PluginClassLoader pluginClassLoader = domibusApplicationInitializer.createPluginClassLoader(domibusConfigLocation);

        Assert.assertTrue(pluginClassLoader.getFiles().contains(pluginsLocation));
        Assert.assertTrue(pluginClassLoader.getFiles().contains(extensionsLocation));
    }

    @Test
    public void configureLogging(@Mocked LogbackLoggingConfigurator logbackLoggingConfigurator) {
        String domibusConfigLocation = "/home/domibus";

        domibusApplicationInitializer.configureLogging(domibusConfigLocation);

        new Verifications() {{
            new LogbackLoggingConfigurator(domibusConfigLocation);
            times = 1;

            logbackLoggingConfigurator.configureLogging();
            times = 1;
        }};
    }

    @Ignore
    @Test
    public void configurePropertySources(@Injectable AnnotationConfigWebApplicationContext rootContext,
                                         @Injectable ConfigurableEnvironment configurableEnvironment,
                                         @Injectable MutablePropertySources propertySources,
                                         @Injectable MapPropertySource domibusConfigLocationSource,
                                         @Injectable DomibusPropertiesPropertySource domibusPropertiesPropertySource,
                                         @Injectable DomibusPropertiesPropertySource updatedDomibusPropertiesPropertySource) throws IOException {
        String domibusConfigLocation = Paths.get("/home/domibus").normalize().toString();

        new Expectations(domibusApplicationInitializer) {{
            rootContext.getEnvironment();
            result = configurableEnvironment;

            configurableEnvironment.getPropertySources();
            result = propertySources;

            domibusApplicationInitializer.createDomibusConfigLocationSource(domibusConfigLocation);
            result = domibusConfigLocationSource;

            domibusApplicationInitializer.createUpdatedDomibusPropertiesSource();
            result = updatedDomibusPropertiesPropertySource;

            updatedDomibusPropertiesPropertySource.getName();
            result = UPDATED_PROPERTIES_NAME;

            domibusApplicationInitializer.createDomibusPropertiesPropertySource(domibusConfigLocation);
            result = domibusPropertiesPropertySource;
        }};

        domibusApplicationInitializer.configurePropertySources(rootContext, domibusConfigLocation);

        new FullVerificationsInOrder() {{
            propertySources.addFirst(updatedDomibusPropertiesPropertySource);
            times = 1;
            propertySources.addAfter(UPDATED_PROPERTIES_NAME, domibusConfigLocationSource);
            times = 1;
            propertySources.addLast(domibusPropertiesPropertySource);
            times = 1;
            propertySources.stream();
            times = 1;
        }};
    }

    @Test
    public void createDomibusPropertiesPropertySource(@Mocked DomibusPropertyConfiguration domibusPropertyConfiguration,
                                                      @Injectable PropertiesFactoryBean propertiesFactoryBean,
                                                      @Injectable Properties properties,
                                                      @Injectable DomibusPropertiesPropertySource domibusPropertiesPropertySource) throws IOException {
        String domibusConfigLocation = "/home/domibus";

        new Expectations() {{
            new DomibusPropertyConfiguration();
            result = domibusPropertyConfiguration;

            domibusPropertyConfiguration.domibusProperties(domibusConfigLocation);
            result = propertiesFactoryBean;

            propertiesFactoryBean.getObject();
            result = properties;
        }};


        domibusApplicationInitializer.createDomibusPropertiesPropertySource(domibusConfigLocation);

        new Verifications() {{
            propertiesFactoryBean.setSingleton(false);

            new DomibusPropertiesPropertySource(DomibusPropertiesPropertySource.NAME, properties);
            times = 1;
        }};
    }

    @Test
    public void createDomibusConfigLocationSource() {
        String domibusConfigLocation = "/home/domibus";

        MapPropertySource domibusConfigLocationSource = domibusApplicationInitializer.createDomibusConfigLocationSource(domibusConfigLocation);
        Assert.assertEquals("domibusConfigLocationSource", domibusConfigLocationSource.getName());
    }

    @Test
    public void createUpdatedDomibusPropertiesSource() {
        MapPropertySource propertySource = domibusApplicationInitializer.createUpdatedDomibusPropertiesSource();
        Assert.assertEquals(UPDATED_PROPERTIES_NAME, propertySource.getName());
    }

    @Test
    public void testOrderOfPropertySources(@Injectable AnnotationConfigWebApplicationContext rootContext,
                                           @Injectable ConfigurableEnvironment configurableEnvironment,
                                           @Injectable MapPropertySource domibusConfigLocationSource,
                                           @Injectable DomibusPropertiesPropertySource domibusPropertiesPropertySource) throws IOException {
        String domibusConfigLocation = "/home/domibus";
        MutablePropertySources propertySources = new MutablePropertySources();
        DomibusPropertiesPropertySource updatedDomibusPropertiesPropertySource = new DomibusPropertiesPropertySource(UPDATED_PROPERTIES_NAME, new Properties());
        new Expectations(domibusApplicationInitializer) {{
            rootContext.getEnvironment();
            result = configurableEnvironment;

            configurableEnvironment.getPropertySources();
            result = propertySources;

            domibusApplicationInitializer.createDomibusConfigLocationSource(domibusConfigLocation);
            result = domibusConfigLocationSource;

            domibusApplicationInitializer.createDomibusPropertiesPropertySource(domibusConfigLocation);
            result = domibusPropertiesPropertySource;

            domibusApplicationInitializer.createUpdatedDomibusPropertiesSource();
            result = updatedDomibusPropertiesPropertySource;
        }};

        domibusApplicationInitializer.configurePropertySources(rootContext, domibusConfigLocation);

        Assert.assertEquals(0, propertySources.precedenceOf(updatedDomibusPropertiesPropertySource));
        Assert.assertEquals(1, propertySources.precedenceOf(domibusConfigLocationSource));
        Assert.assertEquals(propertySources.size() - 1, propertySources.precedenceOf(domibusPropertiesPropertySource));
    }

}
