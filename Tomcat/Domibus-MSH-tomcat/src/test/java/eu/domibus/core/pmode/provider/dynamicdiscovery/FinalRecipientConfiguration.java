package eu.domibus.core.pmode.provider.dynamicdiscovery;


import eu.europa.ec.dynamicdiscovery.model.SMPServiceMetadata;

import java.security.cert.X509Certificate;

/**
 * @author Cosmin Baciu
 * @since 5.1.1
 */
public class FinalRecipientConfiguration {
        protected X509Certificate certificate;
        protected SMPServiceMetadata serviceMetadata;

        protected String partyName;

        public FinalRecipientConfiguration(X509Certificate certificate, SMPServiceMetadata serviceMetadata, String partyName) {
            this.certificate = certificate;
            this.serviceMetadata = serviceMetadata;
            this.partyName = partyName;
        }

        public X509Certificate getCertificate() {
            return certificate;
        }

        public void setCertificate(X509Certificate certificate) {
            this.certificate = certificate;
        }

        public SMPServiceMetadata getServiceMetadata() {
            return serviceMetadata;
        }

        public void setServiceMetadata(SMPServiceMetadata serviceMetadata) {
            this.serviceMetadata = serviceMetadata;
        }

        public String getPartyName() {
            return partyName;
        }

        public void setPartyName(String partyName) {
            this.partyName = partyName;
        }
    }
