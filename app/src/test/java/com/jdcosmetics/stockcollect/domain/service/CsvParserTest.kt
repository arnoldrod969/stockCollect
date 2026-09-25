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

    // --- mapperCatalogue -----------------------------------------------
    // Le fichier source n'\u00e9chappe pas les virgules des noms de produits. Les cas \u00e0 7 colonnes
    // ci-dessous sont tir\u00e9s tels quels de catalogue17022025.csv : ils \u00e9taient import\u00e9s avec un
    // nom tronqu\u00e9, une quantit\u00e9 nulle et un prix pris dans la mauvaise colonne, sans erreur.

    private fun mapper(ligne: String) = CsvParser.mapperCatalogue(CsvParser.parseLigne(ligne, ','))

    @Test
    fun `mapperCatalogue ligne standard 6 colonnes`() {
        val l = mapper("1001000203,6972011064552,,1 MILLION 50ML,0.00,1500.00")
        assertEquals("1001000203", l.codeProduit)
        assertEquals("6972011064552", l.codeBarre)
        assertEquals("1 MILLION 50ML", l.nomProduit)
        assertEquals("0.00", l.quantite)
        assertEquals("1500.00", l.prix)
        assertFalse("Une ligne bien form\u00e9e n'est pas recoll\u00e9e", l.recollee)
    }

    @Test
    fun `mapperCatalogue recolle une virgule dans le nom`() {
        val l = mapper("1001000288,6292014104148,,AL-NUAIM WHITE ORCHID 9,9ML,0.00,1500.00")
        assertEquals("AL-NUAIM WHITE ORCHID 9,9ML", l.nomProduit)
        assertEquals("0.00", l.quantite)
        assertEquals("1500.00", l.prix)
        assertTrue(l.recollee)
    }

    @Test
    fun `mapperCatalogue recolle un nom commencant par une virgule interne`() {
        val l = mapper("1001101141,6292014105473,,EDP D,LOVE 20 ML,0.00,1500.00")
        assertEquals("EDP D,LOVE 20 ML", l.nomProduit)
        assertEquals("1500.00", l.prix)
    }

    @Test
    fun `mapperCatalogue recolle un nom contenant aussi un point-virgule`() {
        val l = mapper(
            "1001000361,6947835826655,,ORANGE ; STRAWBERRY , APRICOT   FACE & BODY WASH 300ML,0.00,1200.00"
        )
        assertEquals("ORANGE ; STRAWBERRY,APRICOT   FACE & BODY WASH 300ML", l.nomProduit)
        assertEquals("1200.00", l.prix)
    }

    @Test
    fun `mapperCatalogue ligne courte garde les index fixes`() {
        val l = mapper("1001000203,6972011064552,,1 MILLION 50ML,7")
        assertEquals("1 MILLION 50ML", l.nomProduit)
        assertEquals("7", l.quantite)
        assertNull("Pas de prix sur une ligne de 5 colonnes", l.prix)
        assertFalse(l.recollee)
    }

    @Test
    fun `mapperCatalogue conserve un code-barre vide`() {
        val l = mapper("1001000203,,,1 MILLION 50ML,0.00,1500.00")
        assertEquals("", l.codeBarre)
        assertEquals("1 MILLION 50ML", l.nomProduit)
    }
}
