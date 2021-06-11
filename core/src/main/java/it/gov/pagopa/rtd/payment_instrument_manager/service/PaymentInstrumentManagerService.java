package it.gov.pagopa.rtd.payment_instrument_manager.service;


import javax.servlet.http.HttpServletResponse;

public interface PaymentInstrumentManagerService {

    String getBinList(String filePartId);

    String getTokenList(String filePartId);

    String getDownloadLink(String filePartId);

    String getParDownloadLink(String filePartId);

    void generateFileForAcquirer();

    void refreshActiveHpans();

}
