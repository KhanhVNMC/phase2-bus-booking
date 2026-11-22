package dev.janitor.busbooking.impl.service;

import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.consts.ServiceFailure;
import dev.janitor.busbooking.consts.UserRole;
import dev.janitor.busbooking.model.User;
import dev.janitor.busbooking.repository.IUserRepository;
import dev.janitor.busbooking.service.IUserService;
import dev.janitor.busbooking.service.ServiceException;
import dev.janitor.busbooking.utils.Sputnik;

public class UserService implements IUserService {
	private static final int MAX_NAME_LENGTH = 100;

	private final IUserRepository userRepo;

	public UserService(IUserRepository userRepo) {
		this.userRepo = userRepo;
	}

	@Override
	public User getProfile(long userId) {
		var user = userRepo.get(userId);
		// A valid token for a user that is gone. The token is the stale part, so 401.
		if (user.isEmpty()) {
			throw new ServiceException(ServiceFailure.UNAUTHENTICATED, "This account no longer exists");
		}
		return user.get();
	}

	@Override
	public User updateProfile(long userId, String fullName, String phone) {
		User current = getProfile(userId);
		User updated = new User(
			current.id(),
			Sputnik.requireText(fullName, "fullName", MAX_NAME_LENGTH),
			current.email(),
			current.passwordHash(),
			Sputnik.requirePhone(phone, "phone"),
			current.role(),
			current.status(),
			current.emailVerified(),
			current.createdAt(),
			System.currentTimeMillis()
		);
		return userRepo.update(updated);
	}

	@Override
	public User changeRole(long actorUserId, long targetUserId, UserRole role) {
		// The token says who is asking, but for handing out admin rights the db has the last word on the actor.
		User actor = getProfile(actorUserId);
		if (actor.role() != UserRole.SUPER_ADMIN || actor.status() != ActiveStatus.ACTIVE) {
			throw new ServiceException(ServiceFailure.FORBIDDEN, "Only a SUPER_ADMIN can change roles");
		}
		if (role != UserRole.USER && role != UserRole.ADMIN) {
			throw ServiceException.invalid("role must be USER or ADMIN");
		}
		var target = userRepo.get(targetUserId);
		if (target.isEmpty()) {
			throw ServiceException.notFound("User not found");
		}
		User current = target.get();
		if (current.role() == UserRole.SUPER_ADMIN) {
			throw new ServiceException(ServiceFailure.FORBIDDEN, "A SUPER_ADMIN's role cannot be changed");
		}
		// An admin must be someone who can actually log in
		if (role == UserRole.ADMIN && (!current.emailVerified() || current.status() != ActiveStatus.ACTIVE)) {
			//System.out.println(role + " " + current.email() + " " + current.id());
			throw ServiceException.conflict("Only active, verified accounts can become ADMIN");
		}
		User updated = new User(
			current.id(), current.fullName(), current.email(), current.passwordHash(), current.phone(),
			role, current.status(), current.emailVerified(), current.createdAt(), System.currentTimeMillis()
		);
		return userRepo.update(updated);
	}
}
