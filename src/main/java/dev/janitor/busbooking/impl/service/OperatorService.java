package dev.janitor.busbooking.impl.service;

import java.util.List;

import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.model.Operator;
import dev.janitor.busbooking.repository.IOperatorRepository;
import dev.janitor.busbooking.service.IOperatorService;
import dev.janitor.busbooking.service.ServiceException;
import dev.janitor.busbooking.utils.Snowflake;
import dev.janitor.busbooking.utils.Sputnik;

// an operator (nha xe) is freely editable; INACTIVE = retired, no new trips, hidden from the public
public class OperatorService implements IOperatorService {
	private final IOperatorRepository operatorRepo;
	
	public OperatorService(IOperatorRepository operatorRepo) {
		this.operatorRepo = operatorRepo;
	}
	
	@Override
	public Operator get(long operatorId) {
		var operator = operatorRepo.get(operatorId);
		if (operator.isEmpty()) {
			throw ServiceException.notFound("Operator not found");
		}
		return operator.get();
	}
	
	@Override
	public List<Operator> search(String searchText, ActiveStatus status) {
		return operatorRepo.search(Sputnik.trimToNull(searchText), status);
	}
	
	@Override
	public Operator create(String name, String address, String phone, String email) {
		Operator operator = new Operator(
			Snowflake.get().nextId(),
			Sputnik.requireText(name, "name", 150),
			Sputnik.requireText(address, "address", 255),
			Sputnik.requirePhone(phone, "phone"),
			Sputnik.requireEmail(email, "email"),
			ActiveStatus.ACTIVE,
			System.currentTimeMillis()
		);
		requireUnique(operator);
		return operatorRepo.create(operator);
	}
	
	@Override
	public Operator update(long operatorId, String name, String address, String phone, String email, ActiveStatus status) {
		Operator current = get(operatorId);
		if (status == null) {
			throw ServiceException.invalid("status is required");
		}
		Operator operator = new Operator(
			operatorId,
			Sputnik.requireText(name, "name", 150),
			Sputnik.requireText(address, "address", 255),
			Sputnik.requirePhone(phone, "phone"),
			Sputnik.requireEmail(email, "email"),
			status,
			current.createdAt()
		);
		requireUnique(operator);
		boolean retiring = current.status() == ActiveStatus.ACTIVE && status == ActiveStatus.INACTIVE;
		// basically if you flip ACTIVE -> INACTIVE operators (nha xe) that still owns active vehicle
		// -> refuse, because an inactive operator cannot operate in service vehicles ffs
		if (retiring && operatorRepo.ownsInServiceVehicles(operatorId)) {
			throw ServiceException.conflict("Operator still owns ACTIVE or MAINTENANCE vehicles");
		}
		return operatorRepo.update(operator);
	}
	
	private void requireUnique(Operator operator) {
		if (operatorRepo.existsByName(operator.name(), operator.id())) {
			throw ServiceException.conflict("An operator with this name already exists");
		}
		if (operatorRepo.existsByEmail(operator.email(), operator.id())) {
			throw ServiceException.conflict("An operator with this email already exists");
		}
	}
}
