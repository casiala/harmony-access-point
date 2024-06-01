package eu.domibus.core.property;

import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Properties;
import java.util.TimeZone;

/**
 * @author Federico Martini
 * @author Soumya
 * This class is designed to retrieve Domibus version details.
 */
@Service
public class DomibusVersionService {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(DomibusVersionService.class);

    private static Properties versionProps = new Properties();

    public DomibusVersionService() {
        init();
    }

    public void init() {
        try (InputStream inputStream = new ClassPathResource("config/version.properties").getInputStream()) {
            if (inputStream == null) {
                LOG.warn("The 'version.properties' has not been found!");
                return;
            }
            versionProps.load(inputStream);
            LOG.info("=========================================================================================================");
            LOG.info("|         " + getDisplayVersion() + "        |");
            LOG.info("|         Upstream version [" + getUpstreamVersion() + "]");
            LOG.info("=========================================================================================================");
        } catch (Exception ex) {
            LOG.warn("Error loading version properties", ex);
        }
    }

    public String getArtifactVersion() {
        return versionProps.getProperty("Artifact-Version");
    }

    public String getVersionNumber() {
        String artifactVersion = getArtifactVersion();
        String versionNumber = artifactVersion.split("-")[0];
        return versionNumber;
    }

    public String getUpstreamVersion() {
      return versionProps.getProperty("Upstream-Version");
    }

    public String getArtifactName() {
        return versionProps.getProperty("Artifact-Name");
    }

    public String getBuiltTime() {
        return versionProps.getProperty("Build-Time") + "|" + TimeZone.getTimeZone("UTC").getDisplayName();
    }

    public String getDisplayVersion() {
        StringBuilder display = new StringBuilder();
        display.append(getArtifactName());
        display.append(" Version [");
        display.append(getArtifactVersion());
        display.append("] Build-Time [");
        display.append(getBuiltTime());
        display.append("]");
        return display.toString();
    }

    public String getBuildDetails() {
        StringBuilder display = new StringBuilder();
        display.append(" Build-Name [");
        display.append(getArtifactName());
        display.append("]");
        return display.toString();

    }
}
