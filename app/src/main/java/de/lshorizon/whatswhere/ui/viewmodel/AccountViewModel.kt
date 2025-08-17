package de.lshorizon.whatswhere.ui.viewmodel

import androidx.lifecycle.*
import de.lshorizon.whatswhere.data.FirestoreManager
import de.lshorizon.whatswhere.data.ItemDao
import de.lshorizon.whatswhere.data.User
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AccountViewModel(itemDao: ItemDao) : ViewModel() {

    val totalItemCount: Flow<Int> = itemDao.getTotalItemCount()
    val totalItemValue: Flow<Double?> = itemDao.getTotalItemValue()

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile: StateFlow<User?> = _userProfile

    init {
        loadUserProfile()
    }

    // KORREKTUR: Diese Funktion wird jetzt von der Activity aufgerufen, um sicherzustellen,
    // dass sie ausgeführt wird, wenn der Nutzer garantiert eingeloggt ist.
    fun loadUserProfile() {
        Firebase.auth.currentUser?.let { user ->
            viewModelScope.launch {
                _userProfile.value = FirestoreManager.getOrCreateUserProfile(user)
            }
        }
    }

    fun updateUserName(newName: String) {
        Firebase.auth.currentUser?.uid?.let { uid ->
            viewModelScope.launch {
                FirestoreManager.updateUserName(uid, newName)
                // Wir müssen das Profil hier nicht neu laden, da der Firestore-Listener in der
                // AccountActivity die UI automatisch aktualisieren würde (Best Practice).
                // Aber zur Sicherheit laden wir es hier manuell neu.
                loadUserProfile()
            }
        }
    }
}

class AccountViewModelFactory(private val itemDao: ItemDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AccountViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AccountViewModel(itemDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}