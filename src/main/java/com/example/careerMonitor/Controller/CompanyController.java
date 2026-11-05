package com.example.careerMonitor.Controller;

import com.example.careerMonitor.CompanyService.CompanyService;
import com.example.careerMonitor.DTO.CompanyRequestDto;
import com.example.careerMonitor.DTO.CompanyResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    private static final String TEMP_USER_ID = "default_test_user";

    @GetMapping
    public ResponseEntity<List<CompanyResponseDto>> getAllCompaniesByUserId() {
        return ResponseEntity.ok().body(companyService.getAllCompaniesByUserId(TEMP_USER_ID));
    }

    @PostMapping
    public ResponseEntity<CompanyResponseDto> addCompany(@Valid @RequestBody CompanyRequestDto companyRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyService.addCompany(TEMP_USER_ID, companyRequestDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable Long id) {
        boolean deleted = companyService.deleteCompany(id, TEMP_USER_ID);

        if (deleted) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
