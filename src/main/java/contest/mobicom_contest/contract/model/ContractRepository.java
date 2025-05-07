package contest.mobicom_contest.contract.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    List<Contract> findByMemberId(Long memberId);
}
