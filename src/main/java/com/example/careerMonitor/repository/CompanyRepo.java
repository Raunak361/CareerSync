package com.example.careerMonitor.repository;

import com.example.careerMonitor.Entity.CompanyDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepo extends JpaRepository<CompanyDetails, Long> {

    List<CompanyDetails> findAllByUserId(String userId);

    Optional<CompanyDetails> findByIdAndUserId(Long id, String userId);
}
