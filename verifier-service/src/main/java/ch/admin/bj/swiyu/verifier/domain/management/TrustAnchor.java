package ch.admin.bj.swiyu.verifier.domain.management;

public record TrustAnchor(String did, @Deprecated(since="Trust Protocol 2.0") String trustRegistryUri){}