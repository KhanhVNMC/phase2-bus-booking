package dev.janitor.busbooking.repository;

import java.util.Optional;

import dev.janitor.busbooking.consts.UserRole;
import dev.janitor.busbooking.model.User;

public interface IUserRepository {
	Optional<User> get(long userId);
	Optional<User> getByEmail(String email);
	boolean existsByRole(UserRole role);
	User create(User user);
	User update(User user);
	boolean markEmailVerified(long userId, long verifiedAt);
}
