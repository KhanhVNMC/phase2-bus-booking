package dev.janitor.busbooking.impl.service;

import dev.janitor.busbooking.bootstrap.ApplicationConfig;
import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.consts.ServiceFailure;
import dev.janitor.busbooking.consts.UserRole;
import dev.janitor.busbooking.model.PendingEmailVerification;
import dev.janitor.busbooking.model.User;
import dev.janitor.busbooking.repository.IAccessTokens;
import dev.janitor.busbooking.repository.IPasswordHasher;
import dev.janitor.busbooking.repository.IPendingVerificationStore;
import dev.janitor.busbooking.repository.IUserRepository;
import dev.janitor.busbooking.repository.IVerificationCodes;
import dev.janitor.busbooking.repository.IVerificationMailer;
import dev.janitor.busbooking.service.IAuthService;
import dev.janitor.busbooking.service.ServiceException;
import dev.janitor.busbooking.utils.Snowflake;
import dev.janitor.busbooking.utils.Sputnik;

public class AuthService implements IAuthService {
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_NAME_LENGTH = 100;
    private static final String BAD_CREDENTIALS = "Invalid email or password";
    private static final String BAD_CODE = "Invalid or expired verification code";

    private final IUserRepository userRepo;
    private final IPendingVerificationStore pendingVerifications;
    private final IVerificationCodes verificationCodes;
    private final IVerificationMailer mailer;
    private final IPasswordHasher passwordHasher;
    private final IAccessTokens accessTokens;
    private final long codeTtlMs;
    private final int maxCodeAttempts;
    private final long resendCooldownMs;
    // chống dò password bằng timing attack
    private final String dummyPasswordHash;

    public AuthService(
        IUserRepository userRepo, IPendingVerificationStore pendingVerifications, IVerificationCodes verificationCodes,
        IVerificationMailer mailer, IPasswordHasher passwordHasher, IAccessTokens accessTokens, ApplicationConfig config
    ) {
        this.userRepo = userRepo;
        this.pendingVerifications = pendingVerifications;
        this.verificationCodes = verificationCodes;
        this.mailer = mailer;
        this.passwordHasher = passwordHasher;
        this.accessTokens = accessTokens;
        this.codeTtlMs = config.getVerificationCodeTtlSeconds() * 1000L;
        this.maxCodeAttempts = config.getVerificationMaxAttempts();
        this.resendCooldownMs = config.getVerificationResendCooldownSeconds() * 1000L;
        this.dummyPasswordHash = passwordHasher.hash("dummy-password-comparison-only");
    }

    @Override
    public User register(String fullName, String email, String password, String phone) {
        String normalizedEmail = Sputnik.requireEmail(email, "email");
        String name = Sputnik.requireText(fullName, "fullName", MAX_NAME_LENGTH);
        String normalizedPhone = Sputnik.requirePhone(phone, "phone");
        requirePassword(password);
        // The unique email column is the real guard, this check is for the friendly error code.code
        if (userRepo.getByEmail(normalizedEmail).isPresent()) {
            throw ServiceException.conflict("An account with this email already exists");
        }
        long now = System.currentTimeMillis();
        User user = new User(
            Snowflake.get().nextId(),
            name,
            normalizedEmail,
            passwordHasher.hash(password),
            normalizedPhone,
            UserRole.USER,
            ActiveStatus.ACTIVE,
            false,
            now,
            now
        );
        User created = userRepo.create(user);
        issueVerificationCode(created);
        return created;
    }

    @Override
    public void verifyEmail(String email, String code) {
        String normalizedEmail = Sputnik.requireEmail(email, "email");
        String trimmedCode = Sputnik.trimToNull(code);
        if (trimmedCode == null || !trimmedCode.matches("[0-9]{6}")) {
            throw ServiceException.invalid(BAD_CODE);
        }
        var pending = pendingVerifications.get(normalizedEmail);
        if (pending.isEmpty()) {
            throw ServiceException.invalid(BAD_CODE);
        }
        String expectedDigest = pending.get().codeDigest();
        if (!expectedDigest.equals(verificationCodes.digest(normalizedEmail, trimmedCode))) {
            long failedAttempts = pendingVerifications.recordFailedAttempt(normalizedEmail, codeTtlMs);
            if (failedAttempts >= maxCodeAttempts) {
                pendingVerifications.delete(normalizedEmail);
                throw new ServiceException(ServiceFailure.TOO_MANY_REQUESTS, "Too many wrong codes, request a new one");
            }
            throw ServiceException.invalid(BAD_CODE);
        }
        userRepo.markEmailVerified(pending.get().userId(), System.currentTimeMillis());
        pendingVerifications.delete(normalizedEmail);
    }

    @Override
    public void resendVerification(String email) {
        String normalizedEmail = Sputnik.requireEmail(email, "email");
        if (!pendingVerifications.tryStartResendCooldown(normalizedEmail, resendCooldownMs)) {
            throw new ServiceException(ServiceFailure.TOO_MANY_REQUESTS, "Wait a minute before asking for another code");
        }
        var user = userRepo.getByEmail(normalizedEmail);
        if (user.isEmpty() || user.get().emailVerified() || user.get().status() != ActiveStatus.ACTIVE) {
            return;
        }
        issueVerificationCode(user.get());
    }

    @Override
    public LoginResult login(String email, String password) {
        String normalizedEmail = Sputnik.trimToNull(email);
        if (normalizedEmail == null || password == null) {
            throw new ServiceException(ServiceFailure.UNAUTHENTICATED, BAD_CREDENTIALS);
        }
        var user = userRepo.getByEmail(Sputnik.toLower(normalizedEmail));
        String storedHash = user.isPresent() ? user.get().passwordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordHasher.matches(password, storedHash);
        if (user.isEmpty() || !passwordMatches) {
            throw new ServiceException(ServiceFailure.UNAUTHENTICATED, BAD_CREDENTIALS);
        }
        User found = user.get();
        if (found.status() != ActiveStatus.ACTIVE) {
            throw new ServiceException(ServiceFailure.FORBIDDEN, "This account is disabled");
        }
        if (!found.emailVerified()) {
            throw new ServiceException(ServiceFailure.FORBIDDEN, "Verify your email before logging in");
        }
        String token = accessTokens.issue(found.id(), found.role());
        return new LoginResult(token, accessTokens.ttlSeconds(), found);
    }

    @Override
    public Principal authenticate(String bearerToken) {
        var claims = accessTokens.verify(Sputnik.trimToNull(bearerToken));
        if (claims.isEmpty()) {
            throw new ServiceException(ServiceFailure.UNAUTHENTICATED, "Missing, invalid or expired token");
        }
        return new Principal(claims.get().userId(), claims.get().role());
    }

    @Override
    public boolean bootstrapSuperAdmin(String email, String password, String fullName, String phone) {
        if (Sputnik.trimToNull(email) == null) {
            return false;
        }
        if (userRepo.existsByRole(UserRole.SUPER_ADMIN)) {
            return false;
        }
        String normalizedEmail = Sputnik.requireEmail(email, "BOOTSTRAP_SUPER_ADMIN_EMAIL");
        if (userRepo.getByEmail(normalizedEmail).isPresent()) {
            throw new IllegalStateException("BOOTSTRAP_SUPER_ADMIN_EMAIL already belongs to an account, refusing to promote it");
        }
        requirePassword(password);
        long now = System.currentTimeMillis();
        User superAdmin = new User(
            Snowflake.get().nextId(),
            Sputnik.requireText(fullName, "BOOTSTRAP_SUPER_ADMIN_FULL_NAME", MAX_NAME_LENGTH),
            normalizedEmail,
            passwordHasher.hash(password),
            Sputnik.requirePhone(phone, "BOOTSTRAP_SUPER_ADMIN_PHONE"),
            UserRole.SUPER_ADMIN,
            ActiveStatus.ACTIVE,
            true, // The operator typed it into the server env, that is proof enough.
            now,
            now
        );
        userRepo.create(superAdmin);
        return true;
    }

    private void issueVerificationCode(User user) {
		String code = verificationCodes.generate();
		String digest = verificationCodes.digest(user.email(), code);
		pendingVerifications.put(user.email(), new PendingEmailVerification(user.id(), digest), codeTtlMs);
	    try {
	    	mailer.sendVerificationCode(user.email(), code, (int) (codeTtlMs / 60_000));
	    } catch (RuntimeException e) {
	    	// Tthe account and the code are saved already, a mail outage must not fail the
	    	// signup; resend fixes it.
	    	System.err.println("[AUTH] verification mail to " + user.email() + " failed: " + e.getMessage());
	    }
	}

	private static void requirePassword(String password) {
		if (password == null || password.isBlank()) {
			throw ServiceException.invalid("password is required");
		}
		int length = password.codePointCount(0, password.length());
		if (length < MIN_PASSWORD_LENGTH || length > MAX_PASSWORD_LENGTH) {
			throw ServiceException.invalid("password must be " + MIN_PASSWORD_LENGTH + " to " + MAX_PASSWORD_LENGTH + " characters");
		}
	}
}
