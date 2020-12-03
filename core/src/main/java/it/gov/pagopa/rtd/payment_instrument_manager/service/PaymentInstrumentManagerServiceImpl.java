package it.gov.pagopa.rtd.payment_instrument_manager.service;

import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.AzureBlobClient;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception.AzureBlobDirectAccessException;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception.AzureBlobUploadException;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.PaymentInstrumentManagerDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
class PaymentInstrumentManagerServiceImpl implements PaymentInstrumentManagerService {

    private final String containerReference;
    private final String blobReference;
    private final String exstractionFileName;
    private final PaymentInstrumentManagerDao paymentInstrumentManagerDao;
    private final AzureBlobClient azureBlobClient;


    @Autowired
    public PaymentInstrumentManagerServiceImpl(PaymentInstrumentManagerDao paymentInstrumentManagerDao,
                                               AzureBlobClient azureBlobClient,
                                               @Value("${blobStorageConfiguration.containerReference}") String containerReference,
                                               @Value("${blobStorageConfiguration.blobReferenceNoExtension}") String blobReferenceNoExtension) {
        this.paymentInstrumentManagerDao = paymentInstrumentManagerDao;
        this.azureBlobClient = azureBlobClient;
        this.containerReference = containerReference;
        this.blobReference = blobReferenceNoExtension.concat(".zip");
        this.exstractionFileName = blobReferenceNoExtension.concat(".csv");
    }

    @Override
    public String getDownloadLink() {
        if (log.isDebugEnabled()) {
            log.debug("PaymentInstrumentManagerServiceImpl.getDownloadLink");
        }

        try {
            return azureBlobClient.getDirectAccessLink(containerReference, blobReference);

        } catch (AzureBlobDirectAccessException e) {
            if (log.isErrorEnabled()) {
                log.error("Failed to get blob direct link", e);
            }
            throw new RuntimeException(e);
        }
    }

    @Scheduled(cron = "${batchConfiguration.paymentInstrumentsExtraction.cron}")
    public void generateFileForAcquirer() {
        log.info("PaymentInstrumentManagerServiceImpl.generateFileForAcquirer");
        uploadHashedPans(getActiveHashPANs());
    }

    private Set<String> getActiveHashPANs() {
        if (log.isDebugEnabled()) {
            log.debug("PaymentInstrumentManagerServiceImpl.getActiveHashPANs");
        }

        return new HashSet<>(paymentInstrumentManagerDao.getActiveHashPANs());
    }

    private void uploadHashedPans(Set<String> hashedPans) {
        if (log.isDebugEnabled()) {
            log.debug("PaymentInstrumentManagerServiceImpl.uploadHashedPans");
            log.debug("hashedPans.size = " + hashedPans.size());
        }

        if (log.isDebugEnabled()) {
            log.debug("Compressing hashed pans");
        }
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            final ZipOutputStream zip = new ZipOutputStream(outputStream);
            ZipEntry zipEntry = new ZipEntry(exstractionFileName);
            zip.putNextEntry(zipEntry);
            for (String hashPan : hashedPans) {
                zip.write(hashPan.getBytes());
                zip.write(System.lineSeparator().getBytes());
            }
            zip.close();
        } catch (IOException e) {
            if (log.isErrorEnabled()) {
                log.error("Failed to compress hashed pans list", e);
            }
            throw new RuntimeException(e);
        }

        if (log.isDebugEnabled()) {
            log.debug("Uploading compressed hashed pans");
        }
        try {

            azureBlobClient.upload(containerReference, blobReference, outputStream.toByteArray());
            log.info("Uploaded hashed pan list");

        } catch (AzureBlobUploadException e) {
            if (log.isErrorEnabled()) {
                log.error("Failed to upload blob to azure storage", e);
            }
            throw new RuntimeException(e);
        }
    }

}
