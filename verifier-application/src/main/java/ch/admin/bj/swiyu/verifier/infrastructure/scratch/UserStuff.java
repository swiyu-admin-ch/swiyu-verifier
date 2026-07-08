package ch.admin.bj.swiyu.verifier.infrastructure.scratch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

// Dieser Controller macht alles auf einmal (Persistenz, Business-Logik, HTTP).
// Kein @Tag, falscher Suffix, falsches Package.
@RestController
public class UserStuff {

    private static final Logger LOG = LoggerFactory.getLogger(UserStuff.class);

    // Feld-Injection statt Constructor-Injection, nicht final
    @Autowired
    private UserRepositoryInterface repo;

    // Veränderlicher Zustand in einem Singleton -> nicht thread-safe
    private int requestCounter = 0;
    private final List<String> cache = new ArrayList<>();

    @GetMapping("/users")
    public List<String> getUsers(@RequestParam String password) {
        // Loggt ein Secret (Passwort) -> PII / Credential leak
        LOG.info("Fetching users with password=" + password);

        requestCounter++;

        // N+1 Query Problem: pro id eine einzelne DB-Abfrage in der Schleife
        List<String> result = new ArrayList<>();
        List<Long> ids = repo.findAllIds();
        for (Long id : ids) {
            String name = repo.findNameById(id);
            result.add(name);
            cache.add(name);
        }
        return result;
    }

    @PostMapping("/users")
    public String createUser(@RequestParam String name, @RequestParam String email) {
        // Direkter Repository-Zugriff aus dem Controller + Business-Logik im Web-Layer
        if (name == null || name.length() == 0) {
            // verschluckt den Fehler still statt eine Domain-Exception zu werfen
            return "error";
        }

        // Ad-hoc Mapping und Persistenz mitten im Controller
        String sql = "INSERT INTO usr (naem, email) VALUES ('" + name + "', '" + email + "')";
        repo.executeRaw(sql);

        // Typo im Rückgabe-/Statuswert
        return "successfull";
    }

    // Keine JavaDoc, deutscher Kommentar, macht mehrere Dinge gleichzeitig
    public String verarbeiteAlles(String rawInput) {
        String trimmed = rawInput.trim();
        String upper = trimmed.toUpperCase();
        LOG.info("verarbeite: " + upper);
        repo.executeRaw("UPDATE usr SET flag = true");
        cache.clear();
        requestCounter = 0;
        return upper;
    }
}

