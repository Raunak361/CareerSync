package com.example.careerMonitor.CompanyService;

import com.example.careerMonitor.DTO.CompanyRequestDto;
import com.example.careerMonitor.DTO.CompanyResponseDto;
import com.example.careerMonitor.Entity.CompanyDetails;
import com.example.careerMonitor.Entity.UserCompanyRelation;
import com.example.careerMonitor.repository.CompanyRepo;
import com.example.careerMonitor.repository.UserCompanyRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyServiceImpl.class);

    private final CompanyRepo companyRepo;
    private final UserCompanyRepo userCompanyRelationRepo;

    @Override
    public List<CompanyResponseDto> getAllCompaniesByUserId(String userId) {

        List<UserCompanyRelation> companyRelations = userCompanyRelationRepo.findAllByUserId(userId);

        List<CompanyDetails> companies = new ArrayList<>();

        for (UserCompanyRelation i : companyRelations) {

            Optional<CompanyDetails> c = companyRepo.findById(i.getCompanyId());

            // Safely unwrap the Optional and add to the list only if it's present
            c.ifPresent(companies::add);
        }

        return companies.stream()
                .map(CompanyResponseDto::new)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<CompanyResponseDto> addCompany(
            String userId,
            CompanyRequestDto companyRequestDto) {

        List<CompanyResponseDto> addedCompanies = new ArrayList<>();

        for (Long companyId : companyRequestDto.getCompanyIds()) {

            // Find company in global company table
            CompanyDetails company = companyRepo.findById(companyId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Company not found: " + companyId));

            // Skip if user already follows this company
            boolean alreadySubscribed =
                    userCompanyRelationRepo
                            .existsByUserIdAndCompanyId(userId, companyId);

            if (alreadySubscribed) {
                continue;
            }

            // Create user-company relation
            UserCompanyRelation relation = new UserCompanyRelation();
            relation.setUserId(userId);
            relation.setCompanyId(companyId);

            userCompanyRelationRepo.save(relation);

            addedCompanies.add(new CompanyResponseDto(company));

            log.info("User '{}' subscribed to company '{}'",
                    userId, company.getName());
        }

        return addedCompanies;
    }

    @Override
    @Transactional
    public boolean deleteCompany(long companyId, String userId) {
        boolean relation = userCompanyRelationRepo.existsByUserIdAndCompanyId(userId,companyId);
        if (!relation) {
            return false;
        }
        userCompanyRelationRepo.delete(userCompanyRelationRepo.findByUserIdAndCompanyId(userId,companyId));
        log.info("Deleted company id={} for user '{}'", companyId, userId);
        return true;
    }

    @Override
    public List<CompanyResponseDto> getAllCompanies() {
        List<CompanyDetails> companies = companyRepo.findAll();
        return companies.stream()
                .map(CompanyResponseDto::new)
                .collect(Collectors.toList());
    }
}
