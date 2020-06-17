package it.gov.pagopa.rtd.payment_instrument_manager.service;

import com.microsoft.azure.storage.*;
import com.microsoft.azure.storage.blob.*;
import it.gov.pagopa.bpd.common.exception.ResourceNotFoundException;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.PaymentInstrumentManagerDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
class PaymentInstrumentManagerServiceImpl implements PaymentInstrumentManagerService {

    private final String storageConnectionString;
    //"DefaultEndpointsProtocol=https;" +
    //"AccountName=<account-name>;" +
    //"AccountKey=<account-key>";
    private final String containerReference;
    private final String blobReference;
    private final PaymentInstrumentManagerDao paymentInstrumentManagerDao;


    @Autowired
    public PaymentInstrumentManagerServiceImpl(PaymentInstrumentManagerDao paymentInstrumentManagerDao,
                                               @Value("${blobStorageConfiguration.storageConnectionString}") String storageConnectionString,
                                               @Value("${blobStorageConfiguration.containerReference}") String containerReference,
                                               @Value("${blobStorageConfiguration.blobReference}") String blobReference) {
        this.paymentInstrumentManagerDao = paymentInstrumentManagerDao;
        this.storageConnectionString = storageConnectionString;
        this.containerReference = containerReference;
        this.blobReference = blobReference;
    }


    @Scheduled(cron = "${batchConfiguration.paymentInstrumentsForAcquirer.cron}")
    public void generateFileForAcquirer() {
        if (log.isDebugEnabled()) {
            log.debug("PaymentInstrumentManagerServiceImpl.generateFileForAcquirer");
        }
        uploadHashedPans(getActiveHashPANs());
    }


    private Set<String> getActiveHashPANs() {
        if (log.isDebugEnabled()) {
            log.debug("PaymentInstrumentManagerServiceImpl.getActiveHashPANs");
        }
        return new HashSet<>(paymentInstrumentManagerDao.getActiveHashPANs());
    }


    public void uploadHashedPans(Set<String> hashedPans) {
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

            final CloudBlockBlob blob = blobContainer.getBlockBlobReference(blobReference);

            if (log.isDebugEnabled()) {
                log.debug("Uploading hashed pans");
            }
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (String hashPAN : hashedPans) {
                outputStream.write((hashPAN + System.lineSeparator()).getBytes());
            }
            final byte[] content = outputStream.toByteArray();
            blob.uploadFromByteArray(content, 0, content.length);
            outputStream.close();

        } catch (URISyntaxException | InvalidKeyException | IOException e) {
            e.printStackTrace();
        } catch (StorageException e) {
            System.out.println(String.format("Error returned from the service. Http code: %d and error code: %s", e.getHttpStatusCode(), e.getErrorCode()));
            e.printStackTrace();
        }
    }


    public String getDownloadLink() throws URISyntaxException, InvalidKeyException, StorageException {
        final String result;

        final CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString);
        final CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
        final CloudBlobContainer blobContainer = blobClient.getContainerReference(containerReference);

        if (!blobContainer.exists()) {
            if (log.isErrorEnabled()) {
                log.error(String.format("blobContainer %s not exists", blobContainer.getName()));
            }
            throw new ResourceNotFoundException(CloudBlobContainer.class, containerReference);
        }

        System.out.printf("blobContainer %s exists%n", blobContainer.getName());
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
        sharedAccessBlobPolicy.setSharedAccessExpiryTime(Date.from(now.plus(30, ChronoUnit.SECONDS).toInstant()));

//                final String sas = blob.generateSharedAccessSignature(sharedAccessBlobPolicy, null);
        final String sas = blob.generateSharedAccessSignature(sharedAccessBlobPolicy,
                null,
                null,
                new IPRange("127.0.0.1"),
                SharedAccessProtocols.HTTPS_HTTP);
        if (log.isDebugEnabled()) {
            log.debug(String.format("sas = %s", sas));
        }
        result = String.format("%s?%s", blob.getUri(), sas);

        return result;
    }

}
