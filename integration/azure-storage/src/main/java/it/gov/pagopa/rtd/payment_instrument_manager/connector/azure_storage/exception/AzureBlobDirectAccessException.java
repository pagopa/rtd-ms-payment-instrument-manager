package it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.exception;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class AzureBlobDirectAccessException extends Exception {

    public AzureBlobDirectAccessException(Throwable cause) {
        super(cause);
    }
}
