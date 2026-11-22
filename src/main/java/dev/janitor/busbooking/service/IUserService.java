package dev.janitor.busbooking.service;

import dev.janitor.busbooking.consts.UserRole;
import dev.janitor.busbooking.model.User;

public interface IUserService {
    User getProfile(long userId);
    // Name and phone only. the email is the login and the verified identity, it does not change
    User updateProfile(long userId, String fullName, String phone);
    // SUPER_ADMIN only, USER <-> ADMIN only. nobody touches a SUPER_ADMIN, including themselves
    User changeRole(long actorUserId, long targetUserId, UserRole role);
}
