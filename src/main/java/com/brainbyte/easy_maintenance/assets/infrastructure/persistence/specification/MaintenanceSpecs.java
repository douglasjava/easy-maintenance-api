package com.brainbyte.easy_maintenance.assets.infrastructure.persistence.specification;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceFilter;
import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public final class MaintenanceSpecs {

    private MaintenanceSpecs() {}

    public static Specification<Maintenance> filter(String orgId, MaintenanceFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            Subquery<Long> subquery = query.subquery(Long.class);
            Root<MaintenanceItem> itemRoot = subquery.from(MaintenanceItem.class);

            subquery.select(itemRoot.get("id"))
                    .where(
                            cb.equal(itemRoot.get("organizationCode"), orgId),
                            cb.equal(itemRoot.get("id"), root.get("itemId"))
                    );

            predicates.add(cb.exists(subquery));

            if (f.itemId() != null) {
                predicates.add(cb.equal(root.get("itemId"), f.itemId()));
            }

            if (f.performedAt() != null) {
                predicates.add(cb.equal(root.get("performedAt"), f.performedAt()));
            } else {
                if (f.performedAtFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("performedAt"), f.performedAtFrom()));
                }
                if (f.performedAtTo() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("performedAt"), f.performedAtTo()));
                }
            }

            if (f.type() != null) {
                predicates.add(cb.equal(root.get("type"), f.type()));
            }

            if (isNotBlank(f.performedBy())) {
                String pattern = "%" + f.performedBy()
                        .toLowerCase()
                        .replace("%", "\\%")
                        .replace("_", "\\_") + "%";

                predicates.add(cb.like(cb.lower(root.get("performedBy")), pattern, '\\'));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Maintenance> filterCrossOrg(List<String> orgCodes,
                                                             LocalDate performedAtFrom,
                                                             LocalDate performedAtTo,
                                                             MaintenanceType type,
                                                             String itemType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            Subquery<Long> subquery = query.subquery(Long.class);
            Root<MaintenanceItem> itemRoot = subquery.from(MaintenanceItem.class);
            subquery.select(itemRoot.get("id"))
                    .where(
                            itemRoot.get("organizationCode").in(orgCodes),
                            cb.equal(itemRoot.get("id"), root.get("itemId"))
                    );
            predicates.add(cb.exists(subquery));

            if (performedAtFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("performedAt"), performedAtFrom));
            }
            if (performedAtTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("performedAt"), performedAtTo));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (isNotBlank(itemType)) {
                String pattern = "%" + itemType.toLowerCase()
                        .replace("%", "\\%")
                        .replace("_", "\\_") + "%";
                Subquery<Long> itemTypeSubquery = query.subquery(Long.class);
                Root<MaintenanceItem> itRoot = itemTypeSubquery.from(MaintenanceItem.class);
                itemTypeSubquery.select(itRoot.get("id"))
                        .where(
                                cb.equal(itRoot.get("id"), root.get("itemId")),
                                cb.like(cb.lower(itRoot.get("itemType")), pattern, '\\')
                        );
                predicates.add(cb.exists(itemTypeSubquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
