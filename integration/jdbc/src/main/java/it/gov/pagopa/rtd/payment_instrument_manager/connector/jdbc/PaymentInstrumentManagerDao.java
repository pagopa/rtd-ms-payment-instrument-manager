package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc;

import java.util.List;

public interface PaymentInstrumentManagerDao {

    List<String> getActiveHashPANs();

}
