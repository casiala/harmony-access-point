package eu.domibus.core.message;


import eu.domibus.core.plugin.routing.RoutingService;
import eu.domibus.messaging.XmlProcessingException;
import eu.domibus.plugin.BackendConnector;
import org.apache.commons.collections.CollectionUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPException;
import java.io.IOException;
import java.util.Map;

/**
 * @author idragusa
 * @since 5.0
 */
@Transactional
public class DeleteReceivedMessageIT extends DeleteMessageAbstractIT {
    @Autowired
    protected RoutingService routingService;
    /**
     * Test to delete a received message
     */
    @Ignore("Test not passing in 5.1.4, only works when executed outside of the build")
    @Test
    public void testReceiveDeleteMessage() throws SOAPException, IOException, ParserConfigurationException, SAXException, XmlProcessingException {
        BackendConnector backendConnector = Mockito.mock(BackendConnector.class);
        Mockito.when(backendConnectorProvider.getBackendConnector(Mockito.any(String.class))).thenReturn(backendConnector);

        deleteAllMessages();

        uploadPmode(SERVICE_PORT);
        Map<String, Integer> initialMap = messageDBUtil.getTableCounts(tablesToExclude);
        receiveMessageToDelete();

        Map<String, Integer> beforeDeletionMap = messageDBUtil.getTableCounts(tablesToExclude);
        deleteAllMessages();

        Map<String, Integer> finalMap = messageDBUtil.getTableCounts(tablesToExclude);

        Assert.assertTrue(initialMap.size() > 0);
        Assert.assertTrue(beforeDeletionMap.size() > 0);
        System.out.println("####before" + initialMap.entrySet());
        System.out.println("####after" + finalMap.entrySet());
        Assert.assertTrue(CollectionUtils.isEqualCollection(initialMap.entrySet(), finalMap.entrySet()));
        Assert.assertFalse(CollectionUtils.isEqualCollection(initialMap.entrySet(), beforeDeletionMap.entrySet()));
    }
}
