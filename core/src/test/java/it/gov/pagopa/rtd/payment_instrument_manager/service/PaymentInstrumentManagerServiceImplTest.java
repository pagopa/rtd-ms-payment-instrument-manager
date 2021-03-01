package it.gov.pagopa.rtd.payment_instrument_manager.service;

import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.AzureBlobClient;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception.AzureBlobDirectAccessException;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception.AzureBlobUploadException;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.PaymentInstrumentManagerDao;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PaymentInstrumentManagerServiceImpl.class)
@TestPropertySource(
        properties = {
                "batchConfiguration.paymentInstrumentsExtraction.extraction.pageSize=100",
                "blobStorageConfiguration.blobReferenceNoExtension=test",
                "blobStorageConfiguration.containerReference=demo",
                "batchConfiguration.paymentInstrumentsExtraction.insert.pageSize=100",
                "batchConfiguration.paymentInstrumentsExtraction.insert.batchSize=100",
                "batchConfiguration.paymentInstrumentsExtraction.delete.pageSize=100",
                "batchConfiguration.paymentInstrumentsExtraction.delete.batchSize=100",
                "batchConfiguration.paymentInstrumentsExtraction.numberPerFile=100",
                "batchConfiguration.paymentInstrumentsExtraction.createGeneralFile=true",
                "batchConfiguration.paymentInstrumentsExtraction.createPartialFile=false"
        })
public class PaymentInstrumentManagerServiceImplTest {

    @MockBean
    private AzureBlobClient azureBlobClientMock;
    @MockBean
    private PaymentInstrumentManagerDao paymentInstrumentManagerDaoMock;

    @Autowired
    private PaymentInstrumentManagerServiceImpl paymentInstrumentManagerService;


    @PostConstruct
    public void configureTests() throws AzureBlobDirectAccessException {
        when(paymentInstrumentManagerDaoMock.getBPDActiveHashPANs(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Collections.singletonList("test"));

        HashMap<String, Object> awardHashMap = new HashMap<>();
        awardHashMap.put("start_date", "2018-01-01 00:00:00.000+01");
        awardHashMap.put("end_date", "2021-12-31 23:59:59.999+01");

        when(paymentInstrumentManagerDaoMock.getAwardPeriods())
                .thenReturn(awardHashMap);

        HashMap<String, Object> rtdHashMap = new HashMap<>();
        rtdHashMap.put("bpd_exec_date", "2018-01-01 00:00:00.000+01");
        rtdHashMap.put("fa_exec_date", "2018-01-01 00:00:00.000+01");
        rtdHashMap.put("bpd_del_exec_date", "2018-01-01 00:00:00.000+01");
        rtdHashMap.put("fa_del_exec_date", "2018-01-01 00:00:00.000+01");

        when(paymentInstrumentManagerDaoMock.getRtdExecutionDate())
                .thenReturn(rtdHashMap);

        when(paymentInstrumentManagerDaoMock.getActiveHashPANs(
                Mockito.any(), Mockito.any()))
                .thenReturn(Collections.singletonList("test"));

    }


    @Test
    public void getDownloadLink_OK() throws AzureBlobDirectAccessException {
        when(azureBlobClientMock.getDirectAccessLink(anyString(), anyString()))
                .thenReturn(UUID.randomUUID().toString());

        final String link = paymentInstrumentManagerService.getDownloadLink(null);

        Assert.assertNotNull(link);
        verify(azureBlobClientMock, only()).getDirectAccessLink(anyString(), anyString());
    }


    @Test
    public void getDownloadLink_KO() throws AzureBlobDirectAccessException {
        when(azureBlobClientMock.getDirectAccessLink(anyString(), anyString()))
                .thenThrow(new AzureBlobDirectAccessException());

        try {
            paymentInstrumentManagerService.getDownloadLink(null);
        } catch (RuntimeException e) {
            Assert.assertEquals(AzureBlobDirectAccessException.class, e.getCause().getClass());
        }

        verify(azureBlobClientMock, only()).getDirectAccessLink(anyString(), anyString());
    }


    @Test
    public void generateFileForAcquirer_OK() throws AzureBlobUploadException {
        doNothing()
                .when(azureBlobClientMock).upload(anyString(), anyString(), any(), any());

        paymentInstrumentManagerService.generateFileForAcquirer();

        verify(azureBlobClientMock, only()).upload(anyString(), anyString(), any(), any());
    }


    @Test
    public void generateFileForAcquirer_KO() throws AzureBlobUploadException {
        doThrow(new AzureBlobUploadException())
                .when(azureBlobClientMock).upload(anyString(), anyString(), any(), any());

        try {
            paymentInstrumentManagerService.generateFileForAcquirer();
        } catch (RuntimeException e) {
            Assert.assertEquals(AzureBlobUploadException.class, e.getCause().getClass());
        }

        verify(azureBlobClientMock, only()).upload(anyString(), anyString(), any(), any());
    }

}