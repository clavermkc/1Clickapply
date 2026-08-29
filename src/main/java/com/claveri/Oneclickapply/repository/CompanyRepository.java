package com.claveri.Oneclickapply.repository;

import com.claveri.Oneclickapply.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByDomain(String domain);
    boolean existsByDomain(String domain);
    List<Company> findByCityIgnoreCase(String city);
    List<Company> findByPrimaryEmailIsNotNull();
}
