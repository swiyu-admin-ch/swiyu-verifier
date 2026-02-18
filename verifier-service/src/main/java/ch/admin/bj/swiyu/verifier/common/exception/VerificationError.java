package ch.admin.bj.swiyu.verifier.common.exception;

public enum VerificationError {
    /**
     * RFC 6749 subset of error codes this verifier agent supports from base on the <a href="https://www.rfc-editor.org/rfc/rfc6749.html#section-4.2.2.1">
     * RFC specification</a>.
     * The other error types as indicated in <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0-ID2.html#section-6.4">OpenID4VP</a>
     * are not listed because they are only relevant for the holder during the presentation submission / response
     *
     * In OID4VP 1.0:
     * <ul>
     * <li>The request contains both a dcql_query parameter and a scope parameter referencing a DCQL query.</li>
     * <li>The request uses the vp_token Response Type but does not include a dcql_query parameter nor a scope parameter referencing a DCQL query.</li>
     * <li>The Wallet does not support the Client Identifier Prefix passed in the Authorization Request.</li>
     * <li>The Client Identifier passed in the request did not belong to its Client Identifier Prefix, or requirements of a certain prefix were violated, for example an unsigned request was sent with Client Identifier Prefix https.</li>
     * </ul>
     */
    // RFC Codes
    INVALID_REQUEST,
    /**
     * <ul>
     * <li>client_metadata parameter defined in Section 5.1 is present, but the Wallet recognizes Client Identifier and knows metadata associated with it.</li>
     * <li>Verifier's pre-registered metadata has been found based on the Client Identifier, but client_metadata parameter is also present.</li>
     * </ul>
     */
    INVALID_CLIENT,
    /**
     * <ul>
     * <li>The Wallet did not have the requested Credentials to satisfy the Authorization Request.</li>
     * <li>The End-User did not give consent to share the requested Credentials with the Verifier.</li>
     * <li>The Wallet failed to authenticate the End-User.</li>
     * </ul>
     */
    ACCESS_DENIED,
    SERVER_ERROR,
    /**
     any of the following is true for at least one object in the transaction_data structure:
     <ul>
     <li>contains an unknown or unsupported transaction data type value,</li>
     <li>is an object of a known type but containing unknown fields,</li>
     <li>contains fields of the wrong type for the transaction data type,</li>
     <li>contains fields with invalid values for the transaction data type,</li>
     <li>is missing required fields for the transaction data type,</li>
     <li>the credential_ids does not match, or</li>
     <li>the referenced Credential(s) are not available in the Wallet.</li>
     </ul>
     */
    INVALID_TRANSACTION_DATA,

    // Codes according to custom profile
    INVALID_CREDENTIAL;
}
