package com.tonyleiva.parkinggarage.presentation.error;

import com.tonyleiva.parkinggarage.application.revenue.InvalidRevenueRequestException;
import com.tonyleiva.parkinggarage.application.revenue.SectorNotFoundException;
import com.tonyleiva.parkinggarage.application.webhook.InvalidWebhookPayloadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler({InvalidWebhookPayloadException.class, HttpMessageNotReadableException.class})
  ResponseEntity<ErrorResponse> invalidPayload(Exception exception) {
    String message = exception instanceof InvalidWebhookPayloadException
        ? exception.getMessage() : "O payload informado é inválido.";
    return ResponseEntity.badRequest().body(new ErrorResponse("INVALID", message));
  }

  @ExceptionHandler(InvalidRevenueRequestException.class)
  ResponseEntity<ErrorResponse> invalidRevenueRequest(InvalidRevenueRequestException exception) {
    return ResponseEntity.badRequest().body(new ErrorResponse("INVALID", exception.getMessage()));
  }

  @ExceptionHandler(SectorNotFoundException.class)
  ResponseEntity<ErrorResponse> sectorNotFound(SectorNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("NOT_FOUND", exception.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unexpected(Exception exception) {
    LOGGER.error("Falha inesperada ao processar a requisição.", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("ERROR", "Ocorreu uma falha interna ao processar a requisição."));
  }
}
