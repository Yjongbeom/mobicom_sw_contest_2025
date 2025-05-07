package contest.mobicom_contest.member.service;

import contest.mobicom_contest.member.model.Member;
import contest.mobicom_contest.member.model.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return memberRepository.findByUsername(username)
                .map(this::createdUserDetails)
                .orElseThrow(() -> new UsernameNotFoundException("해당하는 회원을 찾을 수 없습니다."));
    }

    private UserDetails createdUserDetails(Member member) {
        return User.builder()
                .username(member.getUsername())
                .password(member.getPassword())
                .roles(member.getRole().name())
                .build();
    }
}
