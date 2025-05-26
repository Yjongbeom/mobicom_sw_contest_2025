package contest.mobicom_contest.member.service;

import contest.mobicom_contest.jwt.JwtToken;
import contest.mobicom_contest.jwt.JwtTokenProvider;
import contest.mobicom_contest.member.model.Member;
import contest.mobicom_contest.member.model.MemberRepository;
import contest.mobicom_contest.member.model.Role;
import contest.mobicom_contest.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class MemberServiceImpl implements MemberService {
    private final MemberRepository memberRepository;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Override
    public JwtToken signIn(String username, String password) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        JwtToken jwtToken = jwtTokenProvider.generateToken(authentication);
        return jwtToken;
    }

    @Override
    public Member signUp(String username, String password, Role role, String nationality, String language, Integer experienceYears, String workLocation, String phone, String nickname) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        if (memberRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        Member member = Member.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .role(role)
                .nationality(nationality)
                .language(language)
                .experienceYears(experienceYears)
                .workLocation(workLocation)
                .phone(phone)
                .nickname(nickname)
                .build();

        return memberRepository.save(member);
    }

    @Override
    public Member retrieveUser(Long memberId) {
        // JWT 방식
        // String username = SecurityContextHolder.getContext().getAuthentication().getName();
        // return memberRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found"));

        // memberId 방식
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("User not found. id: " + memberId));
    }

    @Override
    public Member updateUser(Long memberId, MemberDto memberDto) {
        // JWT 방식
        // String username = SecurityContextHolder.getContext().getAuthentication().getName();
        // Member member = memberRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found"));

        // memberId 방식
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("User not found. id: " + memberId));

        member.setNationality(memberDto.getNationality());
        member.setLanguage(memberDto.getLanguage());
        member.setWorkLocation(memberDto.getWorkLocation());
        member.setExperienceYears(memberDto.getExperienceYears());

        return memberRepository.save(member);
    }

    @Override
    public void deleteUser(Long memberId) {
        // JWT 방식
        // String username = SecurityContextHolder.getContext().getAuthentication().getName();
        // Member member = memberRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found"));

        // memberId 방식
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("User not found. id: " + memberId));

        memberRepository.delete(member);
    }

    @Override
    public Member findById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id: " + id));
    }

    @Override
    public Optional<Member> findByUsername(String username) {
        return memberRepository.findByUsername(username);
    }
}
