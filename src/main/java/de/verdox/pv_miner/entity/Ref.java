package de.verdox.pv_miner.entity;

import lombok.Getter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Objects;
import java.util.function.Consumer;


public class Ref<KEY, ENTITY, REPOSITORY extends JpaRepository<ENTITY, KEY>> {
    @Getter
    private final KEY id;
    private final REPOSITORY repository;

    public Ref(KEY id, REPOSITORY repository) {
        this.id = id;
        this.repository = repository;
    }

    public ENTITY read() {
        return repository.getReferenceById(id);
    }

    public void write(Consumer<ENTITY> writeAction) {
        ENTITY entity = read();
        writeAction.accept(entity);
        repository.save(entity);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Ref<?, ?, ?> ref = (Ref<?, ?, ?>) o;
        return Objects.equals(id, ref.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
