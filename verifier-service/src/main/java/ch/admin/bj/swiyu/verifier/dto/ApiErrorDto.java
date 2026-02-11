package ch.admin.bj.swiyu.verifier.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Builder
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorDto {
    @JsonProperty("error")
    String error;
    @JsonProperty("error_description")
    String errorDescription;
    @JsonProperty("detail")
    String errorDetails;
    @JsonIgnore
    HttpStatus status;
}