package com.test.backend.iam.application.internal.commandservices;

import com.test.backend.iam.application.internal.outboundservices.email.EmailService;
import com.test.backend.iam.application.internal.outboundservices.hashing.HashingService;
import com.test.backend.iam.application.internal.outboundservices.tokens.TokenService;
import com.test.backend.iam.application.internal.security.AccountProtectionService;
import com.test.backend.iam.application.internal.security.SecurityEventPublisher;
import com.test.backend.iam.application.internal.security.SessionAuditService;
import com.test.backend.iam.application.internal.security.TwoFactorService;
import com.test.backend.iam.domain.model.aggregates.User;
import com.test.backend.iam.domain.model.commands.ConfirmAccountCommand;
import com.test.backend.iam.domain.model.commands.ResendConfirmationCodeCommand;
import com.test.backend.iam.domain.model.commands.SignInCommand;
import com.test.backend.iam.domain.model.commands.SignUpCommand;
import com.test.backend.iam.domain.model.commands.UpdatePasswordCommand;
import com.test.backend.iam.domain.model.entities.Role;
import com.test.backend.iam.domain.model.events.UserRegisteredEvent;
import com.test.backend.iam.domain.model.exceptions.InvalidTwoFactorCodeException;
import com.test.backend.iam.domain.model.exceptions.UserAlreadyExistsException;
import com.test.backend.iam.domain.model.exceptions.UserNotConfirmedException;
import com.test.backend.iam.domain.model.valueobjects.AuthenticatedUser;
import com.test.backend.iam.domain.model.valueobjects.Roles;
import com.test.backend.iam.domain.services.UserCommandService;
import com.test.backend.iam.infrastructure.persistence.jpa.repositories.IamRoleRepository;
import com.test.backend.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserCommandServiceImpl implements UserCommandService {

    private static final Logger logger = LoggerFactory.getLogger(UserCommandServiceImpl.class);

    private final UserRepository userRepository;
    private final IamRoleRepository roleRepository;
    private final HashingService hashingService;
    private final TokenService tokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailService emailService;
    private final EntityManager entityManager;
    private final TwoFactorService twoFactorService;
    private final SessionAuditService sessionAuditService;
    private final AccountProtectionService accountProtectionService;
    private final SecurityEventPublisher securityEventPublisher;

    public UserCommandServiceImpl(UserRepository userRepository,
                                  IamRoleRepository roleRepository,
                                  HashingService hashingService,
                                  TokenService tokenService,
                                  ApplicationEventPublisher eventPublisher,
                                  EmailService emailService,
                                  EntityManager entityManager,
                                  TwoFactorService twoFactorService,
                                  SessionAuditService sessionAuditService,
                                  AccountProtectionService accountProtectionService,
                                  SecurityEventPublisher securityEventPublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.hashingService = hashingService;
        this.tokenService = tokenService;
        this.eventPublisher = eventPublisher;
        this.emailService = emailService;
        this.entityManager = entityManager;
        this.twoFactorService = twoFactorService;
        this.sessionAuditService = sessionAuditService;
        this.accountProtectionService = accountProtectionService;
        this.securityEventPublisher = securityEventPublisher;
    }

    @Override
    public Optional<AuthenticatedUser> handle(SignInCommand command) {
        String ipAddress = command.ipAddress() != null && !command.ipAddress().isBlank()
                ? command.ipAddress()
                : "unknown";
        var userOpt = userRepository.findByEmail(command.email());
        accountProtectionService.checkNotLocked(userOpt.orElse(null), ipAddress);

        if (userOpt.isEmpty()) {
            accountProtectionService.recordFailedAttempt(null, ipAddress);
            securityEventPublisher.publishLoginFailed(command.email(), null, ipAddress, "Account not found");
            return Optional.empty();
        }

        var user = userOpt.get();
        Long workspaceId = resolveWorkspaceId(user.getEmail());

        if (!hashingService.matches(command.password(), user.getPassword())) {
            accountProtectionService.recordFailedAttempt(user, ipAddress);
            securityEventPublisher.publishLoginFailed(user.getEmail(), workspaceId, ipAddress, "Invalid password");
            return Optional.empty();
        }
        if (!user.isConfirmed()) {
            throw new UserNotConfirmedException("User account is not confirmed");
        }

        boolean passwordExpired = accountProtectionService.isAtRisk(user);
        String securityStatus = passwordExpired ? "AT_RISK" : "SECURE";

        if (user.isTwoFactorEnabled()) {
            boolean hasTotp = command.totpCode() != null && !command.totpCode().isBlank();
            boolean hasBackup = command.backupCode() != null && !command.backupCode().isBlank();
            if (!hasTotp && !hasBackup) {
                return Optional.of(new AuthenticatedUser(
                        user, null, workspaceId, true, passwordExpired, passwordExpired, securityStatus));
            }
            boolean totpValid = hasTotp && twoFactorService.verifyTotp(user.getTwoFactorSecret(), command.totpCode());
            boolean backupValid = !totpValid && hasBackup
                    && twoFactorService.verifyAndConsumeBackupCode(user, command.backupCode());
            if (!totpValid && !backupValid) {
                accountProtectionService.recordFailedAttempt(user, ipAddress);
                securityEventPublisher.publish2faFailed(user.getEmail(), workspaceId, ipAddress, "Invalid 2FA code");
                throw new InvalidTwoFactorCodeException("Invalid two-factor authentication code");
            }
            if (backupValid) {
                userRepository.save(user);
            }
        }

        accountProtectionService.resetFailedAttempts(user, ipAddress);
        var session = sessionAuditService.createSession(
                user, workspaceId, ipAddress, command.userAgent(), command.location());
        var roles = user.getRoles().stream().map(Role::getStringName).toList();
        var token = tokenService.generateToken(user.getEmail(), roles, workspaceId, session.getSessionId());
        securityEventPublisher.publishLoginSucceeded(
                user.getEmail(), workspaceId, ipAddress, "Session created: " + session.getSessionId());

        return Optional.of(new AuthenticatedUser(
                user, token, workspaceId, false, passwordExpired, passwordExpired, securityStatus));
    }

    private Long resolveWorkspaceId(String email) {
        try {
            Object result = entityManager.createNativeQuery(
                    "SELECT workspace_id FROM safelab_automation.user_profiles WHERE email = ?1")
                    .setParameter(1, email)
                    .getSingleResult();
            return result == null ? null : Long.parseLong(result.toString());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Optional<User> handle(SignUpCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new UserAlreadyExistsException("Attempt to register with an already existing username or email in database");
        }

        var roles = new ArrayList<Role>();
        if (command.roles() == null || command.roles().isEmpty()) {
            roleRepository.findByName(Roles.ROLE_USER).ifPresent(roles::add);
        } else {
            command.roles().forEach(roleName -> roleRepository.findByName(roleName).ifPresent(roles::add));
        }

        var user = new User(
                command.email(),
                hashingService.encode(command.password()),
                command.fullName(),
                roles
        );

        if ("admin@safelab.com".equals(command.email())) {
            user.setConfirmed(true);
            userRepository.save(user);
        } else {
            String code = generateConfirmationCode();
            user.setConfirmationCode(code);
            user.setConfirmed(false);
            userRepository.save(user);
            emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), code);
        }

        eventPublisher.publishEvent(new UserRegisteredEvent(user.getEmail(), user.getFullName()));
        return Optional.of(user);
    }

    @Override
    public boolean handle(ConfirmAccountCommand command) {
        var userOptional = userRepository.findByEmail(command.email());
        if (userOptional.isEmpty()) {
            return false;
        }
        var user = userOptional.get();
        if (user.isConfirmed()) {
            return true;
        }
        if (user.getConfirmationCode() != null && user.getConfirmationCode().equals(command.code())) {
            user.setConfirmed(true);
            user.setConfirmationCode(null);
            userRepository.save(user);
            return true;
        }
        return false;
    }

    @Override
    public boolean handle(ResendConfirmationCodeCommand command) {
        var userOptional = userRepository.findByEmail(command.email());
        if (userOptional.isEmpty()) {
            return false;
        }
        var user = userOptional.get();
        if (user.isConfirmed()) {
            return false;
        }
        String code = generateConfirmationCode();
        user.setConfirmationCode(code);
        userRepository.save(user);
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), code);
        return true;
    }

    private String generateConfirmationCode() {
        int code = (int) (Math.random() * 900000) + 100000;
        return String.valueOf(code);
    }

    @Override
    public boolean handle(UpdatePasswordCommand command) {
        var userOptional = userRepository.findByEmail(command.email());
        if (userOptional.isEmpty()) {
            return false;
        }
        var user = userOptional.get();
        if (!hashingService.matches(command.currentPassword(), user.getPassword())) {
            return false;
        }
        user.setPassword(hashingService.encode(command.newPassword()));
        user.setPasswordUpdatedAt(Instant.now());
        userRepository.save(user);
        return true;
    }

    private Long getMaxSeededId(String settingKey) {
        try {
            Object val = entityManager.createNativeQuery(
                    "SELECT value FROM safelab_automation.general_settings WHERE setting_key = ?1"
            ).setParameter(1, settingKey).getSingleResult();
            if (val != null) {
                return Long.parseLong(val.toString());
            }
        } catch (Exception e) {
            // Setting doesn't exist yet, fallback to 0
        }
        return 0L;
    }

    @Override
    @Transactional
    public void handleCleanTestUsers() {
        String adminEmail = "admin@safelab.com";

        Long maxSeededUserId = getMaxSeededId("max_seeded_user_id");
        if (maxSeededUserId == 0L) {
            try {
                Object adminId = entityManager.createNativeQuery("SELECT id FROM safelab_iam.users WHERE email = ?1").setParameter(1, adminEmail).getSingleResult();
                if (adminId != null) maxSeededUserId = Long.parseLong(adminId.toString());
            } catch (Exception e) {}
        }

        Long maxSeededProfileId = getMaxSeededId("max_seeded_profile_id");
        if (maxSeededProfileId == 0L) {
            try {
                Object pId = entityManager.createNativeQuery("SELECT id FROM safelab_automation.user_profiles WHERE email = ?1").setParameter(1, adminEmail).getSingleResult();
                if (pId != null) maxSeededProfileId = Long.parseLong(pId.toString());
            } catch (Exception e) {}
        }

        Long maxSeededLabId = getMaxSeededId("max_seeded_laboratory_id");
        if (maxSeededLabId == 0L) {
            try {
                Object maxLabId = entityManager.createNativeQuery("SELECT MAX(id) FROM safelab_labs.laboratories WHERE lab_code IN ('LAB-BSL4-01','LAB-CHEM-02','LAB-CRYO-03','LAB-ANAL-04','LAB-MOLB-05','LAB-CLEAN-06','LAB-RAD-07','LAB-PHARM-08','LAB-GENE-09','LAB-MICRO-10')").getSingleResult();
                if (maxLabId != null) maxSeededLabId = Long.parseLong(maxLabId.toString());
            } catch (Exception e) {}
        }

        Long maxSeededReadingId = getMaxSeededId("max_seeded_reading_id");
        Long maxSeededAlertId = getMaxSeededId("max_seeded_alert_id");
        Long maxSeededAlertMetricId = getMaxSeededId("max_seeded_alert_metric_id");
        Long maxSeededHistoryId = getMaxSeededId("max_seeded_history_id");
        Long maxSeededSensorId = getMaxSeededId("max_seeded_sensor_id");
        Long maxSeededCalibrationId = getMaxSeededId("max_seeded_calibration_record_id");
        Long maxSeededWorkspaceAccessId = getMaxSeededId("max_seeded_workspace_access_id");
        Long maxSeededLabAccessId = getMaxSeededId("max_seeded_lab_access_id");
        Long maxSeededNotificationPrefId = getMaxSeededId("max_seeded_notification_pref_id");
        Long maxSeededStorageUnitId = getMaxSeededId("max_seeded_storage_unit_id");
        Long maxSeededMitigationSystemId = getMaxSeededId("max_seeded_mitigation_system_id");
        Long maxSeededMetricSubId = getMaxSeededId("max_seeded_metric_sub_id");
        Long maxSeededMetricId = getMaxSeededId("max_seeded_metric_id");
        Long maxSeededActivityId = getMaxSeededId("max_seeded_activity_id");
        Long maxSeededScheduleId = getMaxSeededId("max_seeded_schedule_id");
        Long maxSeededEquipmentThresholdId = getMaxSeededId("max_seeded_equipment_threshold_id");
        Long maxSeededWorkspaceId = getMaxSeededId("max_seeded_workspace_id");

        entityManager.createNativeQuery("DELETE FROM safelab_automation.calibration_records WHERE id > ?1").setParameter(1, maxSeededCalibrationId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_telemetry.sensor_readings WHERE id > ?1").setParameter(1, maxSeededReadingId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_alerts.alert_metrics WHERE id > ?1").setParameter(1, maxSeededAlertMetricId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_alerts.alerts WHERE id > ?1").setParameter(1, maxSeededAlertId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_labs.mitigation_systems WHERE id > ?1").setParameter(1, maxSeededMitigationSystemId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_labs.storage_units WHERE id > ?1").setParameter(1, maxSeededStorageUnitId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_labs.lab_metric_subscriptions WHERE id > ?1").setParameter(1, maxSeededMetricSubId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_labs.lab_metrics WHERE id > ?1").setParameter(1, maxSeededMetricId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_labs.lab_activities WHERE id > ?1").setParameter(1, maxSeededActivityId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_labs.lab_schedules WHERE id > ?1").setParameter(1, maxSeededScheduleId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_history.history_records WHERE id > ?1").setParameter(1, maxSeededHistoryId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_automation.sensor_configurations WHERE id > ?1").setParameter(1, maxSeededSensorId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_automation.equipment_thresholds WHERE id > ?1").setParameter(1, maxSeededEquipmentThresholdId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_automation.lab_user_accesses WHERE id > ?1").setParameter(1, maxSeededLabAccessId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_automation.user_workspace_accesses WHERE id > ?1").setParameter(1, maxSeededWorkspaceAccessId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_automation.notification_preferences WHERE id > ?1").setParameter(1, maxSeededNotificationPrefId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_automation.user_profiles WHERE id > ?1").setParameter(1, maxSeededProfileId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_iam.user_iam_roles WHERE user_id > ?1").setParameter(1, maxSeededUserId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_iam.users WHERE id > ?1").setParameter(1, maxSeededUserId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_labs.laboratories WHERE id > ?1").setParameter(1, maxSeededLabId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM safelab_labs.workspaces WHERE id > ?1").setParameter(1, maxSeededWorkspaceId).executeUpdate();

        logger.info("Successfully performed pristine database reset. Seeded data is preserved.");
    }
}
