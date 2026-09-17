package com.jdcosmetics.stockcollect.domain.service

import android.content.Context
import android.net.Uri
import com.jdcosmetics.stockcollect.util.Constants
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Une ligne du catalogue, colonnes déjà réparties. `recollee` signale que le nom du produit
 * contenait une virgule non échappée et a dû être reconstitué.
 */
data class LigneCatalogue(
    val codeProduit: String?,
    val codeBarre: String?,
    val nomProduit: String?,
    val quantite: String?,
    val prix: String?,
    val recollee: Boolean
)

object CsvParser {

    /**
     * Répartit les colonnes d'une ligne du catalogue.
     *
     * Le fichier source n'échappe pas les virgules contenues dans les noms de produits :
     * « AL-NUAIM WHITE ORCHID 9,9ML » produit 7 colonnes au lieu de 6. Lire aveuglément les index
     * fixes donnait alors un nom tronqué, une quantité non numérique ramenée à 0 et un prix pris
     * dans la mauvaise colonne — **sans la moindre erreur signalée**.
     *
     * La structure reste pourtant déterministe : les deux dernières colonnes sont toujours la
     * quantité et le prix, donc tout le surplus appartient au nom.
     *
     * Le recollage se fait avec une virgule nue : « 9 » + « 9ML » redonne « 9,9ML ». Les espaces
     * qui entouraient éventuellement la virgule d'origine sont perdus (parseLigne ayant trimmé
     * chaque champ) — différence cosmétique, assumée.
     */
    fun mapperCatalogue(colonnes: List<String>): LigneCatalogue {
        val nbAttendu = Constants.COL_CATALOGUE_NB_ATTENDU

        if (colonnes.size >= nbAttendu) {
            return LigneCatalogue(
                codeProduit = colonnes.getOrNull(Constants.COL_CATALOGUE_CODE_PRODUIT),
                codeBarre = colonnes.getOrNull(Constants.COL_CATALOGUE_CODE_BARRE),
                // Du début du nom jusqu'à l'avant-dernière colonne exclue.
                nomProduit = colonnes
                    .subList(Constants.COL_CATALOGUE_NOM_PRODUIT, colonnes.size - 2)
                    .joinToString(","),
                quantite = colonnes[colonnes.size - 2],
                prix = colonnes[colonnes.size - 1],
                recollee = colonnes.size > nbAttendu
            )
        }

        // Ligne courte (4 ou 5 colonnes) : pas de surplus possible, index fixes.
        return LigneCatalogue(
            codeProduit = colonnes.getOrNull(Constants.COL_CATALOGUE_CODE_PRODUIT),
            codeBarre = colonnes.getOrNull(Constants.COL_CATALOGUE_CODE_BARRE),
            nomProduit = colonnes.getOrNull(Constants.COL_CATALOGUE_NOM_PRODUIT),
            quantite = colonnes.getOrNull(Constants.COL_CATALOGUE_QUANTITE),
            prix = colonnes.getOrNull(Constants.COL_CATALOGUE_PRIX),
            recollee = false
        )
    }

    fun detecterSeparateur(premiereLigne: String): Char {
        val nbVirgules = premiereLigne.count { it == Constants.CSV_SEPARATEUR_VIRGULE }
        val nbPointVirgules = premiereLigne.count { it == Constants.CSV_SEPARATEUR_POINTVIRGULE }
        return if (nbPointVirgules > nbVirgules) Constants.CSV_SEPARATEUR_POINTVIRGULE
        else Constants.CSV_SEPARATEUR_VIRGULE
    }

    fun ouvrirReader(context: Context, uri: Uri, encodage: String = Constants.CSV_ENCODAGE_UTF8): BufferedReader {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier : $uri")
        return BufferedReader(InputStreamReader(inputStream, encodage))
    }

    fun parseLigne(ligne: String, separateur: Char): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in ligne) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == separateur && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    fun lireFichier(
        context: Context,
        uri: Uri,
        separateur: Char? = null,
        encodage: String = Constants.CSV_ENCODAGE_UTF8
    ): Pair<Char, List<List<String>>> {
        val reader = ouvrirReader(context, uri, encodage)
        val lignes = mutableListOf<List<String>>()
        var sepDetecte = separateur ?: Constants.CSV_SEPARATEUR_VIRGULE
        var premiereLigne = true

        reader.use { br ->
            br.lineSequence()
                .filter { it.isNotBlank() }
                .forEach { ligne ->
                    if (premiereLigne && separateur == null) {
                        sepDetecte = detecterSeparateur(ligne)
                        premiereLigne = false
                    }
                    lignes.add(parseLigne(ligne, sepDetecte))
                }
        }
        return Pair(sepDetecte, lignes)
    }

    fun lireFichierAvecFallbackEncodage(
        context: Context,
        uri: Uri
    ): Pair<Char, List<List<String>>> {
        return try {
            lireFichier(context, uri, encodage = Constants.CSV_ENCODAGE_UTF8)
        } catch (e: Exception) {
            lireFichier(context, uri, encodage = Constants.CSV_ENCODAGE_LATIN1)
        }
    }
}
