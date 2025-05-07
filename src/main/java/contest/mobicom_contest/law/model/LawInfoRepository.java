package contest.mobicom_contest.law.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawInfoRepository extends JpaRepository<LawInfo, Long> {
    List<LawInfo> findByContractContractId(Long contractId);
}

