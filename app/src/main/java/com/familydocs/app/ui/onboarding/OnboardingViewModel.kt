package com.familydocs.app.ui.onboarding

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familydocs.app.AppContainer
import com.familydocs.app.domain.Countries
import com.familydocs.app.ocr.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    var name by mutableStateOf("")
    var countryCode by mutableStateOf("+91")
    var phone by mutableStateOf("")
    var email by mutableStateOf("")
    var googleLinked by mutableStateOf(false)
    var countries by mutableStateOf(Countries.DEFAULT)
    var photo by mutableStateOf<ByteArray?>(null); private set
    var photoBitmap by mutableStateOf<Bitmap?>(null); private set
    var errors by mutableStateOf(emptyMap<String, String>()); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var pendingPasscode: String? = null

    init {
        // Editing an existing account pre-fills the form.
        viewModelScope.launch {
            c.repo.accountNow()?.let { a ->
                name = a.name; countryCode = a.countryCode; phone = a.phone; email = a.backupEmail; googleLinked = a.googleLinked; countries = Countries.decode(a.countries)
            }
        }
    }

    fun continueWithGoogle(activity: Context) = viewModelScope.launch {
        busy = true
        c.google.signIn(activity)
            .onSuccess { p ->
                email = p.email
                if (name.isBlank()) name = p.name.orEmpty()
                googleLinked = true
                p.photoUrl?.let { c.google.downloadPhoto(it) }?.let { bytes ->
                    photo = bytes
                    photoBitmap = withContext(Dispatchers.Default) { Images.decode(bytes, 600) }
                }
            }
            .onFailure { message = "Google sign-in didn't finish. Try again." }
        busy = false
    }

    fun toggleCountry(code: String) { countries = if (code in countries) countries - code else countries + code }

    fun unlinkGoogle() { googleLinked = false; email = ""; photo = null; photoBitmap = null }

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (name.trim().length < 2) e["name"] = "Enter your name"
        val digits = phone.filter(Char::isDigit)
        if (digits.length !in 7..12) e["phone"] = "Enter a phone number with 7–12 digits"
        if (!googleLinked || email.isBlank()) e["email"] = "Connect your Google account to continue"
        if (countries.isEmpty()) e["countries"] = "Pick at least one country"
        errors = e
        return e.isEmpty()
    }

    fun finish(biometric: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        busy = true
        c.repo.saveAccount(name.trim(), countryCode, phone.trim(), email.trim(), photo, googleLinked, countries)
        pendingPasscode?.let { code -> withContext(Dispatchers.Default) { c.passcode.set(code) } }
        c.settings.update { it.copy(biometric = biometric) }
        c.appLock.unlock()
        busy = false
        onDone()
    }
}
