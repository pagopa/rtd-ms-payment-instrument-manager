package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc;

import java.util.List;
import java.util.Map;

public interface PaymentInstrumentManagerDao {

    Map<String,Object> getAwardPeriods();

    List<Map<String,Object>> getBPDActiveHashPANs(
            String executionDate, String updateExecutionDate, String startDate, String endDate, Long offset, Long size);

    List<String> getFAActiveHashPANs(String executionDate, Long offset, Long size);

    Map<String, Object> getRtdExecutionDate();

    void insertBpdPaymentInstruments(List<Map<String,Object>> paymentInstruments, int batchSize);

    void insertFaPaymentInstruments(List<String> paymentInstruments, int batchSize);

    void disableBpdPaymentInstruments(List<String> paymentInstruments, int batchSize);

    void disableFaPaymentInstruments(List<String> paymentInstruments, int batchSize);

    List<Map<String,Object>> getActiveHashPANs(Long offset, Long size);

    List<Map<String,Object>> getActivePARs(Long offset, Long size);

    void updateExecutionDate(String executionDate);

    List<String> getBpdDisabledPans(String executionDate, String startDate, Long offset, Long size);

    List<String> getFaDisabledPans(String executionDate, Long offset, Long size);

    List<String> getBpdDisabledCitizenPans(List<String> fiscalCodes);

    List<String> getBpdDisabledCitizens(String executionDate, String startDate, Long offset, Long size);

    void deletePaymentInstruments();

}
