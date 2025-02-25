package eu.domibus.plugin.fs.worker;

import eu.domibus.common.MSHRole;
import eu.domibus.ext.domain.JMSMessageDTOBuilder;
import eu.domibus.ext.domain.JmsMessageDTO;
import eu.domibus.ext.exceptions.AuthenticationExtException;
import eu.domibus.ext.exceptions.DomibusErrorCode;
import eu.domibus.ext.services.AuthenticationExtService;
import eu.domibus.ext.services.DomainContextExtService;
import eu.domibus.ext.services.DomibusConfigurationExtService;
import eu.domibus.ext.services.JMSExtService;
import eu.domibus.logging.DomibusLogger;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.logging.MDCKey;
import eu.domibus.messaging.MessageConstants;
import eu.domibus.messaging.MessagingProcessingException;
import eu.domibus.plugin.fs.FSFileNameHelper;
import eu.domibus.plugin.fs.FSFilesManager;
import eu.domibus.plugin.fs.exception.FSPluginException;
import eu.domibus.plugin.fs.exception.FSSetUpException;
import eu.domibus.plugin.fs.property.FSPluginProperties;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.jms.Queue;
import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author FERNANDES Henrique, GONCALVES Bruno
 */
@Service
public class FSSendMessagesService {

    private static final DomibusLogger LOG = DomibusLoggerFactory.getLogger(FSSendMessagesService.class);

    public static final String METADATA_FILE_NAME = "metadata.xml";
    public static final String DEFAULT_DOMAIN = "default";
    public static final String ERROR_EXTENSION = ".error";
    private static final String LS = System.lineSeparator();

    @Autowired
    protected FSPluginProperties fsPluginProperties;

    @Autowired
    protected FSFilesManager fsFilesManager;

    @Autowired
    protected FSProcessFileService fsProcessFileService;

    @Autowired
    protected AuthenticationExtService authenticationExtService;

    @Autowired
    protected DomibusConfigurationExtService domibusConfigurationExtService;

    @Autowired
    protected DomainContextExtService domainContextExtService;

    @Autowired
    protected FSDomainService fsDomainService;

    @Autowired
    protected JMSExtService jmsExtService;

    @Autowired
    @Qualifier("fsPluginSendQueue")
    protected Queue fsPluginSendQueue;

    @Autowired
    protected FSFileNameHelper fsFileNameHelper;


    protected Map<String, FileInfo> observedFilesInfo = new ConcurrentHashMap<>();

    /**
     * Triggering the send messages means that the message files from the OUT directory
     * will be processed to be sent
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendMessages() {
        final String domain = fsDomainService.getFSPluginDomain();

        if (!fsPluginProperties.getDomainEnabled(domain)) {
            LOG.debug("Domain [{}] is disabled for FSPlugin", domain);
            return;
        }

        LOG.debug("Sending file system messages...");

        sendMessagesSafely(domain);

        clearObservedFiles(domain);
    }

    @MDCKey(value = DomibusLogger.MDC_DOMAIN, cleanOnStart = true)
    protected void sendMessagesSafely(String domain) {
        if (StringUtils.isNotEmpty(domain)) {
            LOG.putMDC(DomibusLogger.MDC_DOMAIN, domain);
        }
        try {
            sendMessages(domain);
        } catch (AuthenticationExtException ex) {
            LOG.error("Authentication error for domain [{}]", domain, ex);
        }
    }

    protected void sendMessages(final String domain) {
        if (!fsPluginProperties.getDomainEnabled(domain)) {
            LOG.debug("Domain [{}] is disabled for FSPlugin", domain);
            return;
        }

        LOG.debug("Sending messages for domain [{}]", domain);

        authenticateForDomain(domain);

        FileObject[] contentFiles = null;
        try (FileObject rootDir = fsFilesManager.setUpFileSystem(domain);
             FileObject outgoingFolder = fsFilesManager.getEnsureChildFolder(rootDir, FSFilesManager.OUTGOING_FOLDER)) {

            contentFiles = fsFilesManager.findAllDescendantFiles(outgoingFolder);
            LOG.trace("Found descendant files [{}] for output folder [{}]", contentFiles, outgoingFolder.getName().getPath());

            List<FileObject> processableFiles = filterProcessableFiles(outgoingFolder, contentFiles, domain);
            LOG.debug("Processable files [{}]", processableFiles);

            //we send the thread context manually since it will be lost in threads created by parallel stream
            Map<String, String> context = LOG.getCopyOfContextMap();
            processableFiles.parallelStream().forEach(file -> enqueueProcessableFileWithContext(file, context));

        } catch (FileSystemException ex) {
            LOG.error("Error sending messages", ex);
        } catch (FSSetUpException ex) {
            LOG.error("Error setting up folders for domain: " + domain, ex);
        } finally {
            if (contentFiles != null) {
                fsFilesManager.closeAll(contentFiles);
            }

            clearDomainContext();
            LOG.debug("Finished sending messages for domain [{}]", domain);
        }
    }

    protected void clearDomainContext() {
        LOG.removeMDC(DomibusLogger.MDC_USER);
        domainContextExtService.clearCurrentDomain();
    }

    /**
     * It will check authentication username and password presence
     *
     * @param domain
     */
    public void authenticateForDomain(String domain) throws AuthenticationExtException {

        if (!domibusConfigurationExtService.isSecuredLoginRequired()) {
            LOG.trace("Skip authentication for domain [{}]", domain);
            return;
        }

        String user = fsPluginProperties.getAuthenticationUser(domain);
        if (user == null) {
            LOG.error("Authentication User not defined for domain [{}]", domain);
            throw new AuthenticationExtException(DomibusErrorCode.DOM_002, "Authentication User not defined for domain [" + domain + "]");
        }

        String password = fsPluginProperties.getAuthenticationPassword(domain);
        if (password == null) {
            LOG.error("Authentication Password not defined for domain [{}]", domain);
            throw new AuthenticationExtException(DomibusErrorCode.DOM_002, "Authentication Password not defined for domain [" + domain + "]");
        }

        authenticationExtService.basicAuthenticate(user, password);
    }

    /**
     * process the file - to be called by JMS message listener
     *
     * @param processableFile
     * @param domain
     */
    public void processFileSafely(FileObject processableFile, String domain) {
        String errorMessage = null;
        try {
            fsProcessFileService.processFile(processableFile, domain);
        } catch (JAXBException ex) {
            errorMessage = buildErrorMessage("Invalid metadata file: " + ex.toString()).toString();
            LOG.error(errorMessage, ex);
        } catch (MessagingProcessingException | XMLStreamException ex) {
            errorMessage = buildErrorMessage("Error occurred submitting message to Domibus: " + ex.getMessage()).toString();
            LOG.error(errorMessage, ex);
        } catch (RuntimeException | IOException ex) {
            errorMessage = buildErrorMessage("Error processing file. Skipped it. Error message is: " + ex.getMessage()).toString();
            LOG.error(errorMessage, ex);
        } finally {
            if (errorMessage != null) {
                handleSendFailedMessage(processableFile, domain, errorMessage);
            }
        }
    }

    public void handleSendFailedMessage(FileObject processableFile, String domain, String errorMessage) {
        if (processableFile == null) {
            LOG.error("The send failed message file was not found in domain [{}]", domain);
            return;
        }
        try {
            fsFilesManager.deleteLockFile(processableFile);
        } catch (FileSystemException e) {
            LOG.error("Error deleting lock file", e);
        }

        try (FileObject rootDir = fsFilesManager.setUpFileSystem(domain)) {
            String baseName = processableFile.getName().getBaseName();
            String errorFileName = fsFileNameHelper.stripStatusSuffix(baseName) + ERROR_EXTENSION;

            String processableFileMessageURI = processableFile.getParent().getName().getPath();
            String failedDirectoryLocation = fsFileNameHelper.deriveFailedDirectoryLocation(processableFileMessageURI);
            try (FileObject failedDirectory = fsFilesManager.getEnsureChildFolder(rootDir, failedDirectoryLocation)) {
                try {
                    if (fsPluginProperties.isFailedActionDelete(domain)) {
                        // Delete
                        fsFilesManager.deleteFile(processableFile);
                        LOG.debug("Send failed message file [{}] was deleted", processableFile.getName().getBaseName());
                    } else if (fsPluginProperties.isFailedActionArchive(domain)) {
                        // Archive
                        String archivedFileName = fsFileNameHelper.stripStatusSuffix(baseName);
                        try (FileObject archivedFile = failedDirectory.resolveFile(archivedFileName)) {
                            fsFilesManager.moveFile(processableFile, archivedFile);
                            LOG.debug("Send failed message file [{}] was archived into [{}]", processableFile, archivedFile.getName().getURI());
                        }
                    }
                } finally {
                    // Create error file
                    fsFilesManager.createFile(failedDirectory, errorFileName, errorMessage);
                }
            }
        } catch (IOException e) {
            throw new FSPluginException("Error handling the send failed message file " + processableFile, e);
        }
    }

    public StringBuilder buildErrorMessage(String errorDetail) {
        return buildErrorMessage(null, errorDetail, null, null, null, null);
    }

    public StringBuilder buildErrorMessage(String errorCode, String errorDetail, String messageId, String mshRole, String notified, String timestamp) {
        StringBuilder sb = new StringBuilder();
        if (errorCode != null) {
            sb.append("errorCode: ").append(errorCode).append(LS);
        }
        sb.append("errorDetail: ").append(errorDetail).append(LS);
        if (messageId != null) {
            sb.append("messageInErrorId: ").append(messageId).append(LS);
        }
        if (mshRole != null) {
            sb.append("mshRole: ").append(mshRole).append(LS);
        } else {
            sb.append("mshRole: ").append(MSHRole.SENDING).append(LS);
        }
        if (notified != null) {
            sb.append("notified: ").append(notified).append(LS);
        }
        if (timestamp != null) {
            sb.append("timestamp: ").append(timestamp).append(LS);
        } else {
            sb.append("timestamp: ").append(LocalDateTime.now()).append(LS);
        }

        return sb;
    }

    protected List<FileObject> filterProcessableFiles(FileObject rootFolder, FileObject[] files, String domain) {
        List<FileObject> filteredFiles = new LinkedList<>();

        List<String> lockedFileNames = Arrays.stream(files)
                .filter(f -> fsFileNameHelper.isLockFile(f.getName().getBaseName()))
                .map(f -> fsFileNameHelper.getRelativeName(rootFolder, f))
                .filter(Optional::isPresent)
                .map(fname -> fsFileNameHelper.stripLockSuffix(fname.get()))
                .collect(Collectors.toList());

        for (FileObject file : files) {
            String fileName = file.getName().getBaseName();

            if (!isMetadata(fileName)
                    && !fsFileNameHelper.isAnyState(fileName)
                    && !fsFileNameHelper.isProcessed(fileName)
                    // exclude lock files:
                    && !fsFileNameHelper.isLockFile(fileName)
                    // exclude locked files:
                    && !isLocked(lockedFileNames, fsFileNameHelper.getRelativeName(rootFolder, file))
                    // exclude files that are (or could be) in use by other processes:
                    && canReadFileSafely(file, domain)) {
                filteredFiles.add(file);
            }
        }

        return filteredFiles;
    }

    protected boolean isMetadata(String baseName) {
        return StringUtils.equals(baseName, METADATA_FILE_NAME);
    }

    protected boolean isLocked(List<String> lockedFileNames, Optional<String> fileName) {
        return fileName.isPresent()
                && lockedFileNames.stream().anyMatch(fname -> fname.equals(fileName.get()));
    }

    protected boolean canReadFileSafely(FileObject fileObject, String domain) {
        String filePath = fileObject.getName().getPath();

        if (!checkFileExists(fileObject)) {
            LOG.debug("Could not process file [{}] because it does not exist anymore.", filePath);
            return false;
        }

        if (checkSizeChangedRecently(fileObject, domain)) {
            LOG.debug("Could not process file [{}] because its size has changed recently.", filePath);
            return false;
        }

        if (checkTimestampChangedRecently(fileObject, domain)) {
            LOG.debug("Could not process file [{}] because its timestamp has changed recently.", filePath);
            return false;
        }

        if (checkHasWriteLock(fileObject)) {
            LOG.debug("Could not process file [{}] because it has a write lock.", filePath);
            return false;
        }

        LOG.debug("Could read file [{}] successfully.", filePath);
        return true;
    }

    protected boolean checkSizeChangedRecently(FileObject fileObject, String domain) {
        long delta = fsPluginProperties.getSendDelay(domain);
        //disable check if delay is 0
        if (delta == 0) {
            return false;
        }
        String filePath = fileObject.getName().getPath();
        String key = filePath;
        try {
            long currentFileSize = fileObject.getContent().getSize();
            long currentTime = new Date().getTime();

            FileInfo fileInfo = observedFilesInfo.get(key);
            if (fileInfo == null || fileInfo.getSize() != currentFileSize) {
                observedFilesInfo.put(key, new FileInfo(currentFileSize, currentTime, domain));
                LOG.debug("Could not process file [{}] because its size has changed recently", filePath);
                return true;
            }

            long elapsed = currentTime - fileInfo.getModified(); // time passed since last size change
            // if the file size has changed recently, probably some process is still writing to the file
            if (elapsed < delta) {
                LOG.debug("Could not process file [{}] because its size has changed recently: [{}] ms", filePath, elapsed);
                return true;
            }
        } catch (FileSystemException e) {
            LOG.warn("Could not determine file info for file [{}] ", filePath, e);
            return true;
        }

        return false;
    }

    protected void clearObservedFiles(String domain) {
        LOG.trace("Starting clear of the observed files for domain [{}]; there are [{}] entries", domain, observedFilesInfo.size());

        int delta = 2 * fsPluginProperties.getSendWorkerInterval(domain) + fsPluginProperties.getSendDelay(domain);
        long currentTime = new Date().getTime();
        String[] keys = observedFilesInfo.keySet().toArray(new String[]{});
        for (String key : keys) {
            FileInfo fileInfo = observedFilesInfo.get(key);
            if (fileInfo.getDomain().equals(domain) && ((currentTime - fileInfo.getModified()) > delta)) {
                LOG.debug("File [{}] is old and will not be observed anymore", key);
                observedFilesInfo.remove(key);
            }
        }

        LOG.trace("Ending clear of the observed files for domain [{}]; there are [{}] entries", domain, observedFilesInfo.size());
    }

    protected boolean checkTimestampChangedRecently(FileObject fileObject, String domain) {
        long delta = fsPluginProperties.getSendDelay(domain);
        //disable check if delay is 0
        if (delta == 0) {
            return false;
        }
        String filePath = fileObject.getName().getPath();
        try {
            long fileTime = fileObject.getContent().getLastModifiedTime();
            long elapsed = new Date().getTime() - fileTime; // time passed since last file change
            // if the file timestamp is very recent it is probable that some process is still writing in the file
            if (elapsed < delta) {
                LOG.debug("Could not process file [{}] because it is too recent: [{}] ms", filePath, elapsed);
                return true;
            }
        } catch (FileSystemException e) {
            LOG.warn("Could not determine file date for file [{}] ", filePath, e);
            return true;
        }
        return false;
    }

    protected boolean checkHasWriteLock(FileObject fileObject) {
        // firstly try to lock the file
        // if this fails, it means that another process has an explicit lock on the file
        String filePath;
        if (fileObject.getName().getURI().startsWith("file://")) {
            //handle files that may be located on a different disk partition
            filePath = fileObject.getPath().toString();
            LOG.debug("Special case handling for acquiring lock on file: [{}] ", filePath);
        } else {
            filePath = fileObject.getName().getPath();
        }
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "rw");
             FileChannel fileChannel = raf.getChannel();
             FileLock lock = fileChannel.tryLock(0, 0, true)) {
            if (lock == null) {
                LOG.debug("Could not acquire lock on file [{}] ", filePath);
                return true;
            }
        } catch (Exception e) {
            LOG.debug("Could not acquire lock on file [{}] ", filePath, e);
            return true;
        }
        return false;
    }

    protected boolean checkFileExists(FileObject fileObject) {
        try {
            return fileObject.exists();
        } catch (FileSystemException e) {
            LOG.warn("Error while checking file [{}]", fileObject.getName().getPath(), e);
            return false;
        }
    }

    protected void enqueueProcessableFileWithContext(final FileObject fileObject, final Map<String, String> context) {
        if (context != null) {
            LOG.setContextMap(context);
        }

        this.enqueueProcessableFile(fileObject);
    }

    /**
     * Put a JMS message to FS Plugin Send queue
     *
     * @param fileObject
     */
    protected void enqueueProcessableFile(final FileObject fileObject) {

        String fileName;
        try {
            fileName = fileObject.getURL().getFile();
        } catch (FileSystemException e) {
            LOG.error("Exception while getting filename: ", e);
            return;
        }

        try {
            if (fsFilesManager.hasLockFile(fileObject)) {
                LOG.debug("Skipping file [{}]: it has a lock file associated", fileName);
                return;
            }
            fsFilesManager.createLockFile(fileObject);
        } catch (FileSystemException e) {
            LOG.error("Exception while checking file lock: ", e);
            return;
        }

        final JmsMessageDTO jmsMessage = JMSMessageDTOBuilder.
                create().
                property(MessageConstants.FILE_NAME, fileName).
                build();

        LOG.debug("send message: [{}] to fsPluginSendQueue for file: [{}]", jmsMessage, fileName);
        jmsExtService.sendMessageToQueue(jmsMessage, fsPluginSendQueue);
    }

}
