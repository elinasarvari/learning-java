package com.example.app.service.security;

/**
 * Thrown when row-level policy cannot be resolved.
 *
 * <p>The existence of this exception <i>is</i> the fail-closed rule (STD-SEC-005). The
 * tempting alternative - catching the error and returning an empty restriction set - is
 * not error handling, it is a data leak: empty restrictions produce no predicate, no
 * predicate produces an unfiltered {@code WHERE}, and the caller receives a 200 with
 * more rows than they are entitled to. Nothing in the response distinguishes that from a
 * correct result.
 *
 * <p>Maps to 503, not 500: the request was valid and will likely succeed on retry.
 */
public class AccessPolicyUnavailableException extends RuntimeException {

    public AccessPolicyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
