package contest.mobicom_contest.member.controller;

import contest.mobicom_contest.jwt.JwtToken;
import contest.mobicom_contest.member.model.Member;
import contest.mobicom_contest.member.service.MemberService;
import contest.mobicom_contest.member.dto.MemberDto;
import contest.mobicom_contest.member.dto.SignInDto;
import contest.mobicom_contest.member.dto.SignUpDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class MemberController {
    private final MemberService memberService;

    @Operation(summary = "회원가입")
    @PostMapping("/register")
    public ResponseEntity<JwtToken> register(@Valid @RequestBody SignUpDto signUpDto) {
        Member member = memberService.signUp(
                signUpDto.getUsername(),
                signUpDto.getPassword(),
                signUpDto.getRole(),
                signUpDto.getLanguage(),
                signUpDto.getNationality(),
                signUpDto.getExperienceYears(),
                signUpDto.getWorkLocation()
        );

        JwtToken jwtToken = memberService.signIn(
                signUpDto.getUsername(),
                signUpDto.getPassword()
        );

        log.info("Saved Member: {}", member);
        return ResponseEntity.ok(jwtToken);
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ResponseEntity<JwtToken> login(@RequestBody SignInDto signInDto) {
        JwtToken jwtToken = memberService.signIn(
                signInDto.getUsername(),
                signInDto.getPassword()
        );

        return ResponseEntity.ok(jwtToken);
    }

    @Operation(summary = "회원 정보 조회")
    @GetMapping("/{memberId}")
    public ResponseEntity<MemberDto> retrieveUser(@PathVariable Long memberId) {
        // JWT 기반 조회
        // Member member = memberService.retrieveUser();

        Member member = memberService.retrieveUser(memberId);
        MemberDto memberDto = new MemberDto(
                member.getUsername(),
                member.getNationality(),
                member.getLanguage(),
                member.getWorkLocation(),
                member.getExperienceYears()
        );

        return ResponseEntity.ok(memberDto);
    }

    @Operation(summary = "회원 정보 삭제")
    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long memberId){
        // JWT 기반 삭제
        // memberService.deleteUser();

        memberService.deleteUser(memberId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "회원 정보 수정")
    @PatchMapping("/{memberId}")
    public ResponseEntity<MemberDto> updateUser(@PathVariable Long memberId, @RequestBody MemberDto memberDto) {
        // JWT 기반 수정
        // Member member = memberService.updateUser(memberDto);

        Member member = memberService.updateUser(memberId, memberDto);
        MemberDto updatedDto = new MemberDto(
                member.getNationality(),
                member.getLanguage(),
                member.getWorkLocation(),
                member.getExperienceYears()
        );

        return ResponseEntity.ok(updatedDto);
    }
}
