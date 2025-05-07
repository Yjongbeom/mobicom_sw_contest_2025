package contest.mobicom_contest;

import contest.mobicom_contest.contract.service.ContractService;
import contest.mobicom_contest.member.service.MemberService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;

@Configuration
public class TestConfig {
    @Bean
    public ContractService contractService() {
        return mock(ContractService.class);
    }

    @Bean
    public MemberService memberService() {
        return mock(MemberService.class);
    }
}
