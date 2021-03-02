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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final Long numberPerFile;
    private final Boolean createPartialFile;
    private final Boolean createGeneralFile;

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
            @Value("${blobStorageConfiguration.blobReferenceNoExtension}") String blobReferenceNoExtension,
            @Value("${batchConfiguration.paymentInstrumentsExtraction.numberPerFile}") Long numberPerFile,
            @Value("${batchConfiguration.paymentInstrumentsExtraction.createGeneralFile}") Boolean createGeneralFile,
            @Value("${batchConfiguration.paymentInstrumentsExtraction.createPartialFile}") Boolean createPartialFile
    ) {
        this.paymentInstrumentManagerDao = paymentInstrumentManagerDao;
        this.azureBlobClient = azureBlobClient;
        this.containerReference = containerReference;
        this.extractionPageSize = extractionPageSize;
        this.insertPageSize = insertPageSize;
        this.insertBatchSize = insertBatchSize;
        this.deletePageSize = deletePageSize;
        this.deleteBatchSize = deleteBatchSize;
        this.numberPerFile = numberPerFile;
        this.blobReference = blobReferenceNoExtension.concat(".zip");
        this.exstractionFileName = blobReferenceNoExtension.concat(".csv");
        this.createGeneralFile = createGeneralFile;
        this.createPartialFile = createPartialFile;
    }

    @Override
    public String getDownloadLink(String filePartId) {

        if (log.isInfoEnabled()) {
            log.info("PaymentInstrumentManagerServiceImpl.getDownloadLink");
        }

        try {
            return azureBlobClient.getDirectAccessLink(containerReference, filePartId == null ?
                    blobReference : blobReference.split("\\.")[0]
                    .concat("_").concat(filePartId).concat( ".zip"));

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

        OffsetDateTime saveExecutionDate = OffsetDateTime.now();
        String saveExecutionDateString = saveExecutionDate.toString();

        Map<String,Object> executionDates = paymentInstrumentManagerDao.getRtdExecutionDate();

        writeBpdHpansToRtd(String.valueOf(executionDates.get("bpd_exec_date")), startDate, endDate);
        writeFaHpansToRtd(String.valueOf(executionDates.get("fa_exec_date")));
        disableBpdHpans(String.valueOf(executionDates.get("bpd_del_exec_date")),endDate);
        disableFaHpans(String.valueOf(executionDates.get("fa_del_exec_date")));

        paymentInstrumentManagerDao.updateExecutionDate(saveExecutionDateString);

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
        boolean lastSectionWritten = false;
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

            Set<String> hashedPans = getActiveHashPANs(offset, extractionPageSize);
             for (String hashPan : hashedPans) {
                 if (createGeneralFile) {
                     generalBufferedWriter.write(hashPan.concat(System.lineSeparator()));
                 }
                if (createPartialFile) {
                    tempBufferedWriter.write(hashPan.concat(System.lineSeparator()));
                }
            }

            if (hashedPans.isEmpty() || hashedPans.size() < extractionPageSize) {
                executed = true;
            } else {
                offset += extractionPageSize;
            }

            if (createGeneralFile) {
                generalBufferedWriter.flush();
            }

            if (createPartialFile) {
                tempBufferedWriter.flush();

                if (offset % numberPerFile == 0) {
                    tempBufferedWriter.close();

                    zipAndUpload(tempFileLocalFile, tempFileZippedFile, currentId, !executed ? currentId+1 : null);

                    if (!executed) {
                        currentId = currentId + 1;

                        tempFileZippedFile = Files.createTempFile(blobReference.split("\\.")[0]
                                .concat("_").concat(String.valueOf(currentId)), ".zip");
                        tempFileLocalFile = Files.createTempFile("tempFile".split("\\.")[0]
                                .concat("_").concat(String.valueOf(currentId)), ".csv");

                        FileUtils.forceDelete(tempFileZippedFile.toFile());
                        FileUtils.forceDelete(tempFileLocalFile.toFile());

                        tempBufferedWriter = Files.newBufferedWriter(tempFileLocalFile,
                                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                    } else {
                        lastSectionWritten = true;
                    }
                }
            }

        }

        if (createPartialFile && !lastSectionWritten) {
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
            log.info(nextFile == null ? "Compressing hashed pans" : "Compressing partial hashed pans");
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
            log.info(nextFile == null ?
                    "Uploading compressed hashed pans" :
                    "Uploading partial compressed hashed pans");
        }

        try {

            azureBlobClient.upload(containerReference,
                    currentFile == null ? blobReference : blobReference.split("\\.")[0]
                            .concat("_").concat(String.valueOf(currentFile)).concat( ".zip"),
                    zippedFile.toFile().getAbsolutePath(),
                    nextFile != null ? String.valueOf(nextFile) : null);
            FileUtils.forceDelete(localFile.toFile());
            FileUtils.forceDelete(zippedFile.toFile());

            if (log.isInfoEnabled()) {
                log.info(nextFile == null ? "Uploaded hashed pan list" : "Uploaded partial hashed pan list");
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

    private void disableBpdHpans(String executionDateString, String startDateString) {

        try {

            boolean executed = false;
            long offset = 0L;

            while (!executed) {

                List<String> hashedPans = paymentInstrumentManagerDao
                        .getBpdDisabledPans(executionDateString, startDateString, offset, deletePageSize);

                paymentInstrumentManagerDao.disableBpdPaymentInstruments(hashedPans, deleteBatchSize);

                if (hashedPans.isEmpty() || hashedPans.size() < deletePageSize) {
                    executed = true;
                } else {
                    offset += deletePageSize;
                }

            }

            boolean executedCitizen = false;
            long offsetCitizen = 0L;

            while (!executedCitizen) {
                List<String> fiscalCodes = paymentInstrumentManagerDao.getBpdDisabledCitizens(
                        executionDateString, startDateString, offsetCitizen, deletePageSize);
                List<String> hashedPans = paymentInstrumentManagerDao.getBpdDisabledCitizenPans(fiscalCodes);
                paymentInstrumentManagerDao.disableBpdPaymentInstruments(hashedPans, deleteBatchSize);

                if (fiscalCodes.isEmpty() || fiscalCodes.size() < deletePageSize) {
                    executedCitizen = true;
                } else {
                    offsetCitizen += deletePageSize;
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
