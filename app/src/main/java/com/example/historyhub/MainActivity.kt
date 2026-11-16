package com.example.historyhub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.tabs.TabLayout
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    private lateinit var layoutSignedOut: View
    private lateinit var layoutSignedIn: View
    private lateinit var btnGoogleSignIn: MaterialButton
    private lateinit var btnSignOut: MaterialButton
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var progressBar: ProgressBar

    // HistoryHub components
    private lateinit var tvSelectedDate: TextView
    private lateinit var btnChangeDate: MaterialButton
    private lateinit var btnScanQR: MaterialButton
    private lateinit var btnViewBookmarks: MaterialButton
    private lateinit var tabLayout: TabLayout
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmptyState: TextView

    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var database: HistoryDatabase

    private var selectedMonth: String = "11"
    private var selectedDay: String = "16"
    private var currentFeedResponse: OnThisDayResponse? = null
    private var isShowingBookmarks: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase Auth and Credential Manager
        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)
        database = HistoryDatabase.getDatabase(this)

        // Initialize UI components
        layoutSignedOut = findViewById(R.id.layoutSignedOut)
        layoutSignedIn = findViewById(R.id.layoutSignedIn)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        btnSignOut = findViewById(R.id.btnSignOut)
        tvUserName = findViewById(R.id.tvUserName)
        tvUserEmail = findViewById(R.id.tvUserEmail)
        progressBar = findViewById(R.id.progressBar)

        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        btnChangeDate = findViewById(R.id.btnChangeDate)
        btnScanQR = findViewById(R.id.btnScanQR)
        btnViewBookmarks = findViewById(R.id.btnViewBookmarks)
        tabLayout = findViewById(R.id.tabLayout)
        rvHistory = findViewById(R.id.rvHistory)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        setupRecyclerView()
        setupListeners()
        setCurrentDateToToday()
    }

    override fun onStart() {
        super.onStart()
        val user = auth.currentUser
        updateUI(user)
        if (user != null) {
            scheduleNotificationAndPermissions()
            fetchHistoricalData()
        }
    }

    private fun setupRecyclerView() {
        rvHistory.layoutManager = LinearLayoutManager(this)
        historyAdapter = HistoryAdapter(
            items = emptyList(),
            onItemClick = { item ->
                if (!item.wikiUrl.isNullOrEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.wikiUrl))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open URL", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "No Wikipedia page available", Toast.LENGTH_SHORT).show()
                }
            },
            onBookmarkClick = { item ->
                toggleBookmark(item)
            }
        )
        rvHistory.adapter = historyAdapter
    }

    private fun setupListeners() {
        btnGoogleSignIn.setOnClickListener { performGoogleSignIn() }
        btnSignOut.setOnClickListener { performSignOut() }
        btnChangeDate.setOnClickListener { openDatePicker() }
        btnScanQR.setOnClickListener { startQRScanner() }
        btnViewBookmarks.setOnClickListener { toggleBookmarksView() }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (!isShowingBookmarks) {
                    displayCurrentTabData()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setCurrentDateToToday() {
        val calendar = Calendar.getInstance()
        selectedMonth = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        selectedDay = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))
        updateDateDisplay()
    }

    private fun updateDateDisplay() {
        tvSelectedDate.text = getString(R.string.selected_date_label, "$selectedMonth-$selectedDay")
    }

    private fun scheduleNotificationAndPermissions() {
        NotificationWorker.scheduleDailyNotification(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    // --- Retrofit API Data Fetching ---
    private fun fetchHistoricalData() {
        if (isShowingBookmarks) {
            isShowingBookmarks = false
            btnViewBookmarks.text = getString(R.string.view_bookmarks)
            tabLayout.visibility = View.VISIBLE
        }
        showLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = HistoryRetrofitInstance.api.getOnThisDay("all", selectedMonth, selectedDay)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    currentFeedResponse = response
                    displayCurrentTabData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    tvEmptyState.visibility = View.VISIBLE
                    tvEmptyState.text = getString(R.string.sign_in_failed, e.localizedMessage ?: "Failed to fetch data")
                    historyAdapter.updateData(emptyList())
                }
            }
        }
    }

    private fun displayCurrentTabData() {
        val response = currentFeedResponse ?: return
        val rawItems = when (tabLayout.selectedTabPosition) {
            0 -> response.events ?: emptyList()
            1 -> response.births ?: emptyList()
            2 -> response.deaths ?: emptyList()
            else -> response.events ?: emptyList()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val bookmarkedUrls = database.historyDao().getAllBookmarks().map { it.wikiUrl }.toSet()
            val displayItems = rawItems.map { item ->
                val wikiUrl = item.getWikiUrl() ?: ""
                DisplayHistoryItem(
                    year = item.year ?: 0,
                    text = item.text ?: "",
                    thumbnailUrl = item.getThumbnailUrl(),
                    wikiUrl = wikiUrl,
                    isBookmarked = wikiUrl.isNotEmpty() && bookmarkedUrls.contains(wikiUrl)
                )
            }
            withContext(Dispatchers.Main) {
                if (displayItems.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    tvEmptyState.text = getString(R.string.no_events_found)
                } else {
                    tvEmptyState.visibility = View.GONE
                }
                historyAdapter.updateData(displayItems)
            }
        }
    }

    // --- MaterialDatePicker Integration ---
    private fun openDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selectionMillis ->
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = selectionMillis
            selectedMonth = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
            selectedDay = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))
            updateDateDisplay()
            fetchHistoricalData()
        }
        datePicker.show(supportFragmentManager, "MATERIAL_DATE_PICKER")
    }

    // --- Google Code Scanner Integration ---
    private fun startQRScanner() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        val scanner = GmsBarcodeScanning.getClient(this, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue
                if (!rawValue.isNullOrEmpty() && isValidDateMMDD(rawValue)) {
                    val parts = rawValue.split("-")
                    selectedMonth = parts[0]
                    selectedDay = parts[1]
                    updateDateDisplay()
                    fetchHistoricalData()
                    Toast.makeText(this, "Date updated to $rawValue", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid QR code format. Expected MM-DD", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Scan failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun isValidDateMMDD(dateStr: String): Boolean {
        val parts = dateStr.trim().split("-")
        if (parts.size != 2) return false
        val mm = parts[0].toIntOrNull() ?: return false
        val dd = parts[1].toIntOrNull() ?: return false
        if (mm !in 1..12) return false
        val maxDays = when (mm) {
            2 -> 29 // Allow 29 for Feb (leap year capability)
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return dd in 1..maxDays
    }

    // --- Room Database Bookmarks ---
    private fun toggleBookmarksView() {
        if (isShowingBookmarks) {
            isShowingBookmarks = false
            btnViewBookmarks.text = getString(R.string.view_bookmarks)
            tabLayout.visibility = View.VISIBLE
            displayCurrentTabData()
        } else {
            isShowingBookmarks = true
            btnViewBookmarks.text = getString(R.string.show_history)
            tabLayout.visibility = View.GONE
            loadBookmarksFromDatabase()
        }
    }

    private fun loadBookmarksFromDatabase() {
        showLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val bookmarks = database.historyDao().getAllBookmarks()
            val displayItems = bookmarks.map {
                DisplayHistoryItem(
                    year = it.year,
                    text = it.text,
                    thumbnailUrl = it.thumbnailUrl,
                    wikiUrl = it.wikiUrl,
                    isBookmarked = true
                )
            }
            withContext(Dispatchers.Main) {
                showLoading(false)
                if (displayItems.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    tvEmptyState.text = "No saved bookmarks."
                } else {
                    tvEmptyState.visibility = View.GONE
                }
                historyAdapter.updateData(displayItems)
            }
        }
    }

    private fun toggleBookmark(item: DisplayHistoryItem) {
        if (item.wikiUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Cannot bookmark item without Wikipedia link", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = database.historyDao()
            val isBookmarked = dao.isBookmarked(item.wikiUrl)
            if (isBookmarked) {
                dao.deleteByWikiUrl(item.wikiUrl)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
                    if (isShowingBookmarks) {
                        loadBookmarksFromDatabase()
                    } else {
                        displayCurrentTabData()
                    }
                }
            } else {
                val entity = HistoryEntity(
                    wikiUrl = item.wikiUrl,
                    year = item.year,
                    text = item.text,
                    thumbnailUrl = item.thumbnailUrl,
                    type = "event"
                )
                dao.insertBookmark(entity)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
                    displayCurrentTabData()
                }
            }
        }
    }

    // --- Firebase Auth & Credential Manager Sign-In ---
    private fun performGoogleSignIn() {
        showLoading(true)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@MainActivity
                )
                handleSignInResult(result)
            } catch (_: GetCredentialCancellationException) {
                showLoading(false)
                Toast.makeText(this@MainActivity, getString(R.string.sign_in_cancelled), Toast.LENGTH_SHORT).show()
            } catch (e: GetCredentialException) {
                showLoading(false)
                Toast.makeText(this@MainActivity, getString(R.string.sign_in_failed, e.localizedMessage ?: "Unknown error"), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@MainActivity, getString(R.string.sign_in_failed, e.localizedMessage ?: "Unknown error"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSignInResult(result: GetCredentialResponse) {
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                firebaseAuthWithGoogle(idToken)
            } catch (e: GoogleIdTokenParsingException) {
                showLoading(false)
                Toast.makeText(this, getString(R.string.sign_in_failed, "Invalid ID token"), Toast.LENGTH_SHORT).show()
            }
        } else {
            showLoading(false)
            Toast.makeText(this, getString(R.string.sign_in_failed, "Unrecognized credential type"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val authCredential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(authCredential)
            .addOnCompleteListener(this) { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    Toast.makeText(this, getString(R.string.signed_in_success), Toast.LENGTH_SHORT).show()
                    updateUI(auth.currentUser)
                    fetchHistoricalData()
                } else {
                    val errorMessage = task.exception?.localizedMessage ?: "Authentication failed"
                    Toast.makeText(this, getString(R.string.sign_in_failed, errorMessage), Toast.LENGTH_SHORT).show()
                    updateUI(null)
                }
            }
    }

    private fun performSignOut() {
        auth.signOut()
        lifecycleScope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
                // Ignore clearing errors during sign out
            }
        }
        updateUI(null)
        Toast.makeText(this, getString(R.string.signed_out_success), Toast.LENGTH_SHORT).show()
    }

    private fun updateUI(user: FirebaseUser?) {
        showLoading(false)
        if (user != null) {
            tvUserName.text = user.displayName ?: "No Name"
            tvUserEmail.text = user.email ?: "No Email"
            layoutSignedOut.visibility = View.GONE
            layoutSignedIn.visibility = View.VISIBLE
        } else {
            layoutSignedOut.visibility = View.VISIBLE
            layoutSignedIn.visibility = View.GONE
        }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
        }
    }
}