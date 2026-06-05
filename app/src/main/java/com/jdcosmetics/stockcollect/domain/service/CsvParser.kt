package com.jdcosmetics.stockcollect.domain.service

import android.content.Context
import android.net.Uri
import com.jdcosmetics.stockcollect.util.Constants
import java.io.BufferedReader
import java.io.InputStreamReader

object CsvParser {

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
