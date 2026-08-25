package ch.admin.bj.swiyu.verifier.dto.management.result;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(name = "StatusVerificationResult")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record StatusVerificationResultDto(boolean valid, Integer status) {}