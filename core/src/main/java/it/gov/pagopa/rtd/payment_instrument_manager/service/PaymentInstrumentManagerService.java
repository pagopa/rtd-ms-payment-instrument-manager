package it.gov.pagopa.rtd.payment_instrument_manager.service;


public interface PaymentInstrumentManagerService {

    String getDownloadLink(String filePartId);

    void generateFileForAcquirer();

    void refreshActiveHpans();

}
