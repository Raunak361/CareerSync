package com.example.careerMonitor.Controller;

import com.example.careerMonitor.CompanyService.CompanyService;
import com.example.careerMonitor.DTO.CompanyResponseDto;
import com.example.careerMonitor.Entity.CompanyDetails;
import com.example.careerMonitor.Entity.User;
import com.example.careerMonitor.UserService.UserService;
import com.example.careerMonitor.repository.CompanyRepo;
import com.example.careerMonitor.repository.UserRepo;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final UserRepo userRepo;
    private final CompanyRepo companyRepo;
    private final CompanyService companyService;

    @GetMapping("/test")
    public String test() {
        return "User controller is working";
    }

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt) {

        User user = userService.getOrCreateUser(jwt);

        return ResponseEntity.ok(user);
    }
//    @GetMapping("/t")
//    public ResponseEntity<List<CompanyResponseDto>> getAllCompany() {
//
//        List<CompanyDetails> companies = companyRepo.findAll();
//
//        List<CompanyResponseDto> response = companies.stream()
//                .map(CompanyResponseDto::new)
//                .toList();
//
//        return ResponseEntity.ok(response);
//    }
}
