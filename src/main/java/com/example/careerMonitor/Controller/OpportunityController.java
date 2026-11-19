package com.example.careerMonitor.Controller;

import com.example.careerMonitor.DTO.OpportunityResponseDto;
import com.example.careerMonitor.OppServices.OpportunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/opportunities")
public class OpportunityController {

    private final OpportunityService opportunityService;

    @GetMapping
    public ResponseEntity<List<OpportunityResponseDto>> getAllOpportunityByUserId(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        return ResponseEntity.ok().body(opportunityService.getAllOpportunityByUserId(userId));
    }

    @GetMapping("/{experience}")
    public ResponseEntity<List<OpportunityResponseDto>> getAllOpportunityByUserIdAndExperience(@PathVariable Integer experience, @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        return ResponseEntity.ok().body(opportunityService.getAllOpportunityByUserIdAndExperience(experience,userId));
    }

//    @PutMapping("/{id}/read")
//    public ResponseEntity<Void> markOpportunityAsRead(@PathVariable long id,@AuthenticationPrincipal Jwt jwt) {
//        String userId = jwt.getSubject();
//        try {
//            opportunityService.markOpportunityAsRead(id, userId);
//            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
//        } catch (RuntimeException e) {
//            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
//        }
//    }
}
