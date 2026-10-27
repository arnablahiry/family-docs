package com.familydocs.app.ui.onboarding

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familydocs.app.AppContainer
import com.familydocs.app.data.DEFAULT_SELF_AVATAR
import com.familydocs.app.domain.Countries
import com.familydocs.app.ocr.Images
import com.familydocs.app.ui.ds.CropState
import com.familydocs.app.ui.ds.cropToSquare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Create account and "Your account" (edit) share this. */
class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    var name by mutableStateOf("")
    var countryCode by mutableStateOf("+91")
    var phone by mutableStateOf("")
    var email by mutableStateOf("")
    var googleLinked by mutableStateOf(false)
    var countries by mutableStateOf(Countries.DEFAULT)
    var errors by mutableStateOf(emptyMap<String, String>()); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var pendingPasscode: String? = null

    // ---- profile picture: a photo (from the gallery or Google) or an illustrated character ----
    /** Photo currently saved (editing an existing account). */
    var savedPhoto by mutableStateOf<String?>(null); private set
    /** Newly chosen photo, still being positioned in the crop circle. */
    var picked by mutableStateOf<Bitmap?>(null); private set
    val crop = CropState()
    var removePhoto by mutableStateOf(false); private set
    var avatar by mutableStateOf(DEFAULT_SELF_AVATAR)

    /** True once a Google client ID is in this build (see README → Google setup). Until then Google is optional. */
    val googleReady: Boolean get() = !c.google.webClientId.startsWith("REPLACE_ME")
    var editing by mutableStateOf(false); private set

    init {
        viewModelScope.launch {
            c.repo.accountNow()?.let { a ->
                editing = true
                name = a.name; countryCode = a.countryCode; phone = a.phone; email = a.backupEmail; googleLinked = a.googleLinked
                countries = Countries.decode(a.countries)
                val self = c.repo.selfNow()
                savedPhoto = self?.photoFile ?: a.photoFile
                avatar = self?.avatar ?: DEFAULT_SELF_AVATAR
            }
        }
    }

    fun pickPhoto(context: Context, uri: Uri) = viewModelScope.launch {
        val bmp = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { Images.decode(it, 1600) } }
        if (bmp != null) { picked = bmp; crop.zoom = 1f; crop.pan = androidx.compose.ui.geometry.Offset.Zero; removePhoto = false }
    }

    fun clearPhoto() { picked = null; removePhoto = true }

    fun chooseCharacter(key: String) { avatar = key; picked = null; removePhoto = true }

    val hasPhoto: Boolean get() = picked != null || (savedPhoto != null && !removePhoto)

    fun continueWithGoogle(activity: Context) = viewModelScope.launch {
        if (!googleReady) { message = "Google sign-in isn't set up in this build yet. Type your email for now; you can link Google later."; return@launch }
        busy = true
        c.google.signIn(activity)
            .onSuccess { p ->
                email = p.email
                if (name.isBlank()) name = p.name.orEmpty()
                googleLinked = true
                message = null
                // Google's photo is only a starting point: it goes into the crop circle and can be replaced.
                if (!hasPhoto) p.photoUrl?.let { c.google.downloadPhoto(it) }?.let { bytes ->
                    withContext(Dispatchers.Default) { Images.decode(bytes, 900) }?.let { picked = it; removePhoto = false }
                }
            }
            .onFailure { message = "Google sign-in didn't finish. Try again." }
        busy = false
    }

    fun toggleCountry(code: String) { countries = if (code in countries) countries - code else countries + code }

    fun unlinkGoogle() { googleLinked = false; email = "" }

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (name.trim().length < 2) e["name"] = "Enter your name"
        val digits = phone.filter(Char::isDigit)
        if (digits.length !in 7..12) e["phone"] = "Enter a phone number with 7–12 digits"
        if (googleReady && (!googleLinked || email.isBlank())) e["email"] = "Connect your Google account to continue"
        if (!googleReady && email.isNotBlank() && !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$").matches(email.trim())) e["email"] = "Enter an email like name@gmail.com"
        if (countries.isEmpty()) e["countries"] = "Pick at least one country"
        errors = e
        return e.isEmpty()
    }

    /** [cropBoxPx] = size of the crop circle on screen, so the saved photo is exactly what was shown. */
    fun finish(biometric: Boolean?, cropBoxPx: Float, onDone: () -> Unit) = viewModelScope.launch {
        busy = true
        val bytes = picked?.let { bmp -> withContext(Dispatchers.Default) { Images.jpeg(cropToSquare(bmp, crop, cropBoxPx)) } }
        c.repo.saveAccount(name.trim(), countryCode, phone.trim(), email.trim(), bytes, googleLinked, countries,
            removePhoto = removePhoto && bytes == null, avatar = avatar)
        pendingPasscode?.let { code -> withContext(Dispatchers.Default) { c.passcode.set(code) } }
        if (biometric != null) c.settings.update { it.copy(biometric = biometric) }
        c.appLock.unlock()
        busy = false
        onDone()
    }
}
