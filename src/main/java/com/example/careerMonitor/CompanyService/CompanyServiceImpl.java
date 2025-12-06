package com.example.careerMonitor.CompanyService;

import com.example.careerMonitor.DTO.CompanyRequestDto;
import com.example.careerMonitor.DTO.CompanyResponseDto;
import com.example.careerMonitor.Entity.CompanyDetails;
import com.example.careerMonitor.repository.CompanyRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService{

    @Autowired
    private final CompanyRepo companyRepo;

    @Override
    public List<CompanyResponseDto> getAllCompaniesByUserId(String userId) {
        List<CompanyDetails> companies= companyRepo.findAllByUserId(userId);


        return companies.stream().map(CompanyResponseDto :: new).collect(Collectors.toList());
    }

    @Override
    public CompanyResponseDto addCompany(String userId, CompanyRequestDto companyRequestDto) {
        CompanyDetails company1 = new CompanyDetails();
        company1.setName(companyRequestDto.getName());
        company1.setUrl(companyRequestDto.getUrl());
        company1.setUserId(userId);
        System.out.println("Name from request: " + companyRequestDto.getName());
        System.out.println("Name from request: " + companyRequestDto.getUrl());
        companyRepo.save(company1);
        return new CompanyResponseDto(company1);   // need attention
    }

    @Override
    @Transactional
    public boolean deleteCompany(long companyId, String userId) {
        try {
            companyRepo.deleteByUserIdAndId(userId,companyId);
        } catch (RuntimeException e) {

            throw new RuntimeException(e);

        }

        return true;
    }
}
