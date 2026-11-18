package dev.janitor.busbooking.impl.jpa.repos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.bootstrap.HibernateRuntime;
import dev.janitor.busbooking.impl.jpa.entities.LocationEntity;
import dev.janitor.busbooking.model.Location;
import dev.janitor.busbooking.repository.ILocationRepository;
import dev.janitor.busbooking.utils.Sputnik;

public class JPALocationRepository implements ILocationRepository {
	private final HibernateRuntime runtime;
	
	public JPALocationRepository(HibernateRuntime runtime) {
		this.runtime = runtime;
	}
	
	@Override
	public Optional<Location> get(long locationId) {
		try (var man = runtime.openEntityManager()) {
			LocationEntity entity = man.find(LocationEntity.class, locationId);
			return entity == null ? Optional.empty() : Optional.of(entity.toModel());
		}
	}
	
	@Override
	public List<Location> search(String searchText) {
		boolean hasSearchText = searchText != null && !searchText.isBlank();
		String jpql = "SELECT l FROM LocationEntity l";
		if (hasSearchText) jpql += """
			 WHERE lower(function('unaccent', l.name)) LIKE :pattern ESCAPE '!'
			 OR lower(function('unaccent', l.province)) LIKE :pattern ESCAPE '!'
			 OR lower(function('unaccent', l.address)) LIKE :pattern ESCAPE '!'
		""";
		jpql += " ORDER BY l.name";
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery(jpql, LocationEntity.class);
			if (hasSearchText) {
				query.setParameter("pattern", Sputnik.containing(searchText));
			}
			List<Location> locations = new ArrayList<>();
			for (LocationEntity entity : query.getResultList()) {
				locations.add(entity.toModel());
			}
			return locations;
		}
	}
	
	@Override
	public Location create(Location location) {
		return runtime.transact(man -> {
			var entity = LocationEntity.fromModel(location);
			man.persist(entity);
			return entity.toModel();
		});
	}
	
	@Override
	public Location update(Location location) {
		return runtime.transact(man -> {
			var existing = man.find(LocationEntity.class, location.id());
			if (existing == null) {
				return null;
			}
			existing.setName(location.name());
			existing.setProvince(location.province());
			existing.setAddress(location.address());
			return existing.toModel();
		});
	}
}
