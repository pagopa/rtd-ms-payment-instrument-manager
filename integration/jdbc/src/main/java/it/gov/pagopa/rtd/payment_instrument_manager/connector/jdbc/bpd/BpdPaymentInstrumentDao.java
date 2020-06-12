package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.bpd;

import java.util.List;

public interface BpdPaymentInstrumentDao {

    List<String> getActiveHashPANs();

}
