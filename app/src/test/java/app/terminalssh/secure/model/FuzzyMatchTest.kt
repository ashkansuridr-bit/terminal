package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FuzzyMatchTest {

    @Test fun blankQueryMatchesEverything() {
        assertTrue(FuzzyMatch.matches("", "anything"))
        assertTrue(FuzzyMatch.matches("   ", "anything"))
    }

    @Test fun exactSubstringMatches() {
        assertTrue(FuzzyMatch.matches("prod", "prod-db-01"))
        assertTrue(FuzzyMatch.matches("DB", "prod-db-01"))
    }

    @Test fun scatteredSubsequenceMatches() {
        // The whole point: none of these are substrings of the candidate.
        assertTrue(FuzzyMatch.matches("pdb", "prod-db-01"))
        assertTrue(FuzzyMatch.matches("pd01", "prod-db-01"))
        assertTrue(FuzzyMatch.matches("pddb", "prod-db-01"))
    }

    @Test fun outOfOrderCharactersDoNotMatch() {
        assertFalse(FuzzyMatch.matches("bdp", "prod-db-01"))
    }

    @Test fun queryLongerThanCandidateDoesNotMatch() {
        assertFalse(FuzzyMatch.matches("prod-db-01-extra", "prod-db"))
    }

    @Test fun absentCharacterDoesNotMatch() {
        assertFalse(FuzzyMatch.matches("prodz", "prod-db-01"))
    }

    @Test fun exactSubstringOutranksScatteredSubsequence() {
        val exact = FuzzyMatch.score("proddb", "proddb-01")
        val scattered = FuzzyMatch.score("proddb", "prod-x-d-b")
        assertTrue(exact > scattered, "exact=$exact scattered=$scattered")
    }

    @Test fun wordBoundaryMatchOutranksMidWordMatch() {
        val boundary = FuzzyMatch.score("db", "prod-db")
        val midWord = FuzzyMatch.score("db", "prodxdbx")
        assertTrue(boundary > midWord, "boundary=$boundary midWord=$midWord")
    }

    @Test fun matchingIsCaseInsensitive() {
        assertEquals(FuzzyMatch.score("PROD", "prod-db"), FuzzyMatch.score("prod", "PROD-DB"))
    }

    @Test fun emptyCandidateOnlyMatchesEmptyQuery() {
        assertTrue(FuzzyMatch.matches("", ""))
        assertFalse(FuzzyMatch.matches("a", ""))
    }
}
