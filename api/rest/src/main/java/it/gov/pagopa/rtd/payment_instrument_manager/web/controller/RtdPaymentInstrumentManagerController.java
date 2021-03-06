package it.gov.pagopa.rtd.payment_instrument_manager.web.controller;

import io.swagger.annotations.Api;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

/**
 * Controller to expose MicroService
 */
@Api(tags = "Bonus Pagamenti Digitali payment-instrument-manager Controller")
@RequestMapping("/rtd/payment-instrument-manager")
public interface RtdPaymentInstrumentManagerController {
    @GetMapping(value = "/hashed-pans")
    @ResponseStatus(HttpStatus.FOUND)
    void getHashedPans(HttpServletResponse httpServletResponse,
                       @RequestParam(value = "filePartId", required = false) String filePartId);

    @PostMapping("/active-hpans")
    @ResponseStatus(HttpStatus.OK)
    void uploadActiveHashPANs();

    @PostMapping("/refresh-active-hpans")
    @ResponseStatus(HttpStatus.OK)
    void refreshActiveHpans();
}
