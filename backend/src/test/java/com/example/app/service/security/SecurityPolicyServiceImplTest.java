package com.example.app.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.app.repository.security.UserRestrictionRepository;
import com.example.app.repository.security.entity.UserRestrictionEntity;
import com.example.app.service.security.domain.AttributeRestriction;
import com.example.app.service.security.domain.EntityRestrictions;
import com.example.app.service.security.domain.RestrictedEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

/** STD-SEC-005: the policy resolver must fail closed. */
@ExtendWith(MockitoExtension.class)
class SecurityPolicyServiceImplTest {

    @Mock
    private UserRestrictionRepository restrictionRepository;

    private SecurityPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SecurityPolicyServiceImpl(restrictionRepository);
    }

    @Test
    @DisplayName("groups policy rows into one AttributeRestriction per field")
    void resolve_should_group_rows_by_field() {
        when(restrictionRepository.findByUserIdAndAccountIdAndEntityType(2L, 100L, RestrictedEntity.INVOICE))
                .thenReturn(List.of(
                        new UserRestrictionEntity(2L, 100L, RestrictedEntity.INVOICE, "region", "OH"),
                        new UserRestrictionEntity(2L, 100L, RestrictedEntity.INVOICE, "region", "MI"),
                        new UserRestrictionEntity(2L, 100L, RestrictedEntity.INVOICE, "status", "PAID")));

        EntityRestrictions result = service.resolve(2L, 100L, RestrictedEntity.INVOICE);

        assertThat(result.attributes())
                .containsExactlyInAnyOrder(
                        new AttributeRestriction("region", List.of("OH", "MI")),
                        new AttributeRestriction("status", List.of("PAID")));
    }

    @Test
    @DisplayName("a user with no policy rows resolves to none(), not to an error")
    void resolve_should_return_none_when_no_policy_rows_exist() {
        when(restrictionRepository.findByUserIdAndAccountIdAndEntityType(3L, 100L, RestrictedEntity.INVOICE))
                .thenReturn(List.of());

        EntityRestrictions result = service.resolve(3L, 100L, RestrictedEntity.INVOICE);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.entity()).isEqualTo(RestrictedEntity.INVOICE);
    }

    @Test
    @DisplayName("a policy store failure throws rather than resolving to no restrictions")
    void resolve_should_fail_closed_when_the_policy_store_is_unavailable() {
        when(restrictionRepository.findByUserIdAndAccountIdAndEntityType(1L, 100L, RestrictedEntity.INVOICE))
                .thenThrow(new QueryTimeoutException("policy store timeout"));

        // The regression this guards: returning EntityRestrictions.none() here would
        // produce an unfiltered query and hand the caller every row, with a 200.
        assertThatThrownBy(() -> service.resolve(1L, 100L, RestrictedEntity.INVOICE))
                .isInstanceOf(AccessPolicyUnavailableException.class);
    }

    @Test
    @DisplayName("a missing caller identity is rejected, not treated as unrestricted")
    void resolve_should_reject_a_null_user_id() {
        assertThatThrownBy(() -> service.resolve(null, 100L, RestrictedEntity.INVOICE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
