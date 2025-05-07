package contest.mobicom_contest.member.service;

import contest.mobicom_contest.jwt.JwtToken;
import contest.mobicom_contest.member.model.Member;
import contest.mobicom_contest.member.model.Role;
import contest.mobicom_contest.member.dto.MemberDto;

public interface MemberService {
    // 로그인
    JwtToken signIn(String username, String password);

    // 회원가입
    Member signUp(String username, String password, Role role, String nationality, String language, Integer experienceYears, String workLocation);

    // JWT 기반 사용자 수정 (사용 안함)
    // Member updateUser(MemberDto memberDto);

    // JWT 기반 사용자 조회 (사용 안함)
    // Member retrieveUser();

    // JWT 기반 사용자 삭제 (사용 안함)
    // void deleteUser();

    // 🔄 memberId 기반 수정
    Member updateUser(Long memberId, MemberDto memberDto);

    // 🔄 memberId 기반 조회
    Member retrieveUser(Long memberId);

    // 🔄 memberId 기반 삭제
    void deleteUser(Long memberId);

    Member findById(Long id);
}
