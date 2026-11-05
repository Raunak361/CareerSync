package com.example.careerMonitor.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CompanyRequestDto {

    @NotBlank(message = "Company name is required")
    private String name;

    @NotBlank(message = "Career page URL is required")
    private String url;
}
