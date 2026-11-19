package com.example.careerMonitor.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "user_company_relation",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "company_id"}
        )
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserCompanyRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "user_id", nullable = false)
    private String userId;
}
