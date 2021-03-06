package it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class AzureBlobUploadException extends Exception {

    public AzureBlobUploadException(Throwable cause) {
        super(cause);
    }
}
