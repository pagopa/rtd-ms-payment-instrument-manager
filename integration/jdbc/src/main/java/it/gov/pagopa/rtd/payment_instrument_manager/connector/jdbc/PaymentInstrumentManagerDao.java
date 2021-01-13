package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc;

import java.util.List;

public interface PaymentInstrumentManagerDao {

    List<String> getBPDActiveHashPANs(Long offset, Long size);

    List<String> getFAActiveHashPANs(Long offset, Long size);

}
