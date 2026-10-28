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
import com.familydocs.app.auth.GoogleAccountHelper
import com.familydocs.app.data.DEFAULT_SELF_AVATAR
import com.familydocs.app.domain.Countries
import com.familydocs.app.ocr.Images
import com.familydocs.app.ui.ds.CropState
import com.familydocs.app.ui.ds.cropToSquare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Create account, "I already have an account" (restore from Drive) and "Your account" (edit) share this. */
class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    var name by mutableStateOf("")
    var countryCode by mutableStateOf("+91")
    var phone by mutableStateOf("")
    var email by mutableStateOf("")
    var googleLinked by mutableStateOf(false)
    var countries by mutableStateOf(Countries.DEFAULT)
    /** Sign-up: "create" a group (named [groupName]) or "join" one with [groupCode]. */
    var groupMode by mutableStateOf("create")
    var groupName by mutableStateOf("")
    var groupCode by mutableStateOf("")
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

    var editing by mutableStateOf(false); private set

    // ---- Google ----
    /** Token + profile from the last "Continue with Google" in this flow. */
    var session: GoogleAccountHelper.Session? = null; private set
    /** Create account found that this Google account already has a backup. */
    var foundBackup by mutableStateOf(false); private set

    // ---- restore ("I already have an account") ----
    var restoreStatus by mutableStateOf<String?>(null); private set
    var restoreError by mutableStateOf<String?>(null); private set
    /** The vault came from Drive, so finishing only sets this phone's passcode. */
    var restored by mutableStateOf(false); private set

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

    /** Your Google profile photo, downloaded when you connect Google; shown as one more choice, not used by default. */
    var googlePhoto by mutableStateOf<Bitmap?>(null); private set
    fun useGooglePhoto() { googlePhoto?.let { picked = it; crop.zoom = 1f; crop.pan = androidx.compose.ui.geometry.Offset.Zero; removePhoto = false } }

    private var avatarChosen = false
    fun chooseCharacter(key: String) { avatar = key; avatarChosen = true; picked = null; removePhoto = true }

    val hasPhoto: Boolean get() = picked != null || (savedPhoto != null && !removePhoto)

    /** Call right before launching the Google screen. */
    fun googleStarted() { busy = true; message = null; restoreError = null }

    /** Result of "Continue with Google" on Create account / Your account. */
    fun onGoogle(r: Result<GoogleAccountHelper.Session>) = viewModelScope.launch {
        r.onSuccess { s ->
            session = s
            email = s.profile.email
            if (name.isBlank()) name = s.profile.name.orEmpty()
            googleLinked = true
            // Google's photo is only offered: it shows up as an extra choice next to the characters, never applied on its own.
            s.profile.photoUrl?.let { c.google.downloadPhoto(it) }?.let { bytes ->
                withContext(Dispatchers.Default) { Images.decode(bytes, 900) }?.let { googlePhoto = it }
            }
            if (!editing) foundBackup = runCatching { c.backup.hasBackup(s.token) }.getOrDefault(false)
        }.onFailure { message = GoogleAccountHelper.explain(it) }
        busy = false
    }

    /** "I already have an account": find the Drive backup of this Google account and bring it to this phone. */
    fun signIn(r: Result<GoogleAccountHelper.Session>, onRestored: () -> Unit) = viewModelScope.launch {
        val s = r.getOrElse {
            busy = false
            restoreError = GoogleAccountHelper.explain(it)
            return@launch
        }
        session = s; busy = true; restoreError = null
        restoreStatus = "Looking for your backup in ${s.profile.email}…"
        val exists = runCatching { c.backup.hasBackup(s.token) }
        when {
            exists.isFailure -> restoreError = GoogleAccountHelper.explain(exists.exceptionOrNull()!!)
            exists.getOrNull() != true -> restoreError = "There's no Lararium backup in ${s.profile.email}. Sign in with another Google account, or create a new account."
            else -> runCatching { c.backup.restore(s.token) { msg -> restoreStatus = msg } }
                .onSuccess {
                    restored = true
                    c.settings.update { it.copy(lastBackup = System.currentTimeMillis()) }
                    c.setBackups(true)
                    restoreStatus = null; busy = false
                    onRestored()
                    return@launch
                }
                .onFailure { restoreError = "Couldn't restore: ${it.message ?: "unknown error"}. Nothing on this phone was changed." }
        }
        restoreStatus = null; busy = false
    }

    fun toggleCountry(code: String) { countries = if (code in countries) countries - code else countries + code }

    fun unlinkGoogle() { googleLinked = false; email = "" }

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (name.trim().length < 2) e["name"] = "Enter your name"
        val digits = phone.filter(Char::isDigit)
        if (digits.length !in 7..12) e["phone"] = "Enter a phone number with 7–12 digits"
        if (!googleLinked || email.isBlank()) e["email"] = "Connect your Google account to continue"
        if (countries.isEmpty()) e["countries"] = "Pick at least one country"
        if (!editing && groupMode == "join" && com.familydocs.app.domain.GroupCodes.normalize(groupCode).length < 6) e["group"] = "Enter the group's code"
        errors = e
        return e.isEmpty()
    }

    /** [cropBoxPx] = size of the crop circle on screen, so the saved photo is exactly what was shown. */
    fun finish(biometric: Boolean?, cropBoxPx: Float, onDone: () -> Unit) = viewModelScope.launch {
        busy = true
        if (restored) {
            pendingPasscode?.let { code -> withContext(Dispatchers.Default) { c.passcode.set(code) } }
            if (biometric != null) c.settings.update { it.copy(biometric = biometric) }
            c.appLock.unlock(); busy = false; onDone(); return@launch
        }
        // Joining with an invite someone sent: the group is theirs (read from their encrypted share), set up after the account.
        val invite = if (!editing && groupMode == "join") com.familydocs.app.domain.GroupInvite.find(groupCode) else null
        // Joining with a code: the group (and its documents) comes from this Google account's backup.
        if (!editing && groupMode == "join" && invite == null) {
            val s = session
            val err = when {
                s == null -> "Connect your Google account first"
                runCatching { c.backup.hasBackup(s.token) }.getOrDefault(false).not() -> "There's no Lararium backup in ${s.profile.email} to join from"
                else -> runCatching { c.backup.restore(s.token) }.exceptionOrNull()?.let { "Couldn't open the backup: ${it.message ?: "unknown error"}" }
                    ?: c.repo.groupByCode(groupCode).let { g -> if (g == null) "No group with this code in ${s.profile.email}'s backup" else { c.selectGroup(g.id); null } }
            }
            if (err != null) { errors = errors + ("group" to err); busy = false; return@launch }
            pendingPasscode?.let { code -> withContext(Dispatchers.Default) { c.passcode.set(code) } }
            if (biometric != null) c.settings.update { it.copy(biometric = biometric) }
            c.setBackups(true)
            c.appLock.unlock(); busy = false; onDone(); return@launch
        }
        if (!editing) c.selectGroup(invite?.groupId ?: c.repo.createGroup(groupName.ifBlank { "Family" }).id)
        val bytes = picked?.let { bmp -> withContext(Dispatchers.Default) { Images.jpeg(cropToSquare(bmp, crop, cropBoxPx)) } }
        if (!editing && !avatarChosen) com.familydocs.app.data.presetFor(name)?.let { avatar = it.avatar }
        c.repo.saveAccount(name.trim(), countryCode, phone.trim(), email.trim(), bytes, googleLinked, countries,
            removePhoto = removePhoto && bytes == null, avatar = avatar)
        // (if it can't be read right now, the group is still added and the next sync tries again)
        if (invite != null) runCatching { c.share.accept(session?.token, invite) }
        pendingPasscode?.let { code -> withContext(Dispatchers.Default) { c.passcode.set(code) } }
        if (biometric != null) c.settings.update { it.copy(biometric = biometric) }
        if (!editing && googleLinked) c.setBackups(true, now = true) // first backup straight away
        c.appLock.unlock()
        busy = false
        onDone()
    }
}
