package com.example.walletledger.shared.error;

import com.example.walletledger.wallet.domain.exception.BalanceLimitExceededException;
import com.example.walletledger.wallet.domain.exception.IdempotencyKeyReusedException;
import com.example.walletledger.wallet.domain.exception.InsufficientFundsException;
import com.example.walletledger.wallet.domain.exception.InvalidAmountException;
import com.example.walletledger.wallet.domain.exception.WalletAlreadyExistsException;
import com.example.walletledger.wallet.domain.exception.WalletNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(WalletNotFoundException.class)
    ResponseEntity<ProblemDetail> handleWalletNotFound(WalletNotFoundException ex, HttpServletRequest request) {
        log.info("request rejected code={} playerId={}", ErrorCode.WALLET_NOT_FOUND, ex.getPlayerId());
        return ProblemResponses.of(
                HttpStatus.NOT_FOUND,
                ErrorCode.WALLET_NOT_FOUND,
                "Wallet not found",
                ex.getMessage(),
                request.getRequestURI(),
                clock);
    }

    @ExceptionHandler(WalletAlreadyExistsException.class)
    ResponseEntity<ProblemDetail> handleWalletAlreadyExists(WalletAlreadyExistsException ex, HttpServletRequest request) {
        log.info("request rejected code={} playerId={}", ErrorCode.WALLET_ALREADY_EXISTS, ex.getPlayerId());
        return ProblemResponses.of(
                HttpStatus.CONFLICT,
                ErrorCode.WALLET_ALREADY_EXISTS,
                "Wallet already exists",
                ex.getMessage(),
                request.getRequestURI(),
                clock);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ResponseEntity<ProblemDetail> handleInsufficientFunds(InsufficientFundsException ex, HttpServletRequest request) {
        log.info("request rejected code={}", ErrorCode.INSUFFICIENT_FUNDS);
        return ProblemResponses.of(
                HttpStatus.CONFLICT,
                ErrorCode.INSUFFICIENT_FUNDS,
                "Insufficient funds",
                ex.getMessage(),
                request.getRequestURI(),
                clock);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ResponseEntity<ProblemDetail> handleIdempotencyKeyReused(IdempotencyKeyReusedException ex, HttpServletRequest request) {
        log.info("request rejected code={}", ErrorCode.IDEMPOTENCY_KEY_REUSED);
        return ProblemResponses.of(
                HttpStatus.CONFLICT,
                ErrorCode.IDEMPOTENCY_KEY_REUSED,
                "Idempotency key reused",
                ex.getMessage(),
                request.getRequestURI(),
                clock);
    }

    @ExceptionHandler(BalanceLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleBalanceLimit(BalanceLimitExceededException ex, HttpServletRequest request) {
        log.info("request rejected code={}", ErrorCode.BALANCE_LIMIT_EXCEEDED);
        return ProblemResponses.of(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.BALANCE_LIMIT_EXCEEDED,
                "Balance limit exceeded",
                ex.getMessage(),
                request.getRequestURI(),
                clock);
    }

    @ExceptionHandler(InvalidAmountException.class)
    ResponseEntity<ProblemDetail> handleInvalidAmount(InvalidAmountException ex, HttpServletRequest request) {
        return ProblemResponses.of(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Request validation failed",
                ex.getMessage(),
                request.getRequestURI(),
                clock,
                List.of(new FieldErrorDetail("amount", ex.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::toFieldError)
                .collect(Collectors.toList());
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errors.add(new FieldErrorDetail(error.getObjectName(), defaultMessage(error.getDefaultMessage()))));
        return validationResponse(request, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleHandlerMethodValidation(
            HandlerMethodValidationException ex,
            HttpServletRequest request) {
        List<FieldErrorDetail> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorDetail(
                                result.getMethodParameter().getParameterName(),
                                defaultMessage(error.getDefaultMessage()))))
                .toList();
        return validationResponse(request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        List<FieldErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(
                        violation.getPropertyPath().toString(),
                        defaultMessage(violation.getMessage())))
                .toList();
        return validationResponse(request, errors);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return validationResponse(
                request,
                List.of(new FieldErrorDetail(ex.getHeaderName(), "required header is missing")));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        log.info("request rejected code={}", ErrorCode.MALFORMED_REQUEST);
        return ProblemResponses.of(
                HttpStatus.BAD_REQUEST,
                ErrorCode.MALFORMED_REQUEST,
                "Malformed request",
                "The request could not be parsed.",
                request.getRequestURI(),
                clock);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("unexpected error", ex);
        return ProblemResponses.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "Internal error",
                "An unexpected error occurred.",
                request.getRequestURI(),
                clock);
    }

    private ResponseEntity<ProblemDetail> validationResponse(HttpServletRequest request, List<FieldErrorDetail> errors) {
        log.info("request rejected code={}", ErrorCode.VALIDATION_ERROR);
        return ProblemResponses.of(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Request validation failed",
                "One or more request values are invalid.",
                request.getRequestURI(),
                clock,
                errors);
    }

    private static FieldErrorDetail toFieldError(FieldError error) {
        return new FieldErrorDetail(error.getField(), defaultMessage(error.getDefaultMessage()));
    }

    private static String defaultMessage(String message) {
        return message == null || message.isBlank() ? "invalid value" : message;
    }
}
