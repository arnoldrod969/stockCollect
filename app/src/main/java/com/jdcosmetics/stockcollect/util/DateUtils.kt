package com.jdcosmetics.stockcollect.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("ddMMyyyy_HHmm", Locale.getDefault())
    private val displayDateOnly = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    /** Horodatage ISO 8601 courant — utilisé pour les timestamps DB */
    fun nowIso(): String = isoFormat.format(Date())

    /** Format affichage : 23/05/2026 10:14 */
    fun toDisplay(isoDate: String): String {
        return try {
            val date = isoFormat.parse(isoDate) ?: return isoDate
            displayFormat.format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    /** Format date seule : 23/05/2026 */
    fun toDisplayDate(isoDate: String): String {
        return try {
            val date = isoFormat.parse(isoDate) ?: return isoDate
            displayDateOnly.format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    /** Format pour nom de fichier : STOCK_23052026_1014.csv */
    fun toFileName(): String = "STOCK_${fileNameFormat.format(Date())}.csv"
}

object FormatUtils {

    /** Formate une quantité avec 1 décimale minimum : 10 → "10.0" */
    fun formatQuantite(q: Double): String = "%.1f".format(q)

    /** Formate un prix FCFA : 1500.0 → "1 500 FCFA" */
    fun formatPrix(prix: Double): String {
        return "${"%,.0f".format(prix).replace(",", " ")} FCFA"
    }
}
