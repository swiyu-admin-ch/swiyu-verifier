package ch.admin.bj.swiyu.verifier.domain.management;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// JSON-PERSISTED (ZDD): serialized to JSON in the "management" table (see Management.trustAnchors).
// Keep this type backward compatible across releases: don't rename/remove fields without a migration
// path (e.g. @JsonAlias), and keep any new field optional with a default.
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrustAnchor(String did, String trustRegistryUri){}