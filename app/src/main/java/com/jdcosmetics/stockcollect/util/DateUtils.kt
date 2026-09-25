package com.jdcosmetics.stockcollect.util

import java.math.BigDecimal
import java.math.RoundingMode
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

    /** Décimales conservées sur une quantité : le plafond de `POST /documents` (SPEC §4.2). */
    const val DECIMALES_QUANTITE = 3

    /**
     * Ramène une quantité à [DECIMALES_QUANTITE] décimales. Les `Double` additionnés dérivent :
     * 2.3 − 1 vaut 1.2999999999999998 et 1.1 + 2.2 vaut 3.3000000000000003, que l'API refuse en
     * 422 (plus de 3 décimales) — et la session, clôturée, ne peut plus être corrigée. Toute
     * quantité écrite en base passe donc par ici (SessionRepository).
     *
     * Par la représentation décimale (`toString`) et non la valeur binaire exacte : sinon 1.0005,
     * stocké 1.000499999…, s'arrondirait vers le bas.
     */
    fun normaliserQuantite(q: Double): Double {
        if (q.isNaN() || q.isInfinite()) return q
        return BigDecimal(q.toString()).setScale(DECIMALES_QUANTITE, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * Formate une quantité avec 1 décimale minimum et [DECIMALES_QUANTITE] au plus :
     * 10 → "10.0", 1.25 → "1.25", 1.2999999999999998 → "1.3".
     *
     * Sert à l'écran comme à l'export CSV : l'écran, le fichier et Nirgescom portent ainsi la même
     * quantité. À 1 décimale fixe, une ligne à 1.25 sortait « 1.3 » dans le CSV et 1.25 chez
     * Nirgescom. Les entiers, cas courant, s'écrivent comme avant.
     */
    fun formatQuantite(q: Double): String {
        if (q.isNaN() || q.isInfinite()) return q.toString()
        val d = BigDecimal(q.toString())
            .setScale(DECIMALES_QUANTITE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return (if (d.scale() < 1) d.setScale(1) else d).toPlainString()
    }

    /** Formate un prix FCFA : 1500.0 → "1 500 FCFA" */
    fun formatPrix(prix: Double): String {
        return "${"%,.0f".format(prix).replace(",", " ")} FCFA"
    }
}
