package com.deniscerri.ytdl.ui.more

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.more.terminal.TerminalActivity
import com.deniscerri.ytdl.util.NavbarUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class MoreFragment : Fragment() {
    private lateinit var mainSharedPreferences: SharedPreferences
    private lateinit var mainSharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var terminal: TextView
    private lateinit var logs: TextView
    private lateinit var commandTemplates: TextView
    private lateinit var downloadQueue: TextView
    private lateinit var downloads: TextView
    private lateinit var cookies: TextView
    private lateinit var observeSources: TextView
    private lateinit var terminateApp: TextView
    private lateinit var settings: TextView
    private lateinit var mainActivity: MainActivity
    private lateinit var downloadViewModel: DownloadViewModel
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        return inflater.inflate(R.layout.fragment_more, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainSharedPreferences =  PreferenceManager.getDefaultSharedPreferences(requireContext())
        mainSharedPreferencesEditor = mainSharedPreferences.edit()
        terminal = view.findViewById(R.id.terminal)
        logs = view.findViewById(R.id.logs)
        commandTemplates = view.findViewById(R.id.command_templates)
        downloads = view.findViewById(R.id.downloads)
        downloadQueue = view.findViewById(R.id.download_queue)
        cookies = view.findViewById(R.id.cookies)
        observeSources = view.findViewById(R.id.observe_sources)
        terminateApp = view.findViewById(R.id.terminate)
        settings = view.findViewById(R.id.settings)

        var showingTerminal = false
        var showingDownloads = false
        var showingDownloadQueue = false

        NavbarUtil.getNavBarItems(requireContext()).apply {
            showingTerminal = any { n -> n.itemId == R.id.terminalActivity && n.isVisible }
            showingDownloads = any { n -> n.itemId == R.id.historyFragment && n.isVisible }
            showingDownloadQueue = any { n -> n.itemId == R.id.downloadQueueMainFragment && n.isVisible }
        }

        terminal.isVisible = !showingTerminal
        downloads.isVisible = !showingDownloads
        downloadQueue.isVisible = !showingDownloadQueue

        terminal.setOnClickListener {
            val intent = Intent(context, TerminalActivity::class.java)
            startActivity(intent)
        }

        logs.setOnClickListener {
            findNavController().navigate(R.id.downloadLogListFragment)
        }

        commandTemplates.setOnClickListener {
            findNavController().navigate(R.id.commandTemplatesFragment)
        }

        downloads.setOnClickListener {
            findNavController().navigate(R.id.historyFragment)
        }

        downloadQueue.setOnClickListener {
            findNavController().navigate(R.id.downloadQueueMainFragment)
        }

        cookies.setOnClickListener {
            findNavController().navigate(R.id.cookiesFragment)
        }

        observeSources.setOnClickListener {
            findNavController().navigate(R.id.observeSourcesFragment)
        }

        terminateApp.setOnClickListener {
            showTerminateConfirmationDialog()
        }
        terminateApp.setOnLongClickListener {
            showTerminateConfirmationDialog(skipPreference = true)
            true
        }

        settings.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
            startActivity(intent)
        }

    }

    fun showTerminateConfirmationDialog(skipPreference: Boolean = false) {
        val shouldAskToTerminate = mainSharedPreferences.getBoolean("ask_terminate_app", true)
        if (!shouldAskToTerminate && !skipPreference) {
            terminateApp.isEnabled = false
            terminateApp()
            return
        }

        var doNotShowAgainFinalState = !shouldAskToTerminate

        lateinit var dialog: AlertDialog
        val terminateDialog = MaterialAlertDialogBuilder(requireContext())
        terminateDialog.setTitle(getString(R.string.kill_app))
        val dialogView = layoutInflater.inflate(R.layout.dialog_terminate_app, null)
        val checkbox = dialogView.findViewById<CheckBox>(R.id.doNotShowAgain)
        terminateDialog.setView(dialogView)

        checkbox.isChecked = doNotShowAgainFinalState
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            doNotShowAgainFinalState = isChecked
        }

        terminateDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface, _ ->
            dialogInterface.cancel()
        }

        terminateDialog.setPositiveButton(getString(R.string.ok), null)
        dialog = terminateDialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.setCanceledOnTouchOutside(false)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
            mainSharedPreferencesEditor.putBoolean("ask_terminate_app", !doNotShowAgainFinalState).commit()
            terminateApp()
        }
    }

    fun terminateApp() {
        lifecycleScope.launch {
            downloadViewModel.pauseAllDownloads()
            mainActivity.finishAndRemoveTask()
            mainActivity.finishAffinity()
            exitProcess(0)
        }
    }

    companion object {
        const val TAG = "MoreFragment"
    }

}