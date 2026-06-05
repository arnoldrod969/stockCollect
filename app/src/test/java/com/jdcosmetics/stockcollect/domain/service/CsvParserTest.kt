package com.jdcosmetics.stockcollect.domain.service

import org.junit.Assert.*
import org.junit.Test

class CsvParserTest {

    @Test
    fun `detecterSeparateur retourne virgule si plus de virgules`() {
        val ligne = "1001000203,6972011064552,,1 MILLION 50ML,0.00,1500.00"
        assertEquals(',', CsvParser.detecterSeparateur(ligne))
    }

    @Test
    fun `detecterSeparateur retourne point-virgule si plus de points-virgules`() {
        val ligne = "1001000203;6972011064552;;1 MILLION 50ML;0.00;1500.00"
        assertEquals(';', CsvParser.detecterSeparateur(ligne))
    }

    @Test
    fun `detecterSeparateur retourne virgule par defaut si egal`() {
        val ligne = "A,B;C"
        assertEquals(',', CsvParser.detecterSeparateur(ligne))
    }

    @Test
    fun `parseLigne catalogue standard 6 colonnes`() {
        val ligne = "1001000203,6972011064552,,1 MILLION 50ML,0.00,1500.00"
        val cols = CsvParser.parseLigne(ligne, ',')
        assertEquals(6, cols.size)
        assertEquals("1001000203", cols[0])
        assertEquals("6972011064552", cols[1])
        assertEquals("", cols[2])
        assertEquals("1 MILLION 50ML", cols[3])
        assertEquals("0.00", cols[4])
        assertEquals("1500.00", cols[5])
    }

    @Test
    fun `parseLigne gere les guillemets`() {
        val ligne = """1001000203,"NOM, AVEC VIRGULE",,PRODUIT,0.0,500.0"""
        val cols = CsvParser.parseLigne(ligne, ',')
        assertEquals(6, cols.size)
        assertEquals("NOM, AVEC VIRGULE", cols[1])
    }

    @Test
    fun `parseLigne correspondance 2 colonnes`() {
        val ligne = "022200954419,1001000091"
        val cols = CsvParser.parseLigne(ligne, ',')
        assertEquals(2, cols.size)
        assertEquals("022200954419", cols[0])
        assertEquals("1001000091", cols[1])
    }

    @Test
    fun `parseLigne trimme les espaces`() {
        val ligne = " 1001000203 , 6972011064552 "
        val cols = CsvParser.parseLigne(ligne, ',')
        assertEquals("1001000203", cols[0])
        assertEquals("6972011064552", cols[1])
    }

    @Test
    fun `parseLigne ligne vide retourne liste avec element vide`() {
        val cols = CsvParser.parseLigne("", ',')
        assertEquals(1, cols.size)
        assertEquals("", cols[0])
    }

    @Test
    fun `colonnes insuffisantes moins de 4`() {
        val ligne = "1001000203,6972011064552"
        val cols = CsvParser.parseLigne(ligne, ',')
        assertTrue("Moins de 4 colonnes d\u00e9tect\u00e9es", cols.size < 4)
    }
}
