package eu.domibus.plugin.jms.property;

import eu.domibus.ext.domain.DomibusPropertyMetadataDTO;
import eu.domibus.ext.domain.DomibusPropertyMetadataDTO.Type;
import eu.domibus.ext.domain.DomibusPropertyMetadataDTO.Usage;
import eu.domibus.ext.domain.Module;
import eu.domibus.ext.services.DomibusPropertyExtServiceDelegateAbstract;
import eu.domibus.plugin.jms.JMSMessageConstants;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static eu.domibus.plugin.jms.JMSMessageConstants.*;

/**
 * @author Ion Perpegel
 * @since 4.1.1
 * <p>
 * Property manager for the JmsPlugin properties.
 */
@Service
public class JmsPluginPropertyManager extends DomibusPropertyExtServiceDelegateAbstract {

    private final List<DomibusPropertyMetadataDTO> readOnlyGlobalProperties = Arrays.asList(
            new DomibusPropertyMetadataDTO(CONNECTION_FACTORY, Type.JNDI, Module.JMS_PLUGIN, false, Usage.GLOBAL, true, false, false, false),
            new DomibusPropertyMetadataDTO(CACHING_CONNECTION_FACTORY_SESSION_CACHE_SIZE, Type.NUMERIC, Module.JMS_PLUGIN, false, Usage.GLOBAL, true, false, false, false),
            new DomibusPropertyMetadataDTO(QUEUE_NOTIFICATION, Type.JNDI, Module.JMS_PLUGIN, false, Usage.GLOBAL, false, false, false, false),
            new DomibusPropertyMetadataDTO(QUEUE_IN, Type.JNDI, Module.JMS_PLUGIN, false, Usage.GLOBAL, false, false, false, false),
            new DomibusPropertyMetadataDTO(QUEUE_IN_CONCURRENCY, Type.CONCURRENCY, Module.JMS_PLUGIN, false, Usage.GLOBAL, false, false, false, false),
            new DomibusPropertyMetadataDTO(MESSAGE_NOTIFICATIONS, Type.COMMA_SEPARATED_LIST, Module.JMS_PLUGIN, false, Usage.GLOBAL, false, false, false, false)
    );

    private final List<DomibusPropertyMetadataDTO> readOnlyDomainProperties = Arrays.stream(new String[]{
                    JMSPLUGIN_QUEUE_OUT,
                    JMSPLUGIN_QUEUE_REPLY,
                    JMSPLUGIN_QUEUE_CONSUMER_NOTIFICATION_ERROR,
                    JMSPLUGIN_QUEUE_PRODUCER_NOTIFICATION_ERROR,
                    JMS_PLUGIN_PROPERTY_PREFIX + "." + P1_IN_BODY
            })
            .map(name -> new DomibusPropertyMetadataDTO(name, Module.JMS_PLUGIN, false, Usage.DOMAIN, true, false, false, false))
            .collect(Collectors.toList());

    private final List<DomibusPropertyMetadataDTO> readOnlyComposableDomainProperties = Arrays.stream(new String[]{
                    JMSPLUGIN_QUEUE_OUT_ROUTING,
                    JMSPLUGIN_QUEUE_REPLY_ROUTING,
                    JMSPLUGIN_QUEUE_CONSUMER_NOTIFICATION_ERROR_ROUTING,
                    JMSPLUGIN_QUEUE_PRODUCER_NOTIFICATION_ERROR_ROUTING
            })
            .map(name -> new DomibusPropertyMetadataDTO(name, Module.JMS_PLUGIN, false, Usage.DOMAIN, false, false, false, true))
            .collect(Collectors.toList());


    private final List<DomibusPropertyMetadataDTO> writableProperties = Arrays.asList(
            new DomibusPropertyMetadataDTO(JMSPLUGIN_DOMAIN_ENABLED, Type.BOOLEAN, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + FROM_PARTY_ID, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + FROM_PARTY_TYPE, Type.URI, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + FROM_ROLE, Type.URI, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + TO_PARTY_ID, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + TO_PARTY_TYPE, Type.URI, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + TO_ROLE, Type.URI, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + AGREEMENT_REF, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + AGREEMENT_REF_TYPE, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + SERVICE, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + SERVICE_TYPE, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + ACTION, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + PUT_ATTACHMENTS_IN_QUEUE, Type.BOOLEAN, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + JMSMessageConstants.ATTACHMENTS_REFERENCE_TYPE, Type.STRING, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + JMSMessageConstants.ATTACHMENTS_REFERENCE_CONTEXT, Type.URI, Module.JMS_PLUGIN, Usage.DOMAIN, true),
            new DomibusPropertyMetadataDTO(JMS_PLUGIN_PROPERTY_PREFIX + "." + JMSMessageConstants.ATTACHMENTS_REFERENCE_URL, Type.URI, Module.JMS_PLUGIN, Usage.DOMAIN, true)
    );

    @Override
    public Map<String, DomibusPropertyMetadataDTO> getKnownProperties() {
        List<DomibusPropertyMetadataDTO> allProperties = new ArrayList<>();
        allProperties.addAll(readOnlyGlobalProperties);
        allProperties.addAll(readOnlyDomainProperties);
        allProperties.addAll(readOnlyComposableDomainProperties);
        allProperties.addAll(writableProperties);

        return allProperties.stream().collect(Collectors.toMap(DomibusPropertyMetadataDTO::getName, x -> x));
    }

    @Override
    protected String getPropertiesFileName() {
        return "jms-plugin.properties";
    }

    public boolean isDomainEnabled(String domain) {
        String value = getKnownPropertyValue(domain, JMSPLUGIN_DOMAIN_ENABLED);
        return BooleanUtils.toBoolean(value);
    }
}
