# Règles R8 / ProGuard — StockCollect
#
# Le build release active isMinifyEnabled + isShrinkResources (app/build.gradle.kts).
# Room, Hilt et ML Kit embarquent leurs propres règles « consumer » dans leurs AAR ;
# ce fichier ne couvre que ce qu'elles ne peuvent pas deviner, plus ce qui tient à
# des choix propres au projet.

# ---------------------------------------------------------------------------
# Traces d'exception lisibles
# ---------------------------------------------------------------------------
# L'app tourne sur les tablettes du magasin, sans remontée de crash automatique :
# une trace lisible est la seule chose exploitable quand un magasinier signale un
# plantage. On garde les numéros de ligne en masquant les noms de fichiers.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations, génériques et classes internes : Room et Hilt s'appuient dessus,
# et leur perte produit des erreurs à l'exécution difficiles à relier à R8.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ---------------------------------------------------------------------------
# Navigation Component — instanciation par réflexion
# ---------------------------------------------------------------------------
# nav_graph.xml désigne chaque destination par son nom de classe en clair
# (android:name="com.jdcosmetics.stockcollect.ui.home.HomeFragment"). C'est
# FragmentFactory qui les instancie par réflexion : R8 ne voit aucun appel vers
# ces classes et les supprimerait.
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends android.app.Application

# Les arguments de navigation sont tous primitifs ici (string, long) : aucune
# règle Parcelable/Serializable n'est nécessaire. À revoir si une destination
# reçoit un jour un objet.

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
# RoomDatabase instancie son implémentation générée via
# Class.forName("<Database>_Impl") — réflexion pure, invisible pour R8.
-keep class com.jdcosmetics.stockcollect.data.db.StockCollectDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Les entités portent les noms de colonnes dans leurs annotations et sont
# construites par le code généré. On garde la classe et ses constructeurs.
-keep class com.jdcosmetics.stockcollect.data.db.entity.** { *; }

# ---------------------------------------------------------------------------
# Hilt / Dagger
# ---------------------------------------------------------------------------
# Les AAR Hilt fournissent l'essentiel. Restent les points d'entrée du projet,
# désignés par annotation et non par appel direct.
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { <init>(...); }
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep @dagger.hilt.InstallIn class *

# ---------------------------------------------------------------------------
# ML Kit — modèle de scan embarqué
# ---------------------------------------------------------------------------
# Le modèle de reconnaissance est chargé par nom depuis le bundle : les classes
# du package ne doivent pas être renommées.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-dontwarn com.google.mlkit.**

# ---------------------------------------------------------------------------
# Kotlin / coroutines
# ---------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-dontwarn kotlin.Unit
