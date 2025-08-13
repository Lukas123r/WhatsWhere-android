package com.example.whatswhere.data

import android.content.Context
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index // Import hinzufügen
import androidx.room.PrimaryKey
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "categories",
    indices = [Index(value = ["nameKey"], unique = true)] // HINZUGEFÜGT: Stellt Eindeutigkeit von nameKey sicher
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @get:PropertyName("name") @set:PropertyName("name")
    var nameKey: String = ""
) : Parcelable {
    @Exclude
    fun getDisplayName(context: Context): String {
        // Deine bestehende Logik für getDisplayName ist gut.
        // Stelle sicher, dass du hier auch einen Fallback hast, falls die Ressource nicht gefunden wird,
        // ähnlich wie in deiner Tag-Klasse, falls nameKey mal nicht als Ressource existiert.
        val resourceId = context.resources.getIdentifier(nameKey, "string", context.packageName)
        return if (resourceId != 0) {
            context.getString(resourceId)
        } else {
            // Fallback, falls die String-Ressource nicht existiert (z.B. für benutzerdefinierte Kategorien in Zukunft)
            // oder wenn ein vordefinierter Schlüssel nicht in allen Sprachen vorhanden ist.
            // Du könntest hier eine ähnliche Formatierung wie bei den Tags anwenden oder einfach den nameKey zurückgeben.
            nameKey.removePrefix("category_")
                .replace("_", " ")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } // Macht ersten Buchstaben groß
        }
    }
}
