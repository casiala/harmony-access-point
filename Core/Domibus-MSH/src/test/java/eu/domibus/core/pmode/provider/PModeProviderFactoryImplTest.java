package eu.domibus.core.pmode.provider;

import eu.domibus.api.multitenancy.Domain;
import eu.domibus.core.pmode.provider.dynamicdiscovery.DynamicDiscoveryPModeProvider;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

@RunWith(MockitoJUnitRunner.class)
public class PModeProviderFactoryImplTest {

    @Injectable
    ApplicationContext applicationContext;

    @Tested
    PModeProviderFactoryImpl pModeProviderFactory;

    @Test
    public void testCreateDomainPModeProvider(@Injectable Domain domain) {
        pModeProviderFactory.createDomainPModeProvider(domain);

        new Verifications() {{
            applicationContext.getBean(DynamicDiscoveryPModeProvider.class, domain);
        }};
    }
}
