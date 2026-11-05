package com.example.careerMonitor.CompanyService;

import com.example.careerMonitor.DTO.CompanyRequestDto;
import com.example.careerMonitor.DTO.CompanyResponseDto;
import com.example.careerMonitor.Entity.CompanyDetails;
import com.example.careerMonitor.repository.CompanyRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyServiceImpl.class);

    private final CompanyRepo companyRepo;

    @Override
    public List<CompanyResponseDto> getAllCompaniesByUserId(String userId) {
        List<CompanyDetails> companies = companyRepo.findAllByUserId(userId);
        return companies.stream().map(CompanyResponseDto::new).collect(Collectors.toList());
    }

    @Override
    public CompanyResponseDto addCompany(String userId, CompanyRequestDto companyRequestDto) {
        // Validate URL format
        try {
            URI.create(companyRequestDto.getUrl()).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL: " + companyRequestDto.getUrl());
        }

        // Check for duplicate URL for this user
        List<CompanyDetails> existing = companyRepo.findAllByUserId(userId);
        boolean duplicate = existing.stream()
                .anyMatch(c -> c.getUrl().equalsIgnoreCase(companyRequestDto.getUrl()));
        if (duplicate) {
            throw new IllegalArgumentException("This career page URL is already being monitored");
        }

        CompanyDetails company = new CompanyDetails();
        company.setName(companyRequestDto.getName());
        company.setUrl(companyRequestDto.getUrl());
        company.setUserId(userId);

        companyRepo.save(company);
        log.info("Added company '{}' for user '{}'", company.getName(), userId);
        return new CompanyResponseDto(company);
    }

    @Override
    @Transactional
    public boolean deleteCompany(long companyId, String userId) {
        Optional<CompanyDetails> companyOpt = companyRepo.findByIdAndUserId(companyId, userId);
        if (companyOpt.isEmpty()) {
            return false;
        }
        companyRepo.delete(companyOpt.get());
        log.info("Deleted company id={} for user '{}'", companyId, userId);
        return true;
    }
}
