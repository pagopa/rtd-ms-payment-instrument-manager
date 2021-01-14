package it.gov.pagopa.rtd.payment_instrument_manager.service;

import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.AzureBlobClient;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception.AzureBlobDirectAccessException;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception.AzureBlobUploadException;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.PaymentInstrumentManagerDao;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


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

        uploadHashedPans();

    }

    @SneakyThrows
    private void uploadHashedPans() {

        if (log.isInfoEnabled()) {
            log.info("PaymentInstrumentManagerServiceImpl.uploadHashedPans");
        }

        Path zippedFile = Files.createTempFile(blobReference.split("\\.")[0], ".zip");
        Path localFile = Files.createTempFile("tempFile".split("\\.")[0],".csv");
        Path mergedFile = Files.createTempFile(exstractionFileName.split("\\.")[0],".csv");

        FileUtils.forceDelete(localFile.toFile());
        FileUtils.forceDelete(mergedFile.toFile());
        FileUtils.forceDelete(zippedFile.toFile());

        Map<String,Object> awardPeriodData = paymentInstrumentManagerDao.getAwardPeriods();
        String startDate = String.valueOf(awardPeriodData.get("start_date"));
        String endDate = String.valueOf(awardPeriodData.get("end_date"));

        BufferedWriter bufferedWriter = Files.newBufferedWriter(localFile,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.APPEND);

        writeBpdHpans(startDate,endDate,bufferedWriter);
        writeFAHpans(bufferedWriter);

        bufferedWriter.close();

        if (log.isInfoEnabled()) {
            log.info("Merging hashed pans");
        }

        ExecutorService executorService = Executors.newScheduledThreadPool(1);
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");

        Process process;
        if (isWindows) {
            process = Runtime.getRuntime()
                    .exec(String.format("cmd.exe /c sort %s | uniq > %s",
                            localFile.toAbsolutePath(), mergedFile.toAbsolutePath()));
        } else {
            process = Runtime.getRuntime()
                    .exec(String.format("sh -c sort %s | uniq > %s", localFile.toAbsolutePath(),
                            mergedFile.toAbsolutePath()));
        }


        if (log.isInfoEnabled()) {
            log.info("Compressing hashed pans");
        }

        CommandRunner commandRunner =
                new CommandRunner(process.getInputStream(), System.out::println);
        executorService.submit(commandRunner);
        int exitCode = process.waitFor();
        assert exitCode == 0;

        if (isWindows) {
            process = Runtime.getRuntime()
                    .exec(String.format("cmd.exe /c powershell.exe \"Get-ChildItem -Path %s | " +
                                    "Compress-Archive -DestinationPath %s\"",
                            mergedFile.toAbsolutePath(), zippedFile.toAbsolutePath()));
        } else {
            process = Runtime.getRuntime()
                    .exec(String.format("sh -c zipf %s %s", zippedFile.toAbsolutePath(),
                            mergedFile.toAbsolutePath()));
        }

        commandRunner =
                new CommandRunner(process.getInputStream(), System.out::println);
        executorService.submit(commandRunner);
        exitCode = process.waitFor();
        assert exitCode == 0;

        if (log.isInfoEnabled()) {
            log.info("Uploading compressed hashed pans");
        }

        try {

            azureBlobClient.upload(containerReference, blobReference, zippedFile.toFile().getAbsolutePath());
            FileUtils.forceDelete(localFile.toFile());
            FileUtils.forceDelete(mergedFile.toFile());
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

    private static class CommandRunner implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;

        public CommandRunner(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                    .forEach(consumer);
        }

    }

    @SneakyThrows
    private void writeBpdHpans(String startDate, String endDate, BufferedWriter bufferedWriter) {

        try {

            boolean executed = false;
            long offset = 0L;

            while (!executed) {

                List<String> hashedPans = paymentInstrumentManagerDao
                        .getBPDActiveHashPANs(startDate, endDate, offset, pageSize);
                for (String hashPan : hashedPans) {
                    bufferedWriter.write(hashPan.concat(System.lineSeparator()));
                }

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
    private void writeFAHpans(BufferedWriter bufferedWriter) {

        try {

            boolean executed = false;
            long offset = 0L;

            while (!executed) {

                List<String> hashedPans = paymentInstrumentManagerDao.getFAActiveHashPANs(offset, pageSize);
                for (String hashPan : hashedPans) {
                    bufferedWriter.write(hashPan.concat(System.lineSeparator()));
                }

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
