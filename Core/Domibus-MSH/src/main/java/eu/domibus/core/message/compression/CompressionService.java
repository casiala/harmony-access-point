package eu.domibus.core.message.compression;

import eu.domibus.api.message.compression.DecompressionDataSource;
import eu.domibus.api.model.*;
import eu.domibus.api.property.DomibusPropertyMetadataManagerSPI;
import eu.domibus.api.property.DomibusPropertyProvider;
import eu.domibus.common.ErrorCode;
import eu.domibus.common.model.configuration.LegConfiguration;
import eu.domibus.core.ebms3.EbMS3Exception;
import eu.domibus.core.ebms3.EbMS3ExceptionBuilder;
import eu.domibus.core.message.dictionary.PartPropertyDictionaryService;
import eu.domibus.core.message.splitandjoin.SplitAndJoinConfigurationService;
import eu.domibus.core.message.splitandjoin.SplitAndJoinHelper;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.logging.DomibusMessageCode;
import eu.domibus.messaging.MessageConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import java.io.*;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * This class is responsible for compression handling of incoming and outgoing ebMS3 messages.
 *
 * @author Christian Koch, Stefan Mueller
 * @since 3.0
 */
@Service
public class CompressionService {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(CompressionService.class);

    @Autowired
    private CompressionMimeTypeBlacklist blacklist;

    @Autowired
    protected SplitAndJoinConfigurationService splitAndJoinConfigurationService;

    @Autowired
    protected PartPropertyDictionaryService partPropertyDictionaryService;

    @Autowired
    protected DomibusPropertyProvider domibusPropertyProvider;

    /**
     * This method is responsible for compression of payloads in a ebMS3 AS4 conformant way in case of {@link eu.domibus.api.model.MSHRole#SENDING}
     *
     * @param messageId           the sending {@link UserMessage} with all payloads
     * @param partInfo            the sending {@link UserMessage} with all payloads
     * @param legConfigForMessage legconfiguration for this message
     * @return {@code true} if compression was applied properly and {@code false} if compression was not enabled in the corresponding pmode
     * @throws EbMS3Exception if an problem occurs during the compression or the mimetype was missing
     */
    public boolean handleCompression(String messageId, PartInfo partInfo, final LegConfiguration legConfigForMessage) throws EbMS3Exception {
        if (partInfo == null) {
            return false;
        }

        //if compression is not necessary return false
        if (!legConfigForMessage.isCompressPayloads()) {
            LOG.debug("Compression is not configured for message [{}]", messageId);
            return false;
        }

        if (partInfo.isInBody()) {
            LOG.debug("Compression is not used for body payloads");
            return false;
        }

        final boolean mayUseSplitAndJoin = splitAndJoinConfigurationService.mayUseSplitAndJoin(legConfigForMessage);
        if (mayUseSplitAndJoin) {
            LOG.debug("SplitAndJoin compression is only applied for the multipart message");
            return false;
        }

        if (partInfo.getPartProperties() == null) {
            LOG.businessError(DomibusMessageCode.BUS_MESSAGE_PAYLOAD_COMPRESSION_FAILURE_MISSING_MIME_TYPE, partInfo.getHref(), messageId);
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0303)
                    .message("No mime type found for payload with cid:" + partInfo.getHref())
                    .refToMessageId(messageId)
                    .mshRole(MSHRole.SENDING)
                    .build();
        }

        String mimeType = null;
        for (final Property property : partInfo.getPartProperties()) {
            if (Property.MIME_TYPE.equalsIgnoreCase(property.getName())) {
                mimeType = property.getValue();
                break;
            }
        }

        if (mimeType == null || mimeType.isEmpty()) {
            LOG.businessError(DomibusMessageCode.BUS_MESSAGE_PAYLOAD_COMPRESSION_FAILURE_MISSING_MIME_TYPE, partInfo.getHref(), messageId);
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0303)
                    .message("No mime type found for payload with cid:" + partInfo.getHref())
                    .refToMessageId(messageId)
                    .mshRole(MSHRole.SENDING)
                    .build();
        }

        //if mimetype of payload is not considered to be compressed, skip
        if (this.blacklist.getEntries().contains(mimeType)) {
            return false;
        }

        final PartProperty compressionProperty = partPropertyDictionaryService.findOrCreatePartProperty(MessageConstants.COMPRESSION_PROPERTY_KEY, MessageConstants.COMPRESSION_PROPERTY_VALUE, null);
        partInfo.addProperty(compressionProperty);
        final CompressedDataSource compressedDataSource = new CompressedDataSource(partInfo.getPayloadDatahandler().getDataSource());
        DataHandler gZipDataHandler = new DataHandler(compressedDataSource);
        partInfo.setPayloadDatahandler(gZipDataHandler);
        LOG.debug("Payload with cid: [{}] and mime type: [{}] will be compressed", partInfo.getHref(), mimeType);

        return true;
    }

    /**
     * This method handles decompression of payloads for messages in case of {@link eu.domibus.api.model.MSHRole#RECEIVING}
     *
     * @param userMessage         the receving {@link UserMessage} with all payloads
     * @param legConfigForMessage processing information for the message
     * @return {@code true} if everything was decompressed without problems, {@code false} in case of disabled compression via pmode
     * @throws EbMS3Exception if an problem occurs during the de compression or the mimetype of a compressed payload was missing
     */
    public void handleDecompression(final UserMessage userMessage, List<PartInfo> partInfoList, final LegConfiguration legConfigForMessage) throws EbMS3Exception {
        for (final PartInfo partInfo : partInfoList) {
            handlePartInfoDecompression(userMessage.getMessageId(), partInfo);
        }
    }

    public void handlePartInfoDecompression(String messageId, PartInfo partInfo) throws EbMS3Exception {
        if (partInfo.isInBody()) {
            LOG.businessInfo(DomibusMessageCode.BUS_MESSAGE_PAYLOAD_DECOMPRESSION_PART_INFO_IN_BODY, partInfo.getHref());
            return;
        }

        String mimeType = null;
        boolean payloadCompressed = false;

        if (partInfo.getPartProperties() != null) {
            for (final Property property : partInfo.getPartProperties()) {
                if (Property.MIME_TYPE.equalsIgnoreCase(property.getName())) {
                    mimeType = property.getValue();
                }
                if (MessageConstants.COMPRESSION_PROPERTY_KEY.equalsIgnoreCase(property.getName()) && MessageConstants.COMPRESSION_PROPERTY_VALUE.equalsIgnoreCase(property.getValue())) {
                    payloadCompressed = true;
                }
            }
        }

        if (!payloadCompressed) {
            LOG.debug("Decompression is not needed: payload is not compressed");
            return;
        }

        final PartProperty compressionProperty = new PartProperty();
        compressionProperty.setName(MessageConstants.COMPRESSION_PROPERTY_KEY);
        compressionProperty.setValue(MessageConstants.COMPRESSION_PROPERTY_VALUE);
        partInfo.removeProperty(compressionProperty);

        if (mimeType == null) {
            LOG.businessError(DomibusMessageCode.BUS_MESSAGE_PAYLOAD_DECOMPRESSION_FAILURE_MISSING_MIME_TYPE, partInfo.getHref(), messageId);
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0303)
                    .message("No mime type found for payload with cid:" + partInfo.getHref())
                    .refToMessageId(messageId)
                    .mshRole(MSHRole.RECEIVING)
                    .build();
        }
        partInfo.setCompressed(true);
        LOG.businessInfo(DomibusMessageCode.BUS_MESSAGE_PAYLOAD_DECOMPRESSION, partInfo.getHref());
        if (!domibusPropertyProvider.getBooleanProperty(DomibusPropertyMetadataManagerSPI.DOMIBUS_PAYLOAD_DECOMPRESSION_VALIDATION_ACTIVE)) {
            LOG.debug("Property [{}] is not enabled, ", DomibusPropertyMetadataManagerSPI.DOMIBUS_PAYLOAD_DECOMPRESSION_VALIDATION_ACTIVE);
            return;
        }

        validateDecompression(messageId, partInfo, mimeType);
    }

    public void validateDecompression(String messageId, PartInfo partInfo, String mimeType) throws EbMS3Exception {
        LOG.debug("Property [{}] is enabled, performing decompression validation for partInfo [{}].", DomibusPropertyMetadataManagerSPI.DOMIBUS_PAYLOAD_DECOMPRESSION_VALIDATION_ACTIVE, partInfo.getHref());

        try (InputStream is = new DecompressionDataSource(partInfo.getPayloadDatahandler().getDataSource(), mimeType).getInputStream()) {
            if (is.available() > 0) {
                LOG.debug("Creating decompression data source was successful", partInfo.getHref());
            }
        } catch (IOException e) {
            LOG.businessError(DomibusMessageCode.BUS_MESSAGE_PAYLOAD_COMPRESSION_FAILURE, partInfo.getHref(), e);
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0303)
                    .message("Decompression exception")
                    .refToMessageId(messageId)
                    .mshRole(MSHRole.RECEIVING)
                    .build();
        }

        try (ByteArrayOutputStream os = new ByteArrayOutputStream();
             GZIPInputStream gis = new GZIPInputStream(partInfo.getPayloadDatahandler().getDataSource().getInputStream())) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        } catch (IOException e) {
            LOG.businessError(DomibusMessageCode.BUS_MESSAGE_PAYLOAD_COMPRESSION_FAILURE, partInfo.getHref(), e);
            throw EbMS3ExceptionBuilder.getInstance()
                    .ebMS3ErrorCode(ErrorCode.EbMS3ErrorCode.EBMS_0303)
                    .message("Decompression exception")
                    .refToMessageId(messageId)
                    .mshRole(MSHRole.RECEIVING)
                    .build();
        }

        LOG.info("The validation of the decompression for partInfo [{}] was successful", partInfo.getHref());
    }

    private class CompressedDataSource implements DataSource {
        private DataSource ds;

        private CompressedDataSource(DataSource ds) {
            this.ds = ds;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return ds.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return ds.getOutputStream();
        }

        @Override
        public String getContentType() {
            return MessageConstants.COMPRESSION_PROPERTY_VALUE;
        }

        @Override
        public String getName() {
            return "compressed-" + ds.getName();
        }
    }
}
