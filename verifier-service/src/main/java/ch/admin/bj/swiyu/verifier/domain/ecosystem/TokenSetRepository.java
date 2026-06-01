package ch.admin.bj.swiyu.verifier.domain.ecosystem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenSetRepository extends JpaRepository<TokenSet, EcosystemApiType> {

}
