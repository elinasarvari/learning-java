package com.example.app.repository.security;

import com.example.app.repository.security.entity.UserRestrictionEntity;
import com.example.app.service.security.domain.RestrictedEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The policy store. A plain Spring Data interface - no implementation class, no
 * {@code @Repository} annotation (STD-BE-004).
 */
public interface UserRestrictionRepository extends JpaRepository<UserRestrictionEntity, Long> {

    List<UserRestrictionEntity> findByUserIdAndAccountIdAndEntityType(
            Long userId, Long accountId, RestrictedEntity entityType);
}
