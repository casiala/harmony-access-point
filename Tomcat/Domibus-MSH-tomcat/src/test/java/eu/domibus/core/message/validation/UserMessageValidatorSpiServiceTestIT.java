package eu.domibus.core.message.validation;

import eu.domibus.test.AbstractIT;
import eu.domibus.api.message.validation.UserMessageValidatorSpiService;
import eu.domibus.api.model.MSHRole;
import eu.domibus.api.model.PartInfo;
import eu.domibus.api.model.UserMessage;
import eu.domibus.core.message.dictionary.MshRoleDao;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.test.common.MessageTestUtility;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;

public class UserMessageValidatorSpiServiceTestIT extends AbstractIT {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(AbstractIT.class);

    @Autowired
    UserMessageValidatorSpiService userMessageValidatorSpiService;

    @Autowired
    MshRoleDao mshRoleDao;

    @Configuration
    static class ContextConfiguration {
        @Bean
        public UserMessageValidatorSpiMock userMessageValidatorSpiMock() {
            return new UserMessageValidatorSpiMock();
        }
    }

    @Test
    public void testUserMessageValidation() {
        final MessageTestUtility messageTestUtility = new MessageTestUtility();
        final UserMessage userMessage = messageTestUtility.createSampleUserMessage();
        userMessage.setMshRole(mshRoleDao.findOrCreate(MSHRole.SENDING));
        final List<PartInfo> partInfoList = messageTestUtility.createPartInfoList(userMessage);

        userMessageValidatorSpiService.validate(userMessage, partInfoList);

        for (PartInfo partInfo : partInfoList) {
            try {
                LOG.debug("Consuming part info [{}]", partInfo.getHref());
                //double check that we can still consume the data handler after the payloads have been read by the validator
                IOUtils.toByteArray(partInfo.getPayloadDatahandler().getInputStream());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
