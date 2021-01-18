package it.gov.pagopa.rtd.payment_instrument_manager.service;

import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.AzureBlobClient;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception.AzureBlobDirectAccessException;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception.AzureBlobUploadException;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.PaymentInstrumentManagerDao;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
    private final Long pageSize;


    @Autowired
    public PaymentInstrumentManagerServiceImpl(
            PaymentInstrumentManagerDao paymentInstrumentManagerDao,
            AzureBlobClient azureBlobClient,
            @Value("${batchConfiguration.paymentInstrumentsExtraction.pageSize}") Long pageSize,
            @Value("${blobStorageConfiguration.containerReference}") String containerReference,
            @Value("${blobStorageConfiguration.blobReferenceNoExtension}") String blobReferenceNoExtension) {
        this.paymentInstrumentManagerDao = paymentInstrumentManagerDao;
        this.azureBlobClient = azureBlobClient;
        this.containerReference = containerReference;
        this.pageSize = pageSize;
        this.blobReference = blobReferenceNoExtension.concat(".zip");
        this.exstractionFileName = blobReferenceNoExtension.concat(".csv");
    }

    @Override
    public String getDownloadLink() {

        if (log.isInfoEnabled()) {
            log.info("PaymentInstrumentManagerServiceImpl.getDownloadLink");
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

        if (log.isInfoEnabled()) {
            log.info("PaymentInstrumentManagerServiceImpl.generateFileForAcquirer");
        }

        refreshView();
        uploadHashedPans();
    }

    private void refreshView() {

        if (log.isInfoEnabled()) {
            log.info("PaymentInstrumentManagerServiceImpl.refreshView");
        }

        paymentInstrumentManagerDao.refreshView();
    }

    private Set<String> getActiveHashPANs(Long offset, Long size) {

        if (log.isInfoEnabled()) {
            log.info("PaymentInstrumentManagerServiceImpl.getActiveHashPANs");
        }

        return new HashSet<>(paymentInstrumentManagerDao.getActiveHashPANs(offset, size));
    }

    @SneakyThrows
    private void uploadHashedPans() {

        if (log.isInfoEnabled()) {
            log.info("PaymentInstrumentManagerServiceImpl.uploadHashedPans");
        }

        File file = Files.createTempFile(blobReference.split("\\.")[0], ".zip").toFile();
        FileOutputStream fileOutputStream = null;

        Path localFile = Files.createTempFile(exstractionFileName.split("\\.")[0],".csv");

        FileUtils.forceDelete(localFile.toFile());
        BufferedWriter bufferedWriter = Files.newBufferedWriter(localFile,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.APPEND);

        boolean executed = false;
        long offset = 0L;

        while (!executed) {

            Set<String> hashedPans = getActiveHashPANs(offset, pageSize);
            for (String hashPan : hashedPans) {
                bufferedWriter.write(hashPan.concat(System.lineSeparator()));
            }

            if (hashedPans.isEmpty() || hashedPans.size() < pageSize) {
                executed = true;
            } else {
                offset += pageSize;
            }

        }

        bufferedWriter.close();

        if (log.isInfoEnabled()) {
            log.info("Compressing hashed pans");
        }

        try {

            FileInputStream fileInputStream = new FileInputStream(localFile.toFile());
            BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
            fileOutputStream = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(fileOutputStream);
            final ZipOutputStream zip = new ZipOutputStream(bos);
            ZipEntry zipEntry = new ZipEntry(exstractionFileName);
            zip.putNextEntry(zipEntry);

            IOUtils.copy(bufferedInputStream, zip);

            zip.close();
        } catch (IOException e) {
            if (log.isErrorEnabled()) {
                log.error("Failed to compress hashed pans list", e);
            }
            throw new RuntimeException(e);
        } finally {
            assert fileOutputStream != null;
            fileOutputStream.close();
        }

        if (log.isInfoEnabled()) {
            log.info("Uploading compressed hashed pans");
        }
        try {
            azureBlobClient.upload(containerReference, blobReference, file.getAbsolutePath());
            FileUtils.forceDelete(file);
            if (log.isInfoEnabled()) {
                log.info("Uploaded hashed pan list");
            }
        } catch (AzureBlobUploadException e) {
            if (log.isErrorEnabled()) {
                log.error("Failed to upload blob to azure storage", e);
            }
            throw new RuntimeException(e);
        }
    }

}
