package eu.domibus.core.party;

import eu.domibus.test.AbstractIT;
import eu.domibus.common.model.configuration.Identifier;
import eu.domibus.common.model.configuration.Party;
import eu.domibus.common.model.configuration.Process;
import eu.domibus.core.pmode.ConfigurationDAO;
import eu.domibus.core.pmode.ProcessDaoImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.sql.SQLException;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

/**
 * PartyDaoConfig is commented out because it was causing issues when running the tests locally.
 * To reproduce the issue, uncomment the configuration and the autowire of PartyDao and run tests on the package {@link eu.domibus.core}
 *
 * @author Thomas Dussart
 * @since 4.0
 */
@Transactional
public class PartyDaoIT extends AbstractIT {

    @PersistenceContext
    private javax.persistence.EntityManager em;

    @Autowired
    private PartyDao partyDao;

    @Autowired
    private ProcessDaoImpl processDao;

    @Autowired
    ConfigurationDAO configurationDAO;

    @Transactional
    @Before
    public void initParty() {
        partyDao = new PartyDao();
        ReflectionTestUtils.setField(partyDao, null, em, EntityManager.class);

        Party party = new Party();
        party.setName("P1");
        Identifier id = new Identifier();
        id.setPartyId("P1 party id");
        party.getIdentifiers().add(id);

        em.persist(party);

        Process process = new Process();
        process.setName("PR1");
        process.addInitiator(party);

        party = new Party();
        party.setName("P2");
        id = new Identifier();
        id.setPartyId("P2 party id");
        party.getIdentifiers().add(id);

        process.addResponder(party);

        em.persist(party);

        party = new Party();
        party.setName("P3");
        id = new Identifier();
        id.setPartyId("P3 party id");
        party.getIdentifiers().add(id);

        em.persist(party);

        em.persist(process);
    }

    @Transactional
    @Test
    public void listParties() throws SQLException {
        List<Party> parties = partyDao.getParties();
        assertNotNull(parties.get(0).getCreationTime());
        assertNotNull(parties.get(0).getModificationTime());
        assertNotNull(parties.get(0).getCreatedBy());
        assertNotNull(parties.get(0).getModifiedBy());
        assertNotNull(parties.get(1).getCreationTime());
        assertNotNull(parties.get(1).getModificationTime());
        assertNotNull(parties.get(1).getCreatedBy());
        assertNotNull(parties.get(1).getModifiedBy());
        assertNotNull(parties.get(2).getCreationTime());
        assertNotNull(parties.get(2).getModificationTime());
        assertNotNull(parties.get(2).getCreatedBy());
        assertNotNull(parties.get(2).getModifiedBy());
    }

    @Transactional
    @Test
    public void testFindById() {
        // When
        Party findById = partyDao.findById("P1 party id");

        // Then
        Assert.assertNotNull(findById);
        assertNotNull(findById.getCreationTime());
        assertNotNull(findById.getModificationTime());
        assertNotNull(findById.getCreatedBy());
        assertNotNull(findById.getModifiedBy());

        assertEquals(findById.getCreationTime(), findById.getModificationTime());
    }

}
