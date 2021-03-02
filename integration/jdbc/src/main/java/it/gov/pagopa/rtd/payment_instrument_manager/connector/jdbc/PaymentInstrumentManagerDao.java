package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc;

import java.util.List;
import java.util.Map;

public interface PaymentInstrumentManagerDao {

    Map<String,Object> getAwardPeriods();

    List<String> getBPDActiveHashPANs(
            String executionDate, String startDate, String endDate, Long offset, Long size);

    List<String> getFAActiveHashPANs(String executionDate, Long offset, Long size);

    Map<String, Object> getRtdExecutionDate();

    void insertBpdPaymentInstruments(List<String> paymentInstruments, int batchSize);

    void insertFaPaymentInstruments(List<String> paymentInstruments, int batchSize);

    void disableBpdPaymentInstruments(List<String> paymentInstruments, int batchSize);

    void disableFaPaymentInstruments(List<String> paymentInstruments, int batchSize);

    List<String> getActiveHashPANs(Long offset, Long size);

    void updateExecutionDate(String executionDate);

    List<String> getBpdDisabledPans(String executionDate, String startDate, Long offset, Long size);

    List<String> getFaDisabledPans(String executionDate, Long offset, Long size);

    List<String> getBpdDisabledCitizenPans(List<String> fiscalCodes);

    List<String> getBpdDisabledCitizens(String executionDate, String startDate, Long offset, Long size);

}
