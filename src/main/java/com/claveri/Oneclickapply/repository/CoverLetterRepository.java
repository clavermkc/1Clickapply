package com.claveri.Oneclickapply.repository;

import com.claveri.Oneclickapply.domain.ApplicationStatus;
import com.claveri.Oneclickapply.domain.CoverLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {
    List<CoverLetter> findByStatus(ApplicationStatus status);
    List<CoverLetter> findByCompanyId(Long companyId);
    boolean existsByCompanyId(Long companyId);

    long countBySentAtAfter(LocalDateTime since);
    long countByFollowUpSentAtAfter(LocalDateTime since);

    @Query("select c from CoverLetter c join fetch c.company where c.status = :status order by c.company.matchScore desc")
    List<CoverLetter> findByStatusWithCompany(ApplicationStatus status);

    @Query("select c from CoverLetter c join fetch c.company where c.status = com.claveri.Oneclickapply.domain.ApplicationStatus.SENT and c.sentAt < :before and c.followUpSentAt is null")
    List<CoverLetter> findDueForFollowUp(LocalDateTime before);
}
