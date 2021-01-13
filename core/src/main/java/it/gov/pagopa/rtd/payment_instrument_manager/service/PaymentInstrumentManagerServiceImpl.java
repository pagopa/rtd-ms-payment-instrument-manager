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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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


    @Autowired
    public PaymentInstrumentManagerServiceImpl(
            PaymentInstrumentManagerDao paymentInstrumentManagerDao,
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

        if (log.isInfoEnabled()) {
            log.info("Compressing hashed pans");
        }

        File file = Files.createTempFile(blobReference.split("\\.")[0], ".zip").toFile();
        FileOutputStream fileOutputStream = null;

        Path localFile = Files.createTempFile("tempFile".split("\\.")[0],".csv");
        Path mergedFile = Files.createTempFile(exstractionFileName.split("\\.")[0],".csv");


        FileUtils.forceDelete(localFile.toFile());
        FileUtils.forceDelete(mergedFile.toFile());

        BufferedWriter bufferedWriter = Files.newBufferedWriter(localFile,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.APPEND);

        ExecutorService executorService = Executors.newScheduledThreadPool(2);

        Future bpdFuture = executorService.submit(() -> {

            try {

                boolean executed = false;
                long offset = 0L;
                long size = 1000000L;

                while (!executed) {

                    List<String> hashedPans = paymentInstrumentManagerDao.getBPDActiveHashPANs(offset, size);
                    for (String hashPan : hashedPans) {
                        bufferedWriter.write(hashPan.concat(System.lineSeparator()));
                    }

                    if (hashedPans.isEmpty() || hashedPans.size() < size) {
                        executed = true;
                    } else {
                        offset += size;
                    }

                }
            } catch (Exception e) {
                log.error(e.getMessage(),e);
            }
        });

        Future faFuture = executorService.submit(() -> {

            try {
                boolean executed = false;
                long offset = 0L;
                long size = 1000000L;

                while (!executed) {

                    List<String> hashedPans = paymentInstrumentManagerDao.getFAActiveHashPANs(offset, size);
                    for (String hashPan : hashedPans) {
                        bufferedWriter.write(hashPan.concat(System.lineSeparator()));
                    }

                    if (hashedPans.isEmpty() || hashedPans.size() < size) {
                        executed = true;
                    } else {
                        offset += size;
                    }

                }
            } catch (Exception e) {
                log.error(e.getMessage(),e);
            }
        });

        bpdFuture.get();
        faFuture.get();

        bufferedWriter.close();

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
        StreamGobbler streamGobbler =
                new StreamGobbler(process.getInputStream(), System.out::println);
        Executors.newSingleThreadExecutor().submit(streamGobbler);
        int exitCode = process.waitFor();
        assert exitCode == 0;

        try {

            FileInputStream fileInputStream = new FileInputStream(mergedFile.toFile());
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

    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                    .forEach(consumer);
        }
    }

}
