package it.gov.pagopa.rtd.payment_instrument_manager.service;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.SharedAccessProtocols;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.*;
import it.gov.pagopa.bpd.common.exception.ResourceNotFoundException;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.PaymentInstrumentManagerDao;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
class PaymentInstrumentManagerServiceImpl implements PaymentInstrumentManagerService {

    private final String storageConnectionString;
    private final String containerReference;
    private final String blobReference;
    private final String exstractionFileName;
    private final long expiryTime;
    private final String protocol;
    private final Boolean useHttp;
    private final PaymentInstrumentManagerDao paymentInstrumentManagerDao;


    @Autowired
    public PaymentInstrumentManagerServiceImpl(PaymentInstrumentManagerDao paymentInstrumentManagerDao,
                                               @Value("${blobStorageConfiguration.storageConnectionString}") String storageConnectionString,
                                               @Value("${blobStorageConfiguration.containerReference}") String containerReference,
                                               @Value("${blobStorageConfiguration.blobReferenceNoExtension}") String blobReferenceNoExtension,
                                               @Value("${blobStorageConfiguration.sharedAccess.expiryTime}") long expiryTime,
                                               @Value("${blobStorageConfiguration.sharedAccess.protocol}") String protocol,
                                               @Value("${blobStorageConfiguration.sharedAccess.useHttp}") Boolean useHttp) {
        this.paymentInstrumentManagerDao = paymentInstrumentManagerDao;
        this.storageConnectionString = storageConnectionString;
        this.containerReference = containerReference;
        this.blobReference = blobReferenceNoExtension.concat(".zip");
        this.exstractionFileName = blobReferenceNoExtension.concat(".csv");
        this.expiryTime = expiryTime;
        this.protocol = protocol;
        this.useHttp = useHttp;
    }

    public String getDownloadLink() throws URISyntaxException, InvalidKeyException, StorageException, ResourceNotFoundException {
        final CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString);
        final CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
        final CloudBlobContainer blobContainer = blobClient.getContainerReference(containerReference);

        if (!blobContainer.exists()) {
            if (log.isErrorEnabled()) {
                log.error(String.format("blobContainer %s not exists", blobContainer.getName()));
            }
            throw new ResourceNotFoundException(CloudBlobContainer.class, containerReference);
        }

        if (log.isErrorEnabled()) {
            log.error(String.format("blobContainer %s exists%n", blobContainer.getName()));
        }
        final CloudBlockBlob blob = blobContainer.getBlockBlobReference(blobReference);

        if (!blob.exists()) {
            if (log.isErrorEnabled()) {
                log.error(String.format("blob %s not exists", blob.getName()));
            }
            throw new ResourceNotFoundException(CloudBlockBlob.class, blobReference);
        }

        if (log.isDebugEnabled()) {
            log.debug(String.format("blobUri = %s", blob.getUri()));
        }
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        final SharedAccessBlobPolicy sharedAccessBlobPolicy = new SharedAccessBlobPolicy();
        sharedAccessBlobPolicy.setPermissions(EnumSet.of(SharedAccessBlobPermissions.READ));
        sharedAccessBlobPolicy.setSharedAccessStartTime(Date.from(now.toInstant()));
        sharedAccessBlobPolicy.setSharedAccessExpiryTime(Date.from(now.plusMinutes(expiryTime).toInstant()));

        final String sas = blob.generateSharedAccessSignature(sharedAccessBlobPolicy,
                null,
                null,
                null,
                Arrays.stream(SharedAccessProtocols.values()).filter(p -> p.toString().equals(protocol)).findAny().get());
        if (log.isDebugEnabled()) {
            log.debug(String.format("sas = %s", sas));
        }

        URI uri = blob.getUri();
        return String.format("%s?%s",
                protocol.contains("http") && useHttp ?
                        uri.toString().replace("https", "http") :
                        uri.toString(), sas);
    }


    private Set<String> getActiveHashPANs() {
        if (log.isDebugEnabled()) {
            log.debug("PaymentInstrumentManagerServiceImpl.getActiveHashPANs");
        }
        return new HashSet<>(paymentInstrumentManagerDao.getActiveHashPANs());
    }

    @Scheduled(cron = "${batchConfiguration.paymentInstrumentsExtraction.cron}")
    public void generateFileForAcquirer() {
        if (log.isDebugEnabled()) {
            log.debug("PaymentInstrumentManagerServiceImpl.generateFileForAcquirer");
        }
        uploadHashedPans(getActiveHashPANs());
    }

    private void uploadHashedPans(Set<String> hashedPans) {
        if (log.isDebugEnabled()) {
            log.debug("PaymentInstrumentManagerServiceImpl.uploadHashedPans");
        }
        try {
            final CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString);
            final CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
            final CloudBlobContainer blobContainer = blobClient.getContainerReference(containerReference);

            if (log.isDebugEnabled()) {
                log.debug("Creating container: " + blobContainer.getName());
            }
            blobContainer.createIfNotExists(BlobContainerPublicAccessType.OFF, new BlobRequestOptions(), new OperationContext());

            //TODO: create a blob with current date
            final CloudBlockBlob blob = blobContainer.getBlockBlobReference(blobReference);

            if (log.isDebugEnabled()) {
                log.debug("Compressing hashed pans");
            }
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final ZipOutputStream zip = new ZipOutputStream(outputStream);
            ZipEntry zipEntry = new ZipEntry(exstractionFileName);
            zip.putNextEntry(zipEntry);
            for (String hashPan : hashedPans) {
                zip.write(hashPan.getBytes());
                zip.write(System.lineSeparator().getBytes());
            }
            zip.close();

            if (log.isDebugEnabled()) {
                log.debug("Uploading compressed hashed pans");
            }
            final byte[] content = outputStream.toByteArray();
            blob.getMetadata().put("sha256", DigestUtils.sha256Hex(content));
            blob.uploadFromByteArray(content, 0, content.length);

        } catch (URISyntaxException | InvalidKeyException | IOException e) {
            //TODO manage error
            e.printStackTrace();
        } catch (StorageException e) {
            //TODO manage error
            System.out.println(String.format("Error returned from the service. Http code: %d and error code: %s", e.getHttpStatusCode(), e.getErrorCode()));
            e.printStackTrace();
        }
    }

}
