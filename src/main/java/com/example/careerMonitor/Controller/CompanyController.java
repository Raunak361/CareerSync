package com.example.careerMonitor.Controller;

import com.example.careerMonitor.CompanyService.CompanyService;
import com.example.careerMonitor.DTO.CompanyRequestDto;
import com.example.careerMonitor.DTO.CompanyResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;



    @GetMapping
    public ResponseEntity<List<CompanyResponseDto>> getAllCompaniesByUserId(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        System.out.println(userId);

        return ResponseEntity.ok().body(companyService.getAllCompaniesByUserId(userId));
    }

    @GetMapping("/available")
    public ResponseEntity<List<CompanyResponseDto>> getAvailableCompanies() {

        return ResponseEntity.ok(
                companyService.getAllCompanies()
        );
    }



    @PostMapping
    public ResponseEntity<List<CompanyResponseDto>> addCompany(@Valid @RequestBody CompanyRequestDto companyRequestDto,@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyService.addCompany(jwt.getSubject(), companyRequestDto));
    }



    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable Long id,@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        boolean deleted = companyService.deleteCompany(id, userId);

        if (deleted) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
