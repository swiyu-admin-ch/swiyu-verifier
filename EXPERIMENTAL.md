# EXPERIMENTAL FEATURES

## Features toggled via variables

These variables are part of experimental features - use at own risk.
Not setting these variables will keep the feature deactivated.

* SWIYU_TRUST_REGISTRY_API_URL
* SWIYU_TMS_AUTHORING_URL
* STATUS_LIST_CACHE_TTL_MILLI
* SWIYU_TRUST_REGISTRY_MAX_CACHE_TTL_SECONDS
* SWIYU_TMS_AUTHORING_URL

## Features toggled via request parameters

These features are experimental - use them at your own risk.

### RedirectUri- aka "Session Fixation for Same Device"-Flow

The flow can be enabled by sending a `redirect_uri` in the initial creation of the verification offer. It stores the
`redirect_uri` together with a generated `response_code` in the management entity object. The `redirect_uri` and the
`response_code` are then sent to the wallet if the verification was successful or rejected. The wallet redirects to the
provided `redirect_uri` with the `response_code` as a query parameter. In a next step the business verifier retrieves
the result by using the `id` and `response_code`. If the `redirect_uri` was set in the initial offer the response_code
must be provided.

At the moment this feature should not be used in a production environment as it needs further testing.