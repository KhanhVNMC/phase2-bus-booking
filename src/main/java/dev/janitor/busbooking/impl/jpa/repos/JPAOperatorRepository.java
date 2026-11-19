package dev.janitor.busbooking.impl.jpa.repos;

import dev.janitor.busbooking.consts.VehicleStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.bootstrap.HibernateRuntime;
import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.impl.jpa.entities.OperatorEntity;
import dev.janitor.busbooking.model.Operator;
import dev.janitor.busbooking.repository.IOperatorRepository;
import dev.janitor.busbooking.utils.Sputnik;

public class JPAOperatorRepository implements IOperatorRepository {
	private final HibernateRuntime runtime;
	
	public JPAOperatorRepository(HibernateRuntime runtime) {
		this.runtime = runtime;
	}
	
	@Override
	public Optional<Operator> get(long operatorId) {
		try (var man = runtime.openEntityManager()) {
			OperatorEntity entity = man.find(OperatorEntity.class, operatorId);
			return entity == null ? Optional.empty() : Optional.of(entity.toModel());
		}
	}
	
	@Override
	public List<Operator> search(String searchText, ActiveStatus status) {
		boolean hasSearchText = searchText != null && !searchText.isBlank();
		String jpql = "SELECT o FROM OperatorEntity o WHERE 1 = 1";
		if (hasSearchText) jpql += " AND lower(function('unaccent', o.name)) LIKE :pattern ESCAPE '!'";
		if (status != null) jpql += " AND o.status = :status";
		jpql += " ORDER BY o.name";
		
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery(jpql, OperatorEntity.class);
			if (hasSearchText) query.setParameter("pattern", Sputnik.containing(searchText));
			if (status != null) query.setParameter("status", status);
			List<Operator> operators = new ArrayList<>();
			for (OperatorEntity entity : query.getResultList()) {
				operators.add(entity.toModel());
			}
			return operators;
		}
	}
	
	@Override
	public Operator create(Operator operator) {
		return runtime.transact(man -> {
			var entity = OperatorEntity.fromModel(operator);
			man.persist(entity);
			return entity.toModel();
		});
	}
	
	@Override
	public Operator update(Operator operator) {
		return runtime.transact(man -> {
			var existing = man.find(OperatorEntity.class, operator.id());
			if (existing == null) {
				return null;
			}
			existing.setName(operator.name());
			existing.setAddress(operator.address());
			existing.setPhone(operator.phone());
			existing.setEmail(operator.email());
			existing.setStatus(operator.status());
			return existing.toModel();
		});
	}
	
	@Override
	public boolean ownsInServiceVehicles(long operatorId) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("""
				SELECT count(v) FROM VehicleEntity v
				WHERE v.operatorId = :operator_id AND v.status IN (:active, :maintenance)
			""", Long.class);
			query.setParameter("operator_id", operatorId);
			query.setParameter("active", VehicleStatus.ACTIVE);
			query.setParameter("maintenance", VehicleStatus.MAINTENANCE);
			long inServiceVehicles = query.getSingleResult();
			return inServiceVehicles > 0;
		}
	}
	
	@Override
	public boolean existsByName(String name, long excludedOperatorId) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("""
				SELECT count(o) FROM OperatorEntity o
				WHERE lower(o.name) = lower(:name) AND o.id <> :excluded_operator_id
			""", Long.class);
			query.setParameter("name", name);
			query.setParameter("excluded_operator_id", excludedOperatorId);
			long matchingOperators = query.getSingleResult();
			return matchingOperators > 0;
		}
	}
	
	@Override
	public boolean existsByEmail(String email, long excludedOperatorId) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("""
				SELECT count(o) FROM OperatorEntity o
				WHERE o.email = :email AND o.id <> :excluded_operator_id
			""", Long.class);
			query.setParameter("email", email);
			query.setParameter("excluded_operator_id", excludedOperatorId);
			long matchingOperators = query.getSingleResult();
			return matchingOperators > 0;
		}
	}
}
