package eu.domibus.core.message;

import eu.domibus.test.AbstractIT;
import eu.domibus.api.ebms3.model.Ebms3UserMessage;
import eu.domibus.api.model.PartInfo;
import eu.domibus.api.model.UserMessage;
import eu.domibus.core.ebms3.mapper.Ebms3Converter;
import eu.domibus.test.common.MessageTestUtility;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Cosmin Baciu
 * @since 5.0
 */
public class Ebms3ConverterTestIT extends AbstractIT {

    @Autowired
    Ebms3Converter ebms3Converter;

    @Test
    public void testEbms3ConversionToEntityAndViceVersa() {
        final MessageTestUtility messageTestUtility = new MessageTestUtility();
        final UserMessage userMessage = messageTestUtility.createSampleUserMessage();
        final List<PartInfo> partInfoList = messageTestUtility.createPartInfoList(userMessage);
        Ebms3UserMessage ebms3UserMessage = ebms3Converter.convertToEbms3(userMessage, partInfoList);

        Assert.assertNotNull(ebms3UserMessage.getPayloadInfo().getPartInfo());
        Assert.assertEquals(1, ebms3UserMessage.getPayloadInfo().getPartInfo().size());

        UserMessage converted = ebms3Converter.convertFromEbms3(ebms3UserMessage);
        assertEquals(userMessage, converted);

    }
}
