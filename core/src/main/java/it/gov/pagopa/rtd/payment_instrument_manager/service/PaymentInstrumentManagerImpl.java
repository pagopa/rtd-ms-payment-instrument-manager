package it.gov.pagopa.rtd.payment_instrument_manager.service;

import com.microsoft.azure.storage.*;
import com.microsoft.azure.storage.blob.*;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.bpd.BpdPaymentInstrumentDao;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.fa.FaPaymentInstrumentDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
class PaymentInstrumentManagerImpl implements PaymentInstrumentManager {

    public static final String storageConnectionString = "UseDevelopmentStorage=true;";
    //"DefaultEndpointsProtocol=https;" +
    //"AccountName=<account-name>;" +
    //"AccountKey=<account-key>";

    public static final String containerReference = "testcontainer";
    public static final String blobReference = "hashPANs.csv";

    private final BpdPaymentInstrumentDao bpdPaymentInstrumentDao;
    private final FaPaymentInstrumentDao faPaymentInstrumentDao;

    @Autowired
    public PaymentInstrumentManagerImpl(BpdPaymentInstrumentDao bpdPaymentInstrumentDao, FaPaymentInstrumentDao faPaymentInstrumentDao) {
        this.bpdPaymentInstrumentDao = bpdPaymentInstrumentDao;
        this.faPaymentInstrumentDao = faPaymentInstrumentDao;
    }

    public static void uploadBLOB() throws IOException {
        final Set<String> activeHashPANs = new HashSet<>();
        activeHashPANs.add("NFGDJNFIDSANOI");
        activeHashPANs.add("H5FD4GFD4G65FD");

        try {
            final CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString); // URISyntaxException, InvalidKeyException
            final CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
            final CloudBlobContainer blobContainer = blobClient.getContainerReference(containerReference);// StorageException

            // Create the container if it does not exist with public access.
            System.out.println("Creating container: " + blobContainer.getName());
            blobContainer.createIfNotExists(BlobContainerPublicAccessType.OFF, new BlobRequestOptions(), new OperationContext());

            //Getting a blob reference
            final CloudBlockBlob blob = blobContainer.getBlockBlobReference(blobReference);

            //Creating blob and uploading file to it
            System.out.println("Uploading the sample file ");
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (String hashPAN : activeHashPANs) {
                outputStream.write((hashPAN + System.lineSeparator()).getBytes());
            }
            final byte[] content = outputStream.toByteArray();
            blob.uploadFromByteArray(content, 0, content.length);
            outputStream.close();

            //Listing contents of container
            for (ListBlobItem blobItem : blobContainer.listBlobs()) {
                System.out.println("URI of blob is: " + blobItem.getUri());
            }

        } catch (URISyntaxException | InvalidKeyException e) {
            e.printStackTrace();
        } catch (StorageException e) {
            System.out.println(String.format("Error returned from the service. Http code: %d and error code: %s", e.getHttpStatusCode(), e.getErrorCode()));
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeyException, StorageException, URISyntaxException {
//        uploadBLOB();
        downloadBLOB();
    }

    public static void downloadBLOB() throws URISyntaxException, NoSuchAlgorithmException, InvalidKeyException, StorageException {
        final CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString); // URISyntaxException, InvalidKeyException
        final CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
        final CloudBlobContainer blobContainer = blobClient.getContainerReference(containerReference);// StorageException

        if (blobContainer.exists()) {
            System.out.println("blobContainer " + blobContainer.getName() + " exists");
            final CloudBlockBlob blob = blobContainer.getBlockBlobReference("hashPANs.csv");

            if (blob.exists()) {
                System.out.println("blob " + blob.getName() + " exists");
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
                System.out.println("sas = " + sas);
                System.out.println("blobUri = " + blob.getUri().toString() + "?" + sas);

            } else {
                System.out.println("blob " + blob.getName() + " not exists");
            }

        } else {
            System.out.println("blobContainer " + blobContainer.getName() + " not exists");
        }
    }

    @Scheduled(cron = "${batchConfiguration.paymentInstrumentsForAcquirer.cron}")
    public void generateFileForAcquirer() throws IOException {
        final Set<String> activeHashPANs = getActiveHashPANs();
    }

    @Override
    public Set<String> getActiveHashPANs() {
        final Set<String> result = new HashSet<>();

        final List<String> bpdActivePaymentInstruments = bpdPaymentInstrumentDao.getActiveHashPANs();
        result.addAll(bpdActivePaymentInstruments);

//        final List<String> faActivePaymentInstruments = faPaymentInstrumentDao.getActiveHashPANs();
//        result.addAll(faActivePaymentInstruments);

        return result;
    }

}
