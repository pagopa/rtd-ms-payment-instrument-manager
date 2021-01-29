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
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
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
    private final Long extractionPageSize;
    private final Long insertPageSize;
    private final int insertBatchSize;
    private final Long deletePageSize;
    private final int deleteBatchSize;



    @Autowired
    public PaymentInstrumentManagerServiceImpl(
            PaymentInstrumentManagerDao paymentInstrumentManagerDao,
            AzureBlobClient azureBlobClient,
            @Value("${batchConfiguration.paymentInstrumentsExtraction.extraction.pageSize}") Long extractionPageSize,
            @Value("${batchConfiguration.paymentInstrumentsExtraction.insert.pageSize}") Long insertPageSize,
            @Value("${batchConfiguration.paymentInstrumentsExtraction.insert.batchSize}") int insertBatchSize,
            @Value("${batchConfiguration.paymentInstrumentsExtraction.delete.pageSize}") Long deletePageSize,
            @Value("${batchConfiguration.paymentInstrumentsExtraction.delete.batchSize}") int deleteBatchSize,
            @Value("${blobStorageConfiguration.containerReference}") String containerReference,
            @Value("${blobStorageConfiguration.blobReferenceNoExtension}") String blobReferenceNoExtension) {
        this.paymentInstrumentManagerDao = paymentInstrumentManagerDao;
        this.azureBlobClient = azureBlobClient;
        this.containerReference = containerReference;
        this.extractionPageSize = extractionPageSize;
        this.insertPageSize = insertPageSize;
        this.insertBatchSize = insertBatchSize;
        this.deletePageSize = deletePageSize;
        this.deleteBatchSize = deleteBatchSize;
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

        uploadHashedPans();

    }

    public void refreshActiveHpans() {

        if (log.isInfoEnabled()) {
            log.info("PaymentInstrumentManagerServiceImpl.refreshActiveHpans");
        }

        Map<String,Object> awardPeriodData = paymentInstrumentManagerDao.getAwardPeriods();
        String startDate = String.valueOf(awardPeriodData.get("start_date"));
        String endDate = String.valueOf(awardPeriodData.get("end_date"));

        String saveExecutionDate = OffsetDateTime.now().toString();

        Map<String,Object> executionDates = paymentInstrumentManagerDao.getRtdExecutionDate();

        writeBpdHpansToRtd(String.valueOf(executionDates.get("bpd_exec_date")), startDate, endDate);
        writeFaHpansToRtd(String.valueOf(executionDates.get("fa_exec_date")));
        disableBpdHpans(String.valueOf(executionDates.get("bpd_exec_date")),endDate);
        disableFaHpans(String.valueOf(executionDates.get("fa_exec_date")));

        paymentInstrumentManagerDao.updateExecutionDate(saveExecutionDate);

    }

    private Set<String> getActiveHashPANs(Long offset, Long size) {

        if (log.isInfoEnabled()) {
            log.info("PaymentInstrumentManagerServiceImpl.getActiveHashPANs" +
                    " offset: " + offset + ", size: " + size);
        }

        return new HashSet<>(paymentInstrumentManagerDao.getActiveHashPANs(offset, size));
    }

    @SneakyThrows
    private void uploadHashedPans() {

        if (log.isInfoEnabled()) {
            log.info("PaymentInstrumentManagerServiceImpl.uploadHashedPans");
        }

        Path zippedFile = Files.createTempFile(blobReference.split("\\.")[0], ".zip");
        Path localFile = Files.createTempFile("tempFile".split("\\.")[0],".csv");

        FileUtils.forceDelete(localFile.toFile());
        FileUtils.forceDelete(zippedFile.toFile());

        refreshActiveHpans();

        BufferedWriter bufferedWriter = Files.newBufferedWriter(localFile,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.APPEND);

        boolean executed = false;
        long offset = 0L;

        while (!executed) {

            Set<String> hashedPans = getActiveHashPANs(offset, extractionPageSize);
            for (String hashPan : hashedPans) {
                bufferedWriter.write(hashPan.concat(System.lineSeparator()));
            }

            if (hashedPans.isEmpty() || hashedPans.size() < extractionPageSize) {
                executed = true;
            } else {
                offset += extractionPageSize;
            }

            bufferedWriter.flush();

        }

        bufferedWriter.close();

        if (log.isInfoEnabled()) {
            log.info("Compressing hashed pans");
        }

        FileInputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;
        BufferedInputStream bufferedInputStream = null;
        BufferedOutputStream bos;
        try {
            fileInputStream = new FileInputStream(localFile.toFile());
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            fileOutputStream = new FileOutputStream(zippedFile.toFile());
            bos = new BufferedOutputStream(fileOutputStream);
            ZipOutputStream zip = new ZipOutputStream(bos);
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
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            if (bufferedInputStream != null) {
                bufferedInputStream.close();
            }
        }

        if (log.isInfoEnabled()) {
            log.info("Uploading compressed hashed pans");
        }

        try {

            azureBlobClient.upload(containerReference, blobReference, zippedFile.toFile().getAbsolutePath());
            FileUtils.forceDelete(localFile.toFile());
            FileUtils.forceDelete(zippedFile.toFile());
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

    @SneakyThrows
    private void writeBpdHpansToRtd(String executionDate, String startDate, String endDate) {

        try {

            boolean executed = false;
            long offset = 0L;

            while (!executed) {

                List<String> hashedPans = paymentInstrumentManagerDao
                        .getBPDActiveHashPANs(executionDate, startDate, endDate, offset, insertPageSize);

                paymentInstrumentManagerDao.insertBpdPaymentInstruments(hashedPans,insertBatchSize);

                if (hashedPans.isEmpty() || hashedPans.size() < insertPageSize) {
                    executed = true;
                } else {
                    offset += insertPageSize;
                }

            }

        } catch (Exception e) {
            log.error(e.getMessage(),e);
            throw e;
        }

    }

    @SneakyThrows
    private void writeFaHpansToRtd(String executionDate) {

        try {

            boolean executed = false;
            long offset = 0L;

            while (!executed) {

                List<String> hashedPans = paymentInstrumentManagerDao
                        .getFAActiveHashPANs(executionDate, offset, insertPageSize);

                paymentInstrumentManagerDao.insertFaPaymentInstruments(hashedPans,insertBatchSize);

                if (hashedPans.isEmpty() || hashedPans.size() < insertPageSize) {
                    executed = true;
                } else {
                    offset += insertPageSize;
                }

            }

        } catch (Exception e) {
            log.error(e.getMessage(),e);
            throw e;
        }

    }

    private void disableBpdHpans(String executionDate, String startDate) {

        try {

            boolean executed = false;
            long offset = 0L;

            while (!executed) {

                List<String> hashedPans = paymentInstrumentManagerDao
                        .getBpdDisabledPans(executionDate, startDate, offset, deletePageSize);

                paymentInstrumentManagerDao.disableBpdPaymentInstruments(hashedPans,deleteBatchSize);

                if (hashedPans.isEmpty() || hashedPans.size() < deletePageSize) {
                    executed = true;
                } else {
                    offset += deletePageSize;
                }

            }

        } catch (Exception e) {
            log.error(e.getMessage(),e);
            throw e;
        }

    }

    private void disableFaHpans(String executionDate) {

        try {

            boolean executed = false;
            long offset = 0L;

            while (!executed) {

                List<String> hashedPans = paymentInstrumentManagerDao
                        .getFaDisabledPans(executionDate, offset, deletePageSize);

                paymentInstrumentManagerDao.disableFaPaymentInstruments(hashedPans, deleteBatchSize);

                if (hashedPans.isEmpty() || hashedPans.size() < deletePageSize) {
                    executed = true;
                } else {
                    offset += deletePageSize;
                }

            }

        } catch (Exception e) {
            log.error(e.getMessage(),e);
            throw e;
        }

    }

}
