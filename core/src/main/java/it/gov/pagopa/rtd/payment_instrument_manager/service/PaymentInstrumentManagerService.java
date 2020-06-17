package it.gov.pagopa.rtd.payment_instrument_manager.service;


import com.microsoft.azure.storage.StorageException;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;

public interface PaymentInstrumentManagerService {

    String getDownloadLink() throws URISyntaxException, InvalidKeyException, StorageException;

}
