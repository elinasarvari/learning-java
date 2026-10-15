package com.example.app.service.security;

import com.example.app.repository.security.UserRestrictionRepository;
import com.example.app.repository.security.entity.UserRestrictionEntity;
import com.example.app.service.security.domain.AttributeRestriction;
import com.example.app.service.security.domain.EntityRestrictions;
import com.example.app.service.security.domain.RestrictedEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worked example of STD-SEC-005 (fail closed) and STD-SEC-002 (value object).
 *
 * <p>This service depends on a repository. The repository does not depend on it. That
 * one-way arrow is the whole pattern.
 */
@Service
public class SecurityPolicyServiceImpl implements SecurityPolicyService {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyServiceImpl.class);

    private final UserRestrictionRepository userRestrictionRepository;

    public SecurityPolicyServiceImpl(UserRestrictionRepository userRestrictionRepository) {
        this.userRestrictionRepository = userRestrictionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public EntityRestrictions resolve(Long userId, Long accountId, RestrictedEntity entity) {
        Objects.requireNonNull(entity, "entity");

        if (userId == null || accountId == null) {
            // Not "skip the filter" - refuse. A missing caller identity on a path that
            // exists to filter by caller identity is a bug, and the safe reading of a bug
            // is deny, not allow.
            throw new IllegalArgumentException(
                    "Cannot resolve %s restrictions without both userId and accountId".formatted(entity));
        }

        List<UserRestrictionEntity> rows;
        try {
            rows = userRestrictionRepository.findByUserIdAndAccountIdAndEntityType(userId, accountId, entity);
        } catch (DataAccessException e) {
            // Catch DataAccessException, not Exception: a bare catch here would also
            // swallow the IllegalArgumentException above and any programming error below,
            // converting real bugs into a 503 that looks transient and gets retried.
            log.error("Policy store unreadable for user {} account {} entity {}", userId, accountId, entity, e);
            throw new AccessPolicyUnavailableException(
                    "Access policy is temporarily unavailable", e);
        }

        if (rows.isEmpty()) {
            // Documented decision, not a fallthrough: a user with no configured policy is
            // unrestricted. Invert this line if your default should be deny-all - it is
            // the single most consequential line in the class.
            log.debug("No {} restrictions configured for user {} on account {}", entity, userId, accountId);
            return EntityRestrictions.none(entity);
        }

        Map<String, List<String>> byField = rows.stream()
                .collect(Collectors.groupingBy(
                        UserRestrictionEntity::getFieldName,
                        LinkedHashMap::new,
                        Collectors.mapping(UserRestrictionEntity::getAllowedValue, Collectors.toList())));

        List<AttributeRestriction> attributes = byField.entrySet().stream()
                .map(e -> new AttributeRestriction(e.getKey(), e.getValue()))
                .toList();

        log.debug("Resolved {} {} restriction(s) for user {}", attributes.size(), entity, userId);
        return new EntityRestrictions(entity, attributes);
    }
}
