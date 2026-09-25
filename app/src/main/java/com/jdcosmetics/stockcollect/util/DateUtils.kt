package com.jdcosmetics.stockcollect.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.ParsePosition
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dates de l'app, toutes en `java.time` : un [DateTimeFormatter] est immuable, donc partageable
 * entre threads, là où un `SimpleDateFormat` partagé pouvait corrompre une date sous accès
 * concurrent (TASK-14).
 *
 * Deux familles, à ne pas mélanger :
 * - le format **machine** (ISO en base, `date_heure_cloture` envoyé à Nirgescom, nom du fichier
 *   d'export) est figé en [Locale.US] : chiffres latins quelle que soit la langue de la tablette.
 *   Construit avec la locale de l'appareil, il pouvait sortir en chiffres arabes, et l'API refusait
 *   alors la session en 422 — sans issue, une fois la session clôturée ;
 * - le format **d'affichage** suit la locale de l'appareil : seul un humain le lit.
 */
object DateUtils {

    private val isoFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val fileNameFormat = DateTimeFormatter.ofPattern("ddMMyyyy_HHmm", Locale.US)
    private val displayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val displayDateOnly = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** Horodatage ISO 8601 courant (heure locale, sans fuseau) — utilisé pour les timestamps DB */
    fun nowIso(): String = toIso(LocalDateTime.now())

    /** Format ISO stocké en base et envoyé à l'API : 2026-05-23T10:14:00 */
    fun toIso(date: LocalDateTime): String = isoFormat.format(date)

    /** Format affichage : 23/05/2026 10:14 */
    fun toDisplay(isoDate: String): String =
        parseIso(isoDate)?.let { displayFormat.format(it) } ?: isoDate

    /** Format date seule : 23/05/2026 */
    fun toDisplayDate(isoDate: String): String =
        parseIso(isoDate)?.let { displayDateOnly.format(it) } ?: isoDate

    /** Format pour nom de fichier : STOCK_23052026_1014.csv */
    fun toFileName(): String = toFileName(LocalDateTime.now())

    fun toFileName(date: LocalDateTime): String = "STOCK_${fileNameFormat.format(date)}.csv"

    /**
     * Relit une date stockée. Comme l'ancien `SimpleDateFormat.parse`, ne lit que le début de la
     * chaîne : une suite éventuelle (millisecondes, fuseau) est ignorée plutôt que refusée.
     * Null si la chaîne n'est pas au format ISO : l'appelant affiche alors le texte brut.
     */
    internal fun parseIso(isoDate: String): LocalDateTime? = try {
        LocalDateTime.from(isoFormat.parse(isoDate, ParsePosition(0)))
    } catch (e: RuntimeException) {
        null
    }
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
