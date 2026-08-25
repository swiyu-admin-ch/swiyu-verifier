package ch.admin.bj.swiyu.verifier.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record StatusVerificationResult(boolean valid, Integer status) {}