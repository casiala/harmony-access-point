package eu.domibus.core.message.testservice;

import eu.domibus.api.ebms3.Ebms3Constants;
import eu.domibus.api.party.PartyService;
import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.api.usermessage.UserMessageService;
import eu.domibus.core.error.ErrorLogService;
import eu.domibus.core.message.UserMessageDao;
import eu.domibus.core.message.UserMessageLogDao;
import eu.domibus.core.message.dictionary.ActionDictionaryService;
import eu.domibus.core.message.dictionary.PartyIdDao;
import eu.domibus.core.message.signal.SignalMessageDao;
import eu.domibus.core.monitoring.ConnectionMonitoringHelper;
import eu.domibus.core.pmode.provider.PModeProvider;
import eu.domibus.plugin.Submission;
import eu.domibus.plugin.handler.MessageSubmitter;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import mockit.integration.junit4.JMockit;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Sebastian-Ion TINCU
 */
@RunWith(JMockit.class)
public class TestServiceIT {

    @Tested
    private TestService testService;

    @Injectable
    private PModeProvider pModeProvider;

    @Injectable
    private UserMessageLogDao userMessageLogDao;

    @Injectable
    private UserMessageDao userMessageDao;

    @Injectable
    private ErrorLogService errorLogService;

    @Injectable
    private SignalMessageDao signalMessageDao;

    @Injectable
    private ActionDictionaryService actionDictionaryService;

    @Injectable
    MessageSubmitter messageSubmitter;

    @Injectable
    UserMessageService userMessageService;

    @Injectable
    DomibusPropertyProvider domibusPropertyProvider;

    @Injectable
    PartyService partyService;

    @Injectable
    ConnectionMonitoringHelper connectionMonitoringHelper;

    @Injectable
    private PartyIdDao partyIdDao;

    @Test
    public void createSubmission() throws IOException {
        // GIVEN
        new Expectations() {{
            pModeProvider.getRole("INITIATOR", Ebms3Constants.TEST_SERVICE);
            result = "initiator";
            pModeProvider.getRole("RESPONDER", Ebms3Constants.TEST_SERVICE);
            result = "responder";
        }};

        // WHEN
        Submission submission = testService.createSubmission("sender");

        // THEN
        Assert.assertTrue("Should have correctly read the submission data from the test service Json file",
                submission != null
                        && "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/test".equals(submission.getAction())
                        && "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/service".equals(submission.getService())
                        && "1".equals(submission.getConversationId())
                        && "initiator".equals(submission.getFromRole())
                        && "responder".equals(submission.getToRole())
                        && submission.getFromParties().size() == 1 && submission.getFromParties().stream().allMatch(party -> "sender".equals(party.getPartyId()))
                        && submission.getToParties().size() == 1 && submission.getToParties().stream().allMatch(party -> "_RECEIVER_".equals(party.getPartyId()))
                        && submission.getPayloads().size() == 1 && submission.getPayloads().stream().allMatch(payload -> "cid:message".equals(payload.getContentId()))
        );
    }
}
