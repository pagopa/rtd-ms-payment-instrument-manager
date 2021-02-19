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
    private final Long pageSize;
    private final Long numberPerFile;
    private final Boolean createPartialFile;
    private final Boolean createGeneralFile;


    @Autowired
    public PaymentInstrumentManagerServiceImpl(
            PaymentInstrumentManagerDao paymentInstrumentManagerDao,
            AzureBlobClient azureBlobClient,
            @Value("${batchConfiguration.paymentInstrumentsExtraction.pageSize}") Long pageSize,
            @Value("${blobStorageConfiguration.containerReference}") String containerReference,
            @Value("${blobStorageConfiguration.blobReferenceNoExtension}") String blobReferenceNoExtension,
            @Value("${batchConfiguration.paymentInstrumentsExtraction.numberPerFile}") Long numberPerFile,
            @Value("${batchConfiguration.paymentInstrumentsExtraction.createGeneralFile}") Boolean createGeneralFile,
            @Value("${batchConfiguration.paymentInstrumentsExtraction.createPartialFile}") Boolean createPartialFile
    ) {
        this.paymentInstrumentManagerDao = paymentInstrumentManagerDao;
        this.azureBlobClient = azureBlobClient;
        this.containerReference = containerReference;
        this.pageSize = pageSize;
        this.numberPerFile = numberPerFile;
        this.blobReference = blobReferenceNoExtension.concat(".zip");
        this.exstractionFileName = blobReferenceNoExtension.concat(".csv");
        this.createGeneralFile = createGeneralFile;
        this.createPartialFile = createPartialFile;
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

        String startExecutionDate = paymentInstrumentManagerDao.getRtdExecutionDate();

        writeBpdHpansToRtd(startExecutionDate, startDate, endDate);
        writeFaHpansToRtd(startExecutionDate);

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

        refreshActiveHpans();

        BufferedWriter generalBufferedWriter = null;
        Path generalZippedFile = null;
        Path generalLocalFile = null;
        if (createGeneralFile) {
            generalZippedFile = Files.createTempFile(blobReference.split("\\.")[0], ".zip");
            generalLocalFile = Files.createTempFile("tempFile".split("\\.")[0], ".csv");

            FileUtils.forceDelete(generalLocalFile.toFile());
            FileUtils.forceDelete(generalZippedFile.toFile());

            generalBufferedWriter = Files.newBufferedWriter(generalLocalFile,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        }

        boolean executed = false;
        long offset = 0L;
        long currentId = 1;

        BufferedWriter tempBufferedWriter = null;
        Path tempFileZippedFile = null;
        Path tempFileLocalFile = null;
        if (createPartialFile) {
            tempFileZippedFile = Files.createTempFile(blobReference.split("\\.")[0]
                    .concat("_").concat(String.valueOf(currentId)), ".zip");
            tempFileLocalFile = Files.createTempFile("tempFile".split("\\.")[0]
                    .concat("_").concat(String.valueOf(currentId)), ".csv");

            FileUtils.forceDelete(tempFileZippedFile.toFile());
            FileUtils.forceDelete(tempFileLocalFile.toFile());
            
            tempBufferedWriter = Files.newBufferedWriter(
                    tempFileLocalFile, StandardOpenOption.CREATE_NEW, 
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        }

        while (!executed) {

            Set<String> hashedPans = getActiveHashPANs(offset, pageSize);
             for (String hashPan : hashedPans) {
                 if (createGeneralFile) {
                     generalBufferedWriter.write(hashPan.concat(System.lineSeparator()));
                 }
                if (createPartialFile) {
                    tempBufferedWriter.write(hashPan.concat(System.lineSeparator()));
                }
            }

            if (hashedPans.isEmpty() || hashedPans.size() < pageSize) {
                executed = true;
            } else {
                offset += pageSize;
            }

            if (createGeneralFile) {
                generalBufferedWriter.flush();
            }

            if (createPartialFile) {
                tempBufferedWriter.flush();

                if (offset % numberPerFile == 0) {
                    tempBufferedWriter.close();

                    zipAndUpload(tempFileLocalFile, tempFileZippedFile, currentId, currentId+1);

                    currentId = currentId + 1;

                    tempFileZippedFile = Files.createTempFile(blobReference.split("\\.")[0]
                            .concat("_").concat(String.valueOf(currentId)), ".zip");
                    tempFileLocalFile = Files.createTempFile("tempFile".split("\\.")[0]
                            .concat("_").concat(String.valueOf(currentId)), ".csv");

                    FileUtils.forceDelete(tempFileZippedFile.toFile());
                    FileUtils.forceDelete(tempFileLocalFile.toFile());

                    tempBufferedWriter = Files.newBufferedWriter(tempFileLocalFile,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                }
            }

        }

        if (createPartialFile) {
            tempBufferedWriter.close();
            zipAndUpload(tempFileLocalFile, tempFileZippedFile, currentId, null);
        }

        if (createGeneralFile) {
            generalBufferedWriter.close();
            zipAndUpload(generalLocalFile, generalZippedFile, null,null);
        }

    }

    @SneakyThrows
    private void zipAndUpload(Path localFile, Path zippedFile, Long currentFile, Long nextFile) {

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

            azureBlobClient.upload(containerReference,
                    currentFile == null ? blobReference : blobReference.concat(String.valueOf(currentFile)),
                    zippedFile.toFile().getAbsolutePath(), String.valueOf(nextFile));
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
                        .getBPDActiveHashPANs(executionDate, startDate, endDate, offset, pageSize);

                paymentInstrumentManagerDao.insertPaymentInstruments(hashedPans);

                if (hashedPans.isEmpty() || hashedPans.size() < pageSize) {
                    executed = true;
                } else {
                    offset += pageSize;
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
                        .getFAActiveHashPANs(executionDate, offset, pageSize);

                paymentInstrumentManagerDao.insertPaymentInstruments(hashedPans);

                if (hashedPans.isEmpty() || hashedPans.size() < pageSize) {
                    executed = true;
                } else {
                    offset += pageSize;
                }

            }

        } catch (Exception e) {
            log.error(e.getMessage(),e);
            throw e;
        }

    }

}
