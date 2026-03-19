package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.model.UserFarmMembership;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.auth.service.CustomerNumberService;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.dto.*;
import mofo.com.pestscout.farm.model.*;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.farm.security.FarmAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FarmService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmService.class);
    private static final UUID UNASSIGNED_USER_ID = new UUID(0L, 0L);

    private final FarmRepository farmRepository;
    private final FarmAccessService farmAccess;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final UserFarmMembershipRepository membershipRepository;
    private final CustomerNumberService customerNumberService;
    private final CacheService cacheService;
    private final FarmAreaAllocationService farmAreaAllocationService;

    /**
     * SUPER_ADMIN ONLY.
     * Creates a new farm and assigns initial licensing settings.
     */
    @Transactional
    public FarmResponse createFarm(CreateFarmRequest request) {
        farmAccess.requireSuperAdmin();
        LOGGER.info("Creating farm '{}'", request.name());

        farmRepository.findByNameIgnoreCase(request.name()).ifPresent(existing -> {
            throw new ConflictException("Farm already exists: " + request.name());
        });

        String countryCode = customerNumberService.normalizeCountryCode(request.country());
        farmAreaAllocationService.validateFarmStructureAreas(
                request.licensedAreaHectares(),
                request.greenhouses(),
                request.fieldBlocks()
        );

        Farm farm = toFarmEntity(request, countryCode);
        Farm saved = farmRepository.save(farm);
        synchronizeFarmMemberships(saved, saved.getOwner(), saved.getScout(), request.memberAssignments(), true);

        LOGGER.info("Farm '{}' created with id {}", saved.getName(), saved.getId());
        return mapToResponse(saved);
    }

    /**
     * Updates farm information.
     * SUPER_ADMIN can update all fields.
     * FARM_ADMIN/MANAGER can only update non-license fields.
     *
     * Cache is evicted after update to ensure fresh data.
     */
    @Transactional
    @CacheEvict(value = "farms", keyGenerator = "tenantAwareKeyGenerator")
    public FarmResponse updateFarm(UUID farmId, UpdateFarmRequest request) {

        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        // Access: super admin OR farm owner/manager
        farmAccess.requireAdminOrSuperAdmin(farm);

        LOGGER.info("Updating farm {} ({})", farm.getName(), farm.getId());

        // Enforce name uniqueness
        String requestedName = normalizeNullableText(request.name());
        if (requestedName != null && !farm.getName().equalsIgnoreCase(requestedName)) {
            farmRepository.findByNameIgnoreCase(requestedName)
                    .filter(existing -> !existing.getId().equals(farmId))
                    .ifPresent(existing -> {
                        throw new ConflictException("Farm already exists with name " + requestedName);
                    });
        }

        // Apply field updates based on role
        applyAllowedFarmUpdates(farm, request);

        Farm saved = farmRepository.save(farm);
        synchronizeFarmMemberships(
                saved,
                saved.getOwner(),
                saved.getScout(),
                request.memberAssignments(),
                request.memberAssignments() != null
        );

        // Clear all related caches (greenhouses, sessions, analytics, etc.)
        cacheService.evictFarmCachesAfterCommit(farmId);

        LOGGER.info("Farm '{}' updated successfully", saved.getName());
        return mapToResponse(saved);
    }

    /**
     * Returns all farms visible to this user.
     * SUPER_ADMIN sees all.
     * FARM_ADMIN/MANAGER sees farms they own or belong to through active memberships.
     * SCOUT sees only the farm they are assigned to.
     *
     * Cached per user role to improve performance.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = "farms-list",
            keyGenerator = "tenantAwareKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<FarmResponse> listFarms() {
        LOGGER.info("Listing farms for current user");
        List<Farm> farms;

        switch (farmAccess.getCurrentUserRole()) {
            case SUPER_ADMIN -> farms = farmRepository.findAll();
            case FARM_ADMIN, MANAGER -> farms = resolveManagedFarms(currentUserService.getCurrentUserId());
            case SCOUT -> throw new ForbiddenException("Scouts cannot access farm dashboards.");
            default -> farms = List.of();
        }

        return farms.stream()
                .sorted(Comparator.comparing(Farm::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Loads a single farm.
     * Cached for 2 hours since farm metadata rarely changes.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = "farms",
            keyGenerator = "tenantAwareKeyGenerator",
            unless = "#result == null"
    )
    public FarmResponse getFarm(UUID farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        farmAccess.requireViewAccess(farm);
        return mapToResponse(farm);
    }

    // -------------------------------
    // INTERNAL HELPERS
    // -------------------------------

    private Farm toFarmEntity(CreateFarmRequest request, String countryCode) {
        User owner = resolveAssignedUser(request.ownerId(), "owner");
        User scout = resolveAssignedUser(request.scoutId(), "scout");
        boolean hasGreenhouses = request.greenhouses() != null && !request.greenhouses().isEmpty();
        boolean hasFieldBlocks = request.fieldBlocks() != null && !request.fieldBlocks().isEmpty();

        Farm farm = Farm.builder()
                .name(request.name())
                .description(request.description())
                .externalId(generateExternalId())
                .farmTag(generateFarmTag(countryCode, request.name()))
                .address(request.address())
                .latitude(CoordinateFormatSupport.validateLatitude(request.latitude()))
                .longitude(CoordinateFormatSupport.validateLongitude(request.longitude()))
                .city(request.city())
                .province(request.province())
                .postalCode(request.postalCode())
                .country(request.country())
                .owner(owner)
                .scout(scout)
                .contactName(resolveContactName(request.contactName(), owner))
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .subscriptionStatus(request.subscriptionStatus())
                .subscriptionTier(request.subscriptionTier())
                .billingEmail(request.billingEmail())
                .licensedAreaHectares(request.licensedAreaHectares())
                .licensedUnitQuota(request.licensedUnitQuota())
                .quotaDiscountPercentage(request.quotaDiscountPercentage())
                .structureType(resolveStructureType(request.structureType(), hasGreenhouses, hasFieldBlocks))
                .defaultBayCount(request.defaultBayCount() != null ? request.defaultBayCount() : 0)
                .defaultBenchesPerBay(request.defaultBenchesPerBay() != null ? request.defaultBenchesPerBay() : 0)
                .defaultSpotChecksPerBench(request.defaultSpotChecksPerBench())
                .timezone(request.timezone())
                .licenseExpiryDate(request.licenseExpiryDate())
                .autoRenewEnabled(request.autoRenewEnabled())
                .build();

        if (request.greenhouses() != null) {
            request.greenhouses().forEach(ghRequest -> farm.getGreenhouses().add(buildGreenhouse(farm, ghRequest)));
        }

        if (request.fieldBlocks() != null) {
            request.fieldBlocks().forEach(blockRequest -> farm.getFieldBlocks().add(buildFieldBlock(farm, blockRequest)));
        }

        return farm;
    }

    /**
     * Applies allowed updates depending on user role.
     */
    private void applyAllowedFarmUpdates(Farm farm, UpdateFarmRequest request) {

        // Common fields editable by FARM_MANAGER or SUPER_ADMIN
        applyTextUpdate(request.name(), farm::setName);
        applyTextUpdate(request.description(), farm::setDescription);
        applyTextUpdate(request.address(), farm::setAddress);
        applyTextUpdate(request.city(), farm::setCity);
        applyTextUpdate(request.province(), farm::setProvince);
        applyTextUpdate(request.postalCode(), farm::setPostalCode);
        applyTextUpdate(request.country(), farm::setCountry);
        applyTextUpdate(request.contactName(), farm::setContactName);
        applyTextUpdate(request.contactEmail(), farm::setContactEmail);
        applyTextUpdate(request.contactPhone(), farm::setContactPhone);
        applyTextUpdate(request.timezone(), farm::setTimezone);

        // Defaults (manager allowed)
        if (request.defaultBayCount() != null)
            farm.setDefaultBayCount(request.defaultBayCount());
        if (request.defaultBenchesPerBay() != null)
            farm.setDefaultBenchesPerBay(request.defaultBenchesPerBay());
        if (request.defaultSpotChecksPerBench() != null)
            farm.setDefaultSpotChecksPerBench(request.defaultSpotChecksPerBench());

        if (farmAccess.isSuperAdmin()) {
            if (request.subscriptionStatus() != null) {
                farm.setSubscriptionStatus(request.subscriptionStatus());
            }
            if (request.subscriptionTier() != null) {
                farm.setSubscriptionTier(request.subscriptionTier());
            }
            applyTextUpdate(request.billingEmail(), farm::setBillingEmail);

            if (request.licensedAreaHectares() != null) {
                farmAreaAllocationService.validateCurrentAllocationWithinLicense(farm, request.licensedAreaHectares());
                farm.setLicensedAreaHectares(request.licensedAreaHectares());
            }
            if (request.licensedUnitQuota() != null) {
                farm.setLicensedUnitQuota(request.licensedUnitQuota());
            }
            if (request.quotaDiscountPercentage() != null) {
                farm.setQuotaDiscountPercentage(request.quotaDiscountPercentage());
            }
            if (request.licenseExpiryDate() != null) {
                farm.setLicenseExpiryDate(request.licenseExpiryDate());
            }
            if (request.licenseGracePeriodEnd() != null) {
                farm.setLicenseGracePeriodEnd(request.licenseGracePeriodEnd());
            }
            if (request.licenseArchivedDate() != null) {
                farm.setLicenseArchivedDate(request.licenseArchivedDate());
            }
            if (request.autoRenewEnabled() != null) {
                farm.setAutoRenewEnabled(request.autoRenewEnabled());
            }
            Boolean requestedArchivedState = request.isArchived() != null
                    ? request.isArchived()
                    : request.accessLocked();
            if (requestedArchivedState != null) {
                farm.setIsArchived(requestedArchivedState);
            }
            if (request.latitude() != null) {
                farm.setLatitude(CoordinateFormatSupport.validateLatitude(request.latitude()));
            }
            if (request.longitude() != null) {
                farm.setLongitude(CoordinateFormatSupport.validateLongitude(request.longitude()));
            }

            if (request.ownerId() != null) {
                User owner = resolveAssignedUser(request.ownerId(), "owner");
                farm.setOwner(owner);
                if (request.contactName() == null) {
                    farm.setContactName(resolveContactName(null, owner));
                }
            }
            if (request.scoutId() != null) {
                farm.setScout(resolveAssignedUser(request.scoutId(), "scout"));
            }
        }
    }

    private void applyTextUpdate(String requestedValue, Consumer<String> setter) {
        if (requestedValue == null) {
            return;
        }
        setter.accept(normalizeNullableText(requestedValue));
    }

    private FarmResponse mapToResponse(Farm farm) {
        // Map in the context of the current user's role so we can hide licensing data from scouts.
        Role role = farmAccess.getCurrentUserRole();
        boolean hideLicense = role == Role.SCOUT;

        return new FarmResponse(
                farm.getId(),
                farm.getFarmTag(),

                farm.getName(),
                farm.getDescription(),
                farm.getExternalId(),
                farm.getAddress(),
                farm.getLatitude(),
                farm.getLongitude(),
                farm.getCity(),
                farm.getProvince(),
                farm.getPostalCode(),
                farm.getCountry(),

                farm.getContactName(),
                farm.getContactEmail(),
                farm.getContactPhone(),

                farm.getSubscriptionStatus(),
                farm.getSubscriptionTier(),
                hideLicense ? null : farm.getBillingEmail(),

                farm.getLicensedAreaHectares(),
                hideLicense ? null : farm.getLicensedUnitQuota(),
                hideLicense ? null : farm.getQuotaDiscountPercentage(),
                hideLicense ? null : farm.getLicenseExpiryDate(),
                hideLicense ? null : farm.getAutoRenewEnabled(),
                // accessLocked is a view-only flag that mirrors license state for non-scouters; scouts do not see it.
                hideLicense ? null : farm.isExpired() || farm.inGracePeriod() || Boolean.TRUE.equals(farm.getIsArchived()),
                farm.getStructureType(),

                farm.getDefaultBayCount() != null ? farm.getDefaultBayCount() : 0,
                farm.getDefaultBenchesPerBay() != null ? farm.getDefaultBenchesPerBay() : 0,
                farm.getDefaultSpotChecksPerBench(),
                toInstant(farm.getCreatedAt()),
                toInstant(farm.getUpdatedAt()),
                farm.getTimezone(),
                farm.getOwner() != null ? farm.getOwner().getId() : null,
                farm.getScout() != null ? farm.getScout().getId() : null
        );
    }

    private String generateExternalId() {
        String externalId;

        do {
            externalId = UUID.randomUUID().toString();
        } while (farmRepository.existsByExternalId(externalId));

        return externalId;
    }

    private String generateFarmTag(String countryCode, String farmName) {
        String prefix = (countryCode == null || countryCode.isBlank())
                ? "ZZ"
                : countryCode.trim().toUpperCase(Locale.ROOT);
        String base = normalizeFarmTagPart(farmName);
        String candidate = buildFarmTag(prefix, base, "");
        int suffix = 1;

        while (farmRepository.existsByFarmTag(candidate)) {
            suffix++;
            candidate = buildFarmTag(prefix, base, "-" + suffix);
        }

        return candidate;
    }

    private String normalizeFarmTagPart(String farmName) {
        if (farmName == null) {
            return "FARM";
        }
        String normalized = farmName.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "FARM" : normalized;
    }

    private String buildFarmTag(String prefix, String base, String suffix) {
        int maxBaseLength = 32 - prefix.length() - 1 - suffix.length();
        String trimmedBase = base;
        if (maxBaseLength < 1) {
            trimmedBase = "FARM";
        } else if (base.length() > maxBaseLength) {
            trimmedBase = base.substring(0, maxBaseLength);
        }
        return prefix + "-" + trimmedBase + suffix;
    }

    private List<Farm> resolveManagedFarms(UUID userId) {
        Map<UUID, Farm> farmsById = new LinkedHashMap<>();

        farmRepository.findByOwnerId(userId).forEach(farm -> farmsById.put(farm.getId(), farm));

        membershipRepository.findByUser_Id(userId).stream()
                .filter(membership -> Boolean.TRUE.equals(membership.getIsActive()))
                .filter(membership -> membership.getRole() == Role.FARM_ADMIN || membership.getRole() == Role.MANAGER)
                .map(UserFarmMembership::getFarm)
                .forEach(farm -> farmsById.put(farm.getId(), farm));

        return new ArrayList<>(farmsById.values());
    }


    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> defaultTags(List<String> providedTags, String prefix, int count) {
        if (count <= 0) {
            return providedTags;
        }
        if (providedTags.isEmpty()) {
            return java.util.stream.IntStream.rangeClosed(1, count)
                    .mapToObj(index -> prefix + "-" + index)
                    .toList();
        }
        if (providedTags.size() >= count) {
            return providedTags.stream()
                    .limit(count)
                    .toList();
        }

        List<String> paddedTags = new ArrayList<>(providedTags);
        for (int index = paddedTags.size() + 1; index <= count; index++) {
            paddedTags.add(prefix + "-" + index);
        }
        return List.copyOf(paddedTags);
    }

    private Greenhouse buildGreenhouse(Farm farm, CreateGreenhouseRequest request) {
        GreenhouseLayout layout = resolveGreenhouseLayout(
                farm,
                request.bayCount(),
                request.benchesPerBay(),
                request.bayTags(),
                request.benchTags(),
                request.bays()
        );

        return Greenhouse.builder()
                .farm(farm)
                .name(request.name())
                .description(request.description())
                .bayCount(layout.bayCount())
                .benchesPerBay(layout.maxBedCount())
                .spotChecksPerBench(request.spotChecksPerBench() != null ? request.spotChecksPerBench() : farm.resolveSpotChecksPerBench())
                .areaHectares(request.areaHectares())
                .bayTags(layout.bayTags())
                .benchTags(layout.bedTags())
                .bays(layout.bays())
                .build();
    }

    private FieldBlock buildFieldBlock(Farm farm, CreateFieldBlockRequest request) {
        int bayCount = request.bayCount() != null ? request.bayCount() : 0;
        int spotChecksPerBay = request.spotChecksPerBay() != null ? request.spotChecksPerBay() : farm.resolveSpotChecksPerBench();
        List<String> bayTags = defaultTags(normalizeTags(request.bayTags()), "Bay", bayCount);

        return FieldBlock.builder()
                .farm(farm)
                .name(request.name())
                .bayCount(bayCount)
                .spotChecksPerBay(spotChecksPerBay)
                .areaHectares(request.areaHectares())
                .cropType(normalizeNullableText(request.cropType()))
                .bayTags(bayTags)
                .active(Boolean.TRUE.equals(request.active()))
                .build();
    }

    private GreenhouseLayout resolveGreenhouseLayout(
            Farm farm,
            Integer requestedBayCount,
            Integer requestedBedsPerBay,
            List<String> requestedBayTags,
            List<String> requestedBedTags,
            List<GreenhouseBayRequest> requestedBays
    ) {
        if (requestedBays != null && !requestedBays.isEmpty()) {
            List<GreenhouseBayDefinition> bays = normalizeBays(requestedBays, normalizeTags(requestedBedTags));
            int maxBedCount = bays.stream()
                    .mapToInt(GreenhouseBayDefinition::getBedCount)
                    .max()
                    .orElse(0);
            return new GreenhouseLayout(
                    bays.size(),
                    maxBedCount,
                    bays.stream().map(GreenhouseBayDefinition::getBayTag).toList(),
                    collectBedTags(bays),
                    bays
            );
        }

        int bayCount = requestedBayCount != null ? requestedBayCount : 0;
        int maxBedCount = requestedBedsPerBay != null ? requestedBedsPerBay : 0;
        return new GreenhouseLayout(
                bayCount,
                maxBedCount,
                defaultTags(normalizeTags(requestedBayTags), "Bay", bayCount),
                defaultTags(normalizeTags(requestedBedTags), "Bed", maxBedCount),
                List.of()
        );
    }

    private List<GreenhouseBayDefinition> normalizeBays(
            List<GreenhouseBayRequest> requestedBays,
            List<String> fallbackBedTags
    ) {
        List<GreenhouseBayDefinition> bays = requestedBays.stream()
                .map(request -> {
                    String bayTag = normalizeNullableText(request.bayTag());
                    if (bayTag == null) {
                        throw new BadRequestException("Each greenhouse bay must have a name.");
                    }
                    if (request.bedCount() == null || request.bedCount() < 1) {
                        throw new BadRequestException("Each greenhouse bay must define at least one bed.");
                    }
                    List<String> bedTags = defaultTags(
                            normalizeTags(
                                    request.bedTags() == null || request.bedTags().isEmpty()
                                            ? fallbackBedTags
                                            : request.bedTags()
                            ),
                            "Bed",
                            request.bedCount()
                    );
                    long distinctBedTags = bedTags.stream().distinct().count();
                    if (distinctBedTags != bedTags.size()) {
                        throw new BadRequestException("Bed names within a greenhouse bay must be unique.");
                    }
                    return GreenhouseBayDefinition.builder()
                            .bayTag(bayTag)
                            .bedCount(request.bedCount())
                            .bedTags(bedTags)
                            .build();
                })
                .map(GreenhouseBayDefinition.class::cast)
                .toList();

        long distinctTags = bays.stream()
                .map(GreenhouseBayDefinition::getBayTag)
                .distinct()
                .count();
        if (distinctTags != bays.size()) {
            throw new BadRequestException("Greenhouse bay names must be unique.");
        }
        return bays;
    }

    private List<String> collectBedTags(List<GreenhouseBayDefinition> bays) {
        LinkedHashSet<String> bedTags = new LinkedHashSet<>();
        bays.forEach(bay -> bedTags.addAll(bay.resolvedBedTags()));
        return List.copyOf(bedTags);
    }

    private record GreenhouseLayout(
            int bayCount,
            int maxBedCount,
            List<String> bayTags,
            List<String> bedTags,
            List<GreenhouseBayDefinition> bays
    ) {
    }


    private FarmStructureType resolveStructureType(FarmStructureType requested,
                                                   boolean hasGreenhouses,
                                                   boolean hasFieldBlocks) {
        if (requested != null) {
            return requested;
        }
        if (hasFieldBlocks && !hasGreenhouses) {
            return FarmStructureType.FIELD;
        }
        if (hasGreenhouses && !hasFieldBlocks) {
            return FarmStructureType.GREENHOUSE;
        }
        return FarmStructureType.OTHER;
    }

    private User resolveAssignedUser(UUID userId, String fieldName) {
        if (userId == null || UNASSIGNED_USER_ID.equals(userId)) {
            return null;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!user.isActive()) {
            throw new BadRequestException("Assigned " + fieldName + " must be active.");
        }
        if ("owner".equals(fieldName) && user.getRole() != Role.FARM_ADMIN && user.getRole() != Role.MANAGER) {
            throw new BadRequestException("Assigned owner must have FARM_ADMIN or MANAGER role.");
        }
        if ("scout".equals(fieldName) && user.getRole() != Role.SCOUT) {
            throw new BadRequestException("Assigned scout must have SCOUT role.");
        }

        return user;
    }

    private void synchronizeFarmMemberships(
            Farm farm,
            User owner,
            User scout,
            List<FarmMemberAssignmentRequest> requestedAssignments,
            boolean replaceNonPrimaryMembers
    ) {
        List<UserFarmMembership> existingMembershipRows = Optional.ofNullable(membershipRepository.findByFarmId(farm.getId()))
                .orElse(List.of());
        Map<UUID, UserFarmMembership> existingMemberships = existingMembershipRows.stream()
                .collect(Collectors.toMap(
                        membership -> membership.getUser().getId(),
                        membership -> membership,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<UUID, Role> desiredMemberships = new LinkedHashMap<>();
        Map<UUID, User> resolvedUsers = new LinkedHashMap<>();

        if (owner != null) {
            desiredMemberships.put(owner.getId(), owner.getRole());
            resolvedUsers.put(owner.getId(), owner);
        }
        if (scout != null) {
            desiredMemberships.put(scout.getId(), Role.SCOUT);
            resolvedUsers.put(scout.getId(), scout);
        }

        if (requestedAssignments != null) {
            for (FarmMemberAssignmentRequest assignment : requestedAssignments) {
                User user = resolveMembershipUser(assignment);
                Role requestedRole = validateMembershipRole(user, assignment.role());

                Role existingDesiredRole = desiredMemberships.get(user.getId());
                if (existingDesiredRole != null && existingDesiredRole != requestedRole) {
                    throw new BadRequestException("Assigned owner or scout cannot also be attached with a different farm member role.");
                }

                desiredMemberships.putIfAbsent(user.getId(), requestedRole);
                resolvedUsers.putIfAbsent(user.getId(), user);
            }
        }

        for (Map.Entry<UUID, Role> desiredMembership : desiredMemberships.entrySet()) {
            UUID userId = desiredMembership.getKey();
            Role role = desiredMembership.getValue();
            UserFarmMembership membership = existingMemberships.remove(userId);

            if (membership == null) {
                membership = UserFarmMembership.builder()
                        .user(resolvedUsers.get(userId))
                        .farm(farm)
                        .build();
            }

            membership.setRole(role);
            membership.setIsActive(true);
            membershipRepository.save(membership);
        }

        if (replaceNonPrimaryMembers) {
            existingMemberships.values().forEach(membership -> {
                if (Boolean.TRUE.equals(membership.getIsActive())) {
                    membership.setIsActive(false);
                    membershipRepository.save(membership);
                }
            });
        }
    }

    private User resolveMembershipUser(FarmMemberAssignmentRequest assignment) {
        User user = userRepository.findById(assignment.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", assignment.userId()));
        if (!user.isActive()) {
            throw new BadRequestException("Farm members must be active users.");
        }
        return user;
    }

    private Role validateMembershipRole(User user, Role requestedRole) {
        if (requestedRole == Role.SUPER_ADMIN || requestedRole == Role.EDGE_SYNC) {
            throw new BadRequestException("Only SCOUT, MANAGER, and FARM_ADMIN can be attached as farm members.");
        }
        if (user.getRole() != requestedRole) {
            throw new BadRequestException("Farm member role must match the selected user's role.");
        }
        return requestedRole;
    }

    private String resolveContactName(String requestedContactName, User owner) {
        String normalizedContactName = normalizeNullableText(requestedContactName);
        if (normalizedContactName != null) {
            return normalizedContactName;
        }

        return resolveOwnerName(owner);
    }

    private String resolveOwnerName(User owner) {
        if (owner == null) {
            return null;
        }
        String firstName = owner.getFirstName();
        String lastName = owner.getLastName();
        StringBuilder name = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            name.append(firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(lastName.trim());
        }
        String result = name.toString();
        return result.isBlank() ? null : result;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private Instant toInstant(java.time.LocalDateTime value) {
        return value != null ? value.toInstant(ZoneOffset.UTC) : null;
    }
}
