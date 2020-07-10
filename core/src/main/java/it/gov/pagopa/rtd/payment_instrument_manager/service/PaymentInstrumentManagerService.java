package it.gov.pagopa.rtd.payment_instrument_manager.service;


public interface PaymentInstrumentManagerService {

    String getDownloadLink();

    void generateFileForAcquirer();

}
