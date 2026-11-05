package com.example.careerMonitor.Controller;

import com.example.careerMonitor.DTO.OpportunityResponseDto;
import com.example.careerMonitor.OppServices.OpportunityService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/opportunities")
public class OpportunityController {

    private final OpportunityService opportunityService;

    private static final String TEMP_USER_ID = "default_test_user";

    @GetMapping
    public ResponseEntity<List<OpportunityResponseDto>> getAllOpportunityByUserId() {
        return ResponseEntity.ok().body(opportunityService.getAllOpportunityByUserId(TEMP_USER_ID));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markOpportunityAsRead(@PathVariable long id) {
        try {
            opportunityService.markOpportunityAsRead(id, TEMP_USER_ID);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
