package br.com.officyna.billing.infrastructure.persistence.jpa.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class AbstractJpaRepositoryGateway<E, J, R extends JpaRepository<J, UUID>> {

	private final Logger log = LoggerFactory.getLogger(getClass());

	protected final R jpaRepository;
	protected final Function<E, J> toJpaEntity;
	protected final Function<J, E> toEntity;

	protected AbstractJpaRepositoryGateway(R jpaRepository, Function<E, J> toJpaEntity, Function<J, E> toEntity) {
		this.jpaRepository = jpaRepository;
		this.toJpaEntity = toJpaEntity;
		this.toEntity = toEntity;
	}

	public E save(E entity) {
		return execute("save", () -> {
			J jpaEntity = toJpaEntity.apply(entity);
			J saved = jpaRepository.save(jpaEntity);
			return toEntity.apply(saved);
		});
	}

	public Optional<E> findById(UUID id) {
		return execute("findById", () -> jpaRepository.findById(id).map(toEntity));
	}

	protected <T> T execute(String operation, Supplier<T> action) {
		try {
			return action.get();
		} catch (Exception e) {
			log.error("Operação de banco falhou operation={} repository={}", operation, getClass().getSimpleName(), e);
			throw e;
		}
	}

}
