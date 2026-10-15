package com.example.app.repository.security.entity;

import com.example.app.service.security.domain.RestrictedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One permitted value, for one field, for one user on one account.
 *
 * <p>Normalised to a row per value rather than a comma-delimited column: it keeps the
 * policy queryable ("who can see region OH?"), avoids an {@code AttributeConverter}, and
 * means adding a value is an insert rather than a read-modify-write.
 *
 * <p>{@link com.example.app.service.security.SecurityPolicyServiceImpl} groups these rows
 * by field into {@link com.example.app.service.security.domain.AttributeRestriction}s.
 */
@Entity
@Table(
        name = "user_restriction",
        indexes = @Index(
                name = "ix_user_restriction_lookup",
                columnList = "user_id, account_id, entity_type"))
public class UserRestrictionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /**
     * STRING, never the ORDINAL default: an ordinal silently re-points every stored row
     * at a different constant the moment someone reorders the enum (STD-BE-011).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private RestrictedEntity entityType;

    @Column(name = "field_name", nullable = false, length = 64)
    private String fieldName;

    @Column(name = "allowed_value", nullable = false, length = 255)
    private String allowedValue;

    protected UserRestrictionEntity() {
        // required by JPA
    }

    public UserRestrictionEntity(
            Long userId, Long accountId, RestrictedEntity entityType, String fieldName, String allowedValue) {
        this.userId = userId;
        this.accountId = accountId;
        this.entityType = entityType;
        this.fieldName = fieldName;
        this.allowedValue = allowedValue;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public RestrictedEntity getEntityType() {
        return entityType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getAllowedValue() {
        return allowedValue;
    }
}
