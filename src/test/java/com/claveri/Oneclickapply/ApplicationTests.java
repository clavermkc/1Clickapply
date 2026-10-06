package com.claveri.Oneclickapply;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Charge tout le contexte Spring : nécessite une vraie base PostgreSQL (Neon) et le .env.
 * Désactivé pour que `./gradlew build` passe hors ligne ; les tests unitaires sont dans service/.
 */
@SpringBootTest
@Disabled("Nécessite la base Neon - lancer à la main si besoin")
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}
