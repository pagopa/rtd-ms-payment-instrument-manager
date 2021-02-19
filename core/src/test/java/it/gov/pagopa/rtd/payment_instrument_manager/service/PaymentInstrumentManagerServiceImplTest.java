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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PaymentInstrumentManagerServiceImpl.class)
@TestPropertySource(
        properties = {
                "batchConfiguration.paymentInstrumentsExtraction.pageSize=100",
                "blobStorageConfiguration.blobReferenceNoExtension=test",
                "blobStorageConfiguration.containerReference=demo"
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

        when(paymentInstrumentManagerDaoMock.getActiveHashPANs(
                Mockito.any(), Mockito.any()))
                .thenReturn(Collections.singletonList("test"));

    }


    @Test
    public void getDownloadLink_OK() throws AzureBlobDirectAccessException {
        when(azureBlobClientMock.getDirectAccessLink(anyString(), anyString()))
                .thenReturn(UUID.randomUUID().toString());

        final String link = paymentInstrumentManagerService.getDownloadLink();

        Assert.assertNotNull(link);
        verify(azureBlobClientMock, only()).getDirectAccessLink(anyString(), anyString());
    }


    @Test
    public void getDownloadLink_KO() throws AzureBlobDirectAccessException {
        when(azureBlobClientMock.getDirectAccessLink(anyString(), anyString()))
                .thenThrow(new AzureBlobDirectAccessException());

        try {
            paymentInstrumentManagerService.getDownloadLink();
        } catch (RuntimeException e) {
            Assert.assertEquals(AzureBlobDirectAccessException.class, e.getCause().getClass());
        }

        verify(azureBlobClientMock, only()).getDirectAccessLink(anyString(), anyString());
    }


    @Test
    public void generateFileForAcquirer_OK() throws AzureBlobUploadException {
        doNothing()
                .when(azureBlobClientMock).upload(anyString(), anyString(), any(), null);

        paymentInstrumentManagerService.generateFileForAcquirer();

        verify(azureBlobClientMock, only()).upload(anyString(), anyString(), any(), null);
    }


    @Test
    public void generateFileForAcquirer_KO() throws AzureBlobUploadException {
        doThrow(new AzureBlobUploadException())
                .when(azureBlobClientMock).upload(anyString(), anyString(), any(), null);

        try {
            paymentInstrumentManagerService.generateFileForAcquirer();
        } catch (RuntimeException e) {
            Assert.assertEquals(AzureBlobUploadException.class, e.getCause().getClass());
        }

        verify(azureBlobClientMock, only()).upload(anyString(), anyString(), any(), null);
    }

}