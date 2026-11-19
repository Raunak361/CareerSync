package com.example.careerMonitor.repository;

import com.example.careerMonitor.Entity.CompanyDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepo extends JpaRepository<CompanyDetails, Long> {

    Optional<CompanyDetails> findById(Long id);

    List<CompanyDetails> findAll();


}
