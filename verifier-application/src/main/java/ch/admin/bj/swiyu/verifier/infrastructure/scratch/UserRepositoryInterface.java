package ch.admin.bj.swiyu.verifier.infrastructure.scratch;

import java.util.List;

// Falscher '*Interface'-Suffix, liegt im Web-/Infrastructure-Package statt in '..domain..'.
// Keine JavaDoc.
public interface UserRepositoryInterface {

    List<Long> findAllIds();

    String findNameById(Long id);

    void executeRaw(String sql);
}

