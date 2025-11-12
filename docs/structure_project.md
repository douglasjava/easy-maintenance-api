src/
└─ main/
├─ java/
│   └─ com/brainbyte/easy_maintenance/
│       ├─ config/
│       │   ├─ OpenApiConfig.java
│       │   └─ JacksonConfig.java                 # timezone/serialização
│       │
│       ├─ shared/
│       │   └─ web/
│       │       ├─ CorsConfig.java
│       │       ├─ GlobalExceptionHandler.java    # ProblemDetails (RFC 7807)
│       │       └─ TenantFilter.java              # lê X-Org-Id
│       │
│       ├─ kernel/
│       │   └─ tenant/
│       │       ├─ TenantContext.java
│       │       └─ RequireTenant.java
│       │
│       ├─ org_users/
│       │   ├─ domain/
│       │   │   ├─ Organization.java
│       │   │   └─ User.java
│       │   └─ infrastructure/
│       │       └─ persistence/
│       │           ├─ OrganizationRepository.java
│       │           └─ UserRepository.java
│       │
│       ├─ catalog_norms/
│       │   ├─ domain/
│       │   │   ├─ Norm.java
│       │   │   └─ enums/
│       │   │       ├─ ItemType.java             # EXTINGUISHER, SPDA, ...
│       │   │       └─ PeriodUnit.java           # DAYS, MONTHS
│       │   ├─ application/
│       │   │   └─ service/
│       │   │       └─ FindNormByItemTypeService.java
│       │   └─ infrastructure/
│       │       ├─ persistence/
│       │       │   └─ NormRepository.java
│       │       └─ web/
│       │           └─ NormsController.java      # GET /api/norms?itemType=
│       │
│       └─ assets/
│           ├─ domain/
│           │   ├─ MaintenanceItem.java
│           │   ├─ Maintenance.java
│           │   ├─ enums/
│           │   │   └─ ItemStatus.java           # OK, NEAR_DUE, OVERDUE
│           │   └─ rules/
│           │       └─ StatusCalculator.java     # cálculo de status
│           ├─ application/
│           │   ├─ dto/
│           │   │   ├─ CreateItemRequest.java
│           │   │   ├─ ItemResponse.java
│           │   │   ├─ RegisterMaintenanceRequest.java
│           │   │   └─ MaintenanceResponse.java
│           │   └─ service/
│           │       ├─ CreateItemWithSuggestedNormService.java
│           │       ├─ RegisterMaintenanceService.java
│           │       └─ ListItemsService.java
│           └─ infrastructure/
│               ├─ persistence/
│               │   ├─ MaintenanceItemRepository.java
│               │   └─ MaintenanceRepository.java
│               └─ web/
│                   └─ ItemsController.java      # POST/GET /api/items, POST /{id}/maintenances
│
└─ resources/
├─ application.properties
└─ db/migration/
├─ V1__init_schema.sql
└─ V2__seed_norms.sql
