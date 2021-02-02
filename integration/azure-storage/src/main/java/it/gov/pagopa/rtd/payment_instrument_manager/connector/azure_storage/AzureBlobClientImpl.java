package it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.SharedAccessProtocols;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.*;
import it.gov.pagopa.bpd.common.exception.ResourceNotFoundException;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception.AzureBlobDirectAccessException;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception.AzureBlobUploadException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;

@Service
@Slf4j
class AzureBlobClientImpl implements AzureBlobClient {

    private final String storageConnectionString;
    private final long expiryTime;
    private final String protocol;

    public AzureBlobClientImpl(@Value("${blobStorageConfiguration.storageConnectionString}") String storageConnectionString,
                               @Value("${blobStorageConfiguration.sharedAccess.expiryTime}") long expiryTime,
                               @Value("${blobStorageConfiguration.sharedAccess.protocol}") String protocol) {
        this.storageConnectionString = storageConnectionString;
        this.expiryTime = expiryTime;
        this.protocol = protocol;
    }

    @Override
    public String getDirectAccessLink(String containerReference, String blobReference) throws AzureBlobDirectAccessException {
        if (log.isDebugEnabled()) {
            log.debug("AzureBlobClientImpl.getDirectAccessLink");
            log.debug("containerReference = " + containerReference + ", blobReference = " + blobReference);
        }

        CloudBlockBlob blob = null;
        String sas = null;
        try {
            final CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString);
            final CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
            final CloudBlobContainer blobContainer = blobClient.getContainerReference(containerReference);

            if (!blobContainer.exists()) {
                if (log.isErrorEnabled()) {
                    log.error(String.format("blobContainer %s not exists", blobContainer.getName()));
                }
                throw new ResourceNotFoundException(CloudBlobContainer.class, containerReference);
            }

            if (log.isDebugEnabled()) {
                log.debug(String.format("blobContainer %s exists%n", blobContainer.getName()));
            }
            blob = blobContainer.getBlockBlobReference(blobReference);

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

            sas = blob.generateSharedAccessSignature(sharedAccessBlobPolicy,
                    null,
                    null,
                    null,
                    Arrays.stream(SharedAccessProtocols.values()).filter(p -> p.toString().equals(protocol)).findAny().get());
            if (log.isDebugEnabled()) {
                log.debug(String.format("sas = %s", sas));
            }

        } catch (URISyntaxException | InvalidKeyException | StorageException e) {
            throw new AzureBlobDirectAccessException(e);
        }

        return String.format("%s?%s", blob.getUri().toString(), sas);
    }

    @Override
    public void upload(String containerReference, String blobReference, String zipFile) throws AzureBlobUploadException {
        if (log.isDebugEnabled()) {
            log.debug("AzureBlobClientImpl.upload");
            log.debug("containerReference = " + containerReference + ", blobReference = " + blobReference + ", zipFile = " + zipFile);
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
            blob.getMetadata().put("sha256", DigestUtils.sha256Hex(FileUtils.openInputStream(new File(zipFile))));
            blob.uploadFromFile(zipFile);

            if (log.isDebugEnabled()) {
                log.debug(String.format("Uploaded %s", blobReference));
            }

        } catch (StorageException | InvalidKeyException | URISyntaxException | IOException e) {
            throw new AzureBlobUploadException(e);
        }
    }

}
