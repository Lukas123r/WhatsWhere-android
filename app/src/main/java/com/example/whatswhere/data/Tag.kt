package com.example.whatswhere.data

import android.content.Context
import android.os.Parcelable
import android.util.Log // Import für Logging
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName // Behalte deine Firestore-Annotationen
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "tags",
    // Der 'nameKey' sollte eindeutig sein, um Konflikte zu vermeiden,
    // sowohl für Ressourcenschlüssel als auch für benutzerdefinierte Namen.
    indices = [Index(value = ["nameKey"], unique = true)]
)
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Für vordefinierte Tags: Speichert den Ressourcenschlüssel (z.B. "predefined_tag_urgent").
     * Für benutzerdefinierte Tags: Speichert den vom Benutzer eingegebenen Namen.
     * Wird in Firestore als "name" serialisiert und deserialisiert.
     */
    @get:PropertyName("name") @set:PropertyName("name")
    var nameKey: String = "",

    /**
     * Flag, um vordefinierte Tags (deren Namen aus String-Ressourcen stammen)
     * von benutzererstellten Tags zu unterscheiden.
     * Dieses Feld wird NICHT in Firestore gespeichert, da es eine reine lokale Logik ist.
     */
    @get:Exclude // Nicht in Firestore speichern
    var isPredefined: Boolean = false // Standardmäßig false für neue Tags
) : Parcelable {

    /**
     * Hilfsfunktion, um den übersetzten Namen des Tags zu erhalten,
     * wenn es vordefiniert ist, oder den direkten Namen/Schlüssel, wenn es benutzerdefiniert ist.
     */
    @Exclude // Diese Funktion sollte nicht Teil der Firestore-Serialisierung sein
    fun getDisplayName(context: Context): String {
        return if (isPredefined) {
            try {
                val resourceId = context.resources.getIdentifier(nameKey, "string", context.packageName)
                if (resourceId != 0) { // 0 bedeutet "nicht gefunden"
                    context.getString(resourceId)
                } else {
                    Log.w(
                        "TagDisplay",
                        "String-Ressource für vordefinierten Tag-Schlüssel '$nameKey' nicht gefunden. " +
                                "Paket: ${context.packageName}. Verwende Fallback-Formatierung."
                    )
                    // Fallback: Versuche, aus "predefined_tag_example_name" -> "Example Name" zu machen
                    nameKey.removePrefix("predefined_tag_")
                        .replace("_", " ")
                        .split(' ')
                        .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
                }
            } catch (e: Exception) {
                Log.e(
                    "TagDisplay",
                    "Fehler beim Laden der String-Ressource für Tag-Schlüssel '$nameKey'.",
                    e
                )
                nameKey.removePrefix("predefined_tag_")
                    .replace("_", " ")
                    .split(' ')
                    .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
            }
        } else {
            // Für benutzerdefinierte Tags einfach den in nameKey gespeicherten Namen zurückgeben
            nameKey
        }
    }

    // Standardkonstruktor für Room und Parcelize, falls benötigt (normalerweise generiert Kotlin das)
    // constructor() : this(0, "", false) // Kann oft weggelassen werden
}

