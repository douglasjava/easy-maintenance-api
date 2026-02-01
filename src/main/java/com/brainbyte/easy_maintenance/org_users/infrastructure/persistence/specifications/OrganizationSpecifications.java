package com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.specifications;

import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Plan;
import org.springframework.data.jpa.domain.Specification;

public class OrganizationSpecifications {

    public static Specification<Organization> withNameLike(String name) {
        return (root, query, criteriaBuilder) -> {
            if (name == null || name.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%");
        };
    }

    public static Specification<Organization> withPlan(Plan plan) {
        return (root, query, criteriaBuilder) -> {
            if (plan == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("plan"), plan);
        };
    }

    public static Specification<Organization> withCityLike(String city) {
        return (root, query, criteriaBuilder) -> {
            if (city == null || city.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(criteriaBuilder.lower(root.get("city")), "%" + city.toLowerCase() + "%");
        };
    }

    public static Specification<Organization> withDocLike(String doc) {
        return (root, query, criteriaBuilder) -> {
            if (doc == null || doc.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(criteriaBuilder.lower(root.get("doc")), "%" + doc.toLowerCase() + "%");
        };
    }
}
