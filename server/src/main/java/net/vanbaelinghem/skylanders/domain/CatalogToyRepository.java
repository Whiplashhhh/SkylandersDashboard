package net.vanbaelinghem.skylanders.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogToyRepository extends JpaRepository<CatalogToy, ToyKey> {}
