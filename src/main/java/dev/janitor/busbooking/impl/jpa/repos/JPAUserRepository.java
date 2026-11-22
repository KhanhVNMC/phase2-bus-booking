package dev.janitor.busbooking.impl.jpa.repos;

import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.bootstrap.HibernateRuntime;
import dev.janitor.busbooking.impl.jpa.entities.UserEntity;
import dev.janitor.busbooking.consts.UserRole;
import dev.janitor.busbooking.model.User;
import dev.janitor.busbooking.repository.IUserRepository;

public class JPAUserRepository implements IUserRepository {
    private final HibernateRuntime runtime;

    public JPAUserRepository(HibernateRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public Optional<User> get(long userId) {
        try (var mgr = runtime.openEntityManager()) {
            UserEntity entity = mgr.find(UserEntity.class, userId);
            return entity == null ? Optional.empty() : Optional.of(entity.toModel());
        }
    }

    @Override
    public Optional<User> getByEmail(String email) {
        try (var mgr = runtime.openEntityManager()) {
            var query = mgr.createQuery("SELECT u FROM UserEntity u WHERE u.email = :email", UserEntity.class);
            query.setParameter("email", email);
            List<UserEntity> matchingUsers = query.getResultList();
            return matchingUsers.isEmpty() ? Optional.empty() : Optional.of(matchingUsers.getFirst().toModel());
        }
    }

    @Override
    public boolean existsByRole(UserRole role) {
        try (var mgr = runtime.openEntityManager()) {
            var query = mgr.createQuery("SELECT count(u) FROM UserEntity u WHERE u.role = :role", Long.class);
            query.setParameter("role", role);
            long matchingUsers = query.getSingleResult();
            return matchingUsers > 0;
        }
    }

    @Override
    public User create(User user) {
        return runtime.transact(mgr -> {
            var entity = UserEntity.fromModel(user);
            mgr.persist(entity);
            return entity.toModel();
        });
    }

    @Override
    public User update(User user) {
        return runtime.transact(mgr -> {
            var existing = mgr.find(UserEntity.class, user.id());
            if (existing == null) {
                return null;
            }
            existing.setFullName(user.fullName());
            existing.setPhone(user.phone());
            existing.setPasswordHash(user.passwordHash());
            existing.setRole(user.role());
            existing.setStatus(user.status());
            existing.setUpdatedAt(user.updatedAt());
            return existing.toModel();
        });
    }

    @Override
    public boolean markEmailVerified(long userId, long verifiedAt) {
        return runtime.transact(mgr -> {
            var update = mgr.createQuery("""
                UPDATE UserEntity u SET u.emailVerified = true, u.updatedAt = :verified_at
                WHERE u.id = :user_id AND u.emailVerified = false
            """);
            update.setParameter("user_id", userId);
            update.setParameter("verified_at", verifiedAt);
            int updatedRows = update.executeUpdate();
            return updatedRows == 1;
        });
    }
}
