package contest.mobicom_contest.member.model;

import lombok.Getter;

@Getter
public enum Role {
    USER("ROLE_USER"), ADMIN("ROLE_ADMIN");

    private final String authority;

    Role(String authority) {
        this.authority = authority;
    }
}
