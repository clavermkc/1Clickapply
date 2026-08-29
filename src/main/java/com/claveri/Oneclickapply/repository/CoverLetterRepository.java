package com.claveri.Oneclickapply.repository;

import com.claveri.Oneclickapply.domain.ApplicationStatus;
import com.claveri.Oneclickapply.domain.CoverLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {
    List<CoverLetter> findByStatus(ApplicationStatus status);
    List<CoverLetter> findByCompanyId(Long companyId);
}
