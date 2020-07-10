package it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage;

import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception.AzureBlobDirectAccessException;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception.AzureBlobUploadException;

public interface AzureBlobClient {

    String getDirectAccessLink(String containerReference, String blobReference) throws AzureBlobDirectAccessException;

    void upload(String containerReference, String blobReference, byte[] content) throws AzureBlobUploadException;

}
