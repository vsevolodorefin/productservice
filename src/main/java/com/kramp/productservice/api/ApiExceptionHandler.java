package com.kramp.productservice.api;

import com.kramp.productservice.domain.UnsupportedMarketException;
import com.kramp.productservice.upstream.*;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Framework validation errors and application errors both use RFC 9457 Problem Details. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(UnsupportedMarketException.class)
    ProblemDetail unsupportedMarket(UnsupportedMarketException exception) {
        return problem(HttpStatus.BAD_REQUEST, "unsupported-market", exception.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail notFound(ProductNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "product-not-found", "Product was not found.");
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    ProblemDetail unavailable(UpstreamUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "upstream-unavailable", "Required product data is unavailable.");
    }

    @ExceptionHandler(UpstreamTimeoutException.class)
    ProblemDetail timeout(UpstreamTimeoutException exception) {
        return problem(HttpStatus.GATEWAY_TIMEOUT, "upstream-timeout", "Required product data did not arrive in time.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception exception, WebRequest request) {
        logger.error("Unexpected request failure", exception);
        return handleExceptionInternal(exception,
                problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "An unexpected error occurred."),
                new org.springframework.http.HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setTitle(status.getReasonPhrase());
        result.setType(URI.create("urn:productservice:problem:" + code));
        return result;
    }
}
