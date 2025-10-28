package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager.SORTING
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.DownloadItemConfigureMultiple
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.FormatViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.receiver.ShareActivity
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.adapter.ConfigureMultipleDownloadsAdapter
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class DownloadMultipleBottomSheetDialog : BottomSheetDialogFragment(), ConfigureMultipleDownloadsAdapter.OnItemClickListener, View.OnClickListener,
    ConfigureDownloadBottomSheetDialog.OnDownloadItemUpdateListener {
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var formatViewModel: FormatViewModel
    private lateinit var listAdapter : ConfigureMultipleDownloadsAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var bottomAppBar: BottomAppBar
    private lateinit var filesize : TextView
    private var itemsFileSize: Long = 0L
    private lateinit var count : TextView
    private lateinit var downloadBtn : MaterialButton
    private lateinit var scheduleBtn : MaterialButton
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var shimmerTitle: ShimmerFrameLayout
    private lateinit var shimmerSubtitle: ShimmerFrameLayout
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var parentActivity: BaseActivity

    private var processingDownloadIDs: List<Long> = listOf()
    private lateinit var currentDownloadIDs: List<Long>
    private lateinit var currentHistoryIDs: List<Long>
    private var ignoreDuplicates: Boolean = false
    private var processingItemsCount : Int = 0

    private lateinit var formatBtn : MenuItem
    private lateinit var moreBtn : MenuItem
    private lateinit var containerBtn : MenuItem
    private lateinit var containerTextView: TextView

    private lateinit var multipleSelectHeader: ConstraintLayout
    private lateinit var selectItemsMenuBtn: MaterialButton
    private lateinit var selectRangeBtn: MaterialButton
    private lateinit var selectItemsOpenBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        historyViewModel = ViewModelProvider(requireActivity())[HistoryViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        formatViewModel = ViewModelProvider(requireActivity())[FormatViewModel::class.java]
        commandTemplateViewModel = ViewModelProvider(requireActivity())[CommandTemplateViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        currentDownloadIDs = arguments?.getLongArray("currentDownloadIDs")?.toList() ?: listOf()
        currentHistoryIDs = arguments?.getLongArray("currentHistoryIDs")?.toList() ?: listOf()
        ignoreDuplicates = arguments?.getBoolean("ignore_duplicates") == true
        processingItemsCount = currentDownloadIDs.size
    }

    @SuppressLint("RestrictedApi", "NotifyDataSetChanged")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.download_multiple_bottom_sheet, null)
        dialog.setContentView(view)
        dialog.window?.navigationBarColor = SurfaceColors.SURFACE_1.getColor(requireActivity())
        parentActivity = activity as BaseActivity

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            if(resources.getBoolean(R.bool.isTablet) || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE){
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = displayMetrics.heightPixels
            }
        }

        listAdapter =
            ConfigureMultipleDownloadsAdapter(
                this,
                requireActivity()
            )

        recyclerView = view.findViewById(R.id.downloadMultipleRecyclerview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = listAdapter
        recyclerView.enableFastScroll()


        view.findViewById<TextView>(R.id.bottom_sheet_title)?.apply {
            setOnClickListener {
                recyclerView.scrollToPosition(0)
            }
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getStringSet("swipe_gesture", requireContext().getStringArray(R.array.swipe_gestures_values).toSet())!!.toList().contains("multipledownloadcard")){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }

        scheduleBtn = view.findViewById(R.id.bottomsheet_schedule_button)
        downloadBtn = view.findViewById(R.id.bottomsheet_download_button)
        bottomAppBar = view.findViewById(R.id.bottomAppBar)

        val preferredDownloadType = bottomAppBar.menu.findItem(R.id.preferred_download_type)
        formatBtn = bottomAppBar.menu.findItem(R.id.format)
        moreBtn = bottomAppBar.menu.findItem(R.id.more)
        containerBtn = bottomAppBar.menu.findItem(R.id.container)
        containerTextView = containerBtn.actionView as TextView
        val incognitoBtn = bottomAppBar.menu.findItem(R.id.incognito)

        filesize = view.findViewById(R.id.filesize)
        count = view.findViewById(R.id.count)

        title = view.findViewById(R.id.bottom_sheet_title)
        subtitle = view.findViewById(R.id.bottom_sheet_subtitle)
        shimmerTitle = view.findViewById(R.id.shimmer_loading_title)
        shimmerSubtitle = view.findViewById(R.id.shimmer_loading_subtitle)

        lifecycleScope.launch {
            downloadViewModel.processingItems.collectLatest {
                toggleLoading(it)
            }
        }

        lifecycleScope.launch {
            formatViewModel.noFreeSpace.collectLatest {
                if (it != null) {
                    val snack = Snackbar.make(view, it, Snackbar.LENGTH_INDEFINITE)
                    snack.setTextMaxLines(10)
                    snack.show()
                }
            }
        }

        lifecycleScope.launch {
            downloadViewModel.processingDownloads.collectLatest { items ->
                processingItemsCount = items.size
                processingDownloadIDs = items.map { it.id }
                count.text = "$processingItemsCount ${getString(R.string.selected)}"
                listAdapter.submitList(items)

                updateFileSize(items)

                if (items.isNotEmpty()){
                    val checkedItems = listAdapter.getCheckedItemsOrNull() ?: listOf()
                    val firstItem = if (checkedItems.isNotEmpty()) {
                        items.firstOrNull { it.id == checkedItems.first() } ?: items.first()
                    }else {
                        items.first()
                    }
                    updateBottomAppBarMenuItemsVisibility(firstItem)
                    val type = items.first().type
                    when(type){
                        DownloadViewModel.Type.audio -> {
                            preferredDownloadType.setIcon(R.drawable.baseline_audio_file_24)
                        }
                        DownloadViewModel.Type.video -> {
                            preferredDownloadType.setIcon(R.drawable.baseline_video_file_24)
                        }
                        DownloadViewModel.Type.command -> {
                            preferredDownloadType.setIcon(R.drawable.baseline_insert_drive_file_24)
                            setContainerText("")
                        }

                        else -> {}
                    }

                }
            }
        }


        scheduleBtn.setOnClickListener{
            UiUtil.showDatePicker(parentFragmentManager, preferences) { cal ->
                toggleLoading(true)
                lifecycleScope.launch {
                    withContext(Dispatchers.IO){
                        downloadViewModel.deleteAllWithID(currentDownloadIDs)
                        historyViewModel.deleteAllWithIDsCheckFiles(currentHistoryIDs)
                        val result = downloadViewModel.updateProcessingDownloadTimeAndQueueScheduled(cal.timeInMillis, ignoreDuplicates)
                        if (result.message.isNotBlank()){
                            lifecycleScope.launch {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }

                        withContext(Dispatchers.Main){
                            handleDuplicatesAndDismiss(result.duplicateDownloadIDs)
                            dismiss()
                        }
                    }
                }
            }
        }

        downloadBtn.setOnClickListener {
            toggleLoading(true)
            lifecycleScope.launch {
                withContext(Dispatchers.IO){
                    downloadViewModel.deleteAllWithID(currentDownloadIDs)
                    historyViewModel.deleteAllWithIDsCheckFiles(currentHistoryIDs)
                    val result = downloadViewModel.queueProcessingDownloads(ignoreDuplicates)
                    if (result.message.isNotBlank()){
                        lifecycleScope.launch {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    withContext(Dispatchers.Main){
                        handleDuplicatesAndDismiss(result.duplicateDownloadIDs)
                        dismiss()
                    }
                }
            }
        }

        downloadBtn.setOnLongClickListener {
            val dd = MaterialAlertDialogBuilder(requireContext())
            dd.setTitle(getString(R.string.save_for_later))
            dd.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
            dd.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch{
                    withContext(Dispatchers.IO){
                        downloadViewModel.moveProcessingToSavedCategory()
                        downloadViewModel.deleteAllWithID(currentDownloadIDs)
                        historyViewModel.deleteAllWithIDsCheckFiles(currentHistoryIDs)
                    }

                    downloadViewModel.processingItemsJob?.cancel(CancellationException())
                    downloadViewModel.processingItemsJob = null

                    dismiss()
                }
            }
            dd.show()
            true
        }

        val formatListener = object : OnMultipleFormatClickListener {
            override fun onFormatClick(formatTuple: List<MultipleItemFormatTuple>) {
                downloadViewModel.updateAllProcessingFormats(listAdapter.getCheckedItemsOrNull(), formatTuple)
            }

            override fun onFormatUpdated(url: String, formats: List<Format>) {
                downloadViewModel.updateProcessingFormatByUrl(url, formats)
            }

            override fun onItemUnavailable(url: String) {
                downloadViewModel.removeUnavailableDownloadAndResultByURL(url)
            }

            override fun onContinueOnBackground() {
                requireActivity().lifecycleScope.launch {
                    withContext(Dispatchers.IO){
                        downloadViewModel.continueUpdatingFormatsOnBackground(listAdapter.getCheckedItemsOrNull())
                    }
                    downloadViewModel.processingItemsJob?.cancel(CancellationException())
                    downloadViewModel.processingItemsJob = null

                    dismiss()
                }
            }
        }

        lifecycleScope.launch {
            val allIncognito = withContext(Dispatchers.IO){
                downloadViewModel.areAllProcessingIncognito(listAdapter.getCheckedItemsOrNull())
            }

            incognitoBtn.icon!!.apply {
                alpha = if (allIncognito){
                    255
                }else{
                    30
                }
            }

        }

        lifecycleScope.launch {
            launch{
                downloadViewModel.alreadyExistsUiState.collectLatest { res ->
                    if (res.isNotEmpty() && activity is ShareActivity){
                        withContext(Dispatchers.Main){
                            val bundle = bundleOf(
                                Pair("duplicates", ArrayList(res))
                            )
                            delay(500)
                            findNavController().navigate(R.id.action_downloadMultipleBottomSheetDialog_to_downloadsAlreadyExistDialog2, bundle)
                        }
                        downloadViewModel.alreadyExistsUiState.value = mutableListOf()
                    }
                }
            }
        }


        bottomAppBar.setOnMenuItemClickListener { m: MenuItem ->
            when (m.itemId) {
                R.id.preferred_download_type -> {
                    lifecycleScope.launch{
                        val bottomSheet = BottomSheetDialog(requireContext())
                        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
                        bottomSheet.setContentView(R.layout.download_type_sheet)

                        // BUTTON ----------------------------------
                        val audio = bottomSheet.findViewById<TextView>(R.id.audio)
                        val video = bottomSheet.findViewById<TextView>(R.id.video)
                        val command = bottomSheet.findViewById<TextView>(R.id.command)


                        withContext(Dispatchers.IO){
                            val nr = commandTemplateViewModel.getTotalNumber()
                            if(nr == 0){
                                command!!.visibility = View.GONE
                            }else{
                                command!!.visibility = View.VISIBLE
                            }
                        }

                        audio!!.setOnClickListener {
                            CoroutineScope(Dispatchers.IO).launch {
                                downloadViewModel.updateProcessingType(listAdapter.getCheckedItemsOrNull(), DownloadViewModel.Type.audio)
                                withContext(Dispatchers.Main){
                                    bottomSheet.cancel()
                                }
                            }
                        }

                        video!!.setOnClickListener {
                            CoroutineScope(Dispatchers.IO).launch{
                                downloadViewModel.updateProcessingType(listAdapter.getCheckedItemsOrNull(), DownloadViewModel.Type.video)
                                withContext(Dispatchers.Main){
                                    bottomSheet.cancel()
                                }
                            }
                        }

                        command!!.setOnClickListener {
                            CoroutineScope(Dispatchers.IO).launch{
                                downloadViewModel.updateProcessingType(listAdapter.getCheckedItemsOrNull(), DownloadViewModel.Type.command)
                                withContext(Dispatchers.Main){
                                    bottomSheet.cancel()
                                }

                            }
                        }

                        bottomSheet.show()
                        val displayMetrics = DisplayMetrics()
                        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
                        bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
                        bottomSheet.window!!.setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                }
                R.id.format -> {
                    lifecycleScope.launch {
                        val res = withContext(Dispatchers.IO){
                            downloadViewModel.checkIfAllProcessingItemsHaveSameType(listAdapter.getCheckedItemsOrNull())
                        }
                        if (!res.first){
                            Toast.makeText(requireContext(), getString(R.string.format_filtering_hint), Toast.LENGTH_SHORT).show()
                        }else{

                            if (res.second == DownloadViewModel.Type.command){
                                UiUtil.showCommandTemplates(requireActivity(), commandTemplateViewModel) {
                                    val format  = Format(
                                        it.first().title,
                                        "",
                                        "",
                                        "",
                                        "",
                                        0,
                                        it.joinToString(" ") { c -> c.content }
                                    )

                                    lifecycleScope.launch {
                                        withContext(Dispatchers.IO){
                                            downloadViewModel.updateProcessingCommandFormat(listAdapter.getCheckedItemsOrNull(), format)
                                        }
                                    }
                                }
                            }else{
                                val items = withContext(Dispatchers.IO){
                                    downloadViewModel.getProcessingDownloads(listAdapter.getCheckedItemsOrNull())
                                }
                                formatViewModel.setItems(items)
                                val bottomSheet = FormatSelectionBottomSheetDialog( _multipleFormatsListener = formatListener)
                                bottomSheet.show(parentFragmentManager, "formatSheet")
                            }
                        }
                    }
                }
                R.id.folder -> {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    pathResultLauncher.launch(intent)
                }
                R.id.incognito -> {
                    lifecycleScope.launch {
                        if (m.icon!!.alpha == 255) {
                            incognitoBtn.isEnabled = false
                            withContext(Dispatchers.IO) {
                                downloadViewModel.updateProcessingIncognito(listAdapter.getCheckedItemsOrNull(), false)
                                withContext(Dispatchers.Main){
                                    m.icon!!.alpha = 30
                                    m.isEnabled = true
                                }
                            }
                            incognitoBtn.icon?.alpha = 30
                            incognitoBtn.isEnabled = true
                            Toast.makeText(requireContext(), "${getString(R.string.incognito)}: ${getString(R.string.disabled)}", Toast.LENGTH_SHORT).show()
                        }else{
                            incognitoBtn.isEnabled = false
                            withContext(Dispatchers.IO) {
                                downloadViewModel.updateProcessingIncognito(listAdapter.getCheckedItemsOrNull(),true)
                                withContext(Dispatchers.Main){
                                }
                            }
                            incognitoBtn.icon?.alpha = 255
                            incognitoBtn.isEnabled = true
                            Toast.makeText(requireContext(), "${getString(R.string.incognito)}: ${getString(R.string.ok)}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.more -> {
                    lifecycleScope.launch {
                        val res = withContext(Dispatchers.IO){
                            downloadViewModel.checkIfAllProcessingItemsHaveSameType(listAdapter.getCheckedItemsOrNull())
                        }
                        if (!res.first) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.format_filtering_hint),
                                Toast.LENGTH_SHORT
                            ).show()
                        }else{
                            val scale = resources.displayMetrics.density
                            val padding = (40*scale*0.5f).toInt()

                            when(res.second){
                                DownloadViewModel.Type.audio -> {
                                    val bottomSheet = BottomSheetDialog(requireContext())
                                    bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
                                    bottomSheet.setContentView(R.layout.adjust_audio)
                                    val sheetView = bottomSheet.findViewById<View>(android.R.id.content)!!
                                    sheetView.findViewById<View>(R.id.adjust).setPadding(padding)

                                    val items = withContext(Dispatchers.IO){
                                        downloadViewModel.getProcessingDownloads(listAdapter.getCheckedItemsOrNull())
                                    }

                                    UiUtil.configureAudio(
                                        sheetView,
                                        requireActivity(),
                                        items,
                                        embedThumbClicked = {enabled ->
                                            items.forEach {
                                                it.audioPreferences.embedThumb = enabled
                                            }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        cropThumbClicked = {enabled ->
                                            items.forEach {
                                                it.audioPreferences.cropThumb = enabled
                                            }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        splitByChaptersClicked = {enabled ->
                                            items.forEach {
                                                it.audioPreferences.splitByChapters = enabled
                                            }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        bitrateSet = { bitrate ->
                                            items.forEach {
                                                it.audioPreferences.bitrate = bitrate
                                            }
                                            requireActivity().lifecycleScope.launch {
                                                items.forEach { downloadViewModel.updateDownload(it) }
                                            }
                                            bottomSheet.dismiss()
                                        },
                                        filenameTemplateSet = {template ->
                                            items.forEach {
                                                it.customFileNameTemplate = template
                                            }
                                            requireActivity().lifecycleScope.launch {
                                                items.forEach { downloadViewModel.updateDownload(it) }
                                            }
                                            bottomSheet.dismiss()
                                        },
                                        sponsorBlockItemsSet = { values, checkedItems ->
                                            items.forEach { it.audioPreferences.sponsorBlockFilters.clear() }
                                            for (i in checkedItems.indices) {
                                                if (checkedItems[i]) {
                                                    items.forEach { it.audioPreferences.sponsorBlockFilters.add(values[i]) }
                                                }
                                            }
                                            requireActivity().lifecycleScope.launch {
                                                items.forEach { downloadViewModel.updateDownload(it) }
                                            }
                                            bottomSheet.dismiss()
                                        },
                                        cutClicked = {},
                                        cutDisabledClicked = {},
                                        cutValueChanged = {},
                                        extraCommandsClicked = { returnValue ->
                                            val callback = object : ExtraCommandsListener {
                                                override fun onChangeExtraCommand(c: String) {
                                                    items.forEach { it.extraCommands = c }
                                                    requireActivity().lifecycleScope.launch {
                                                        items.forEach { downloadViewModel.updateDownload(it) }
                                                    }
                                                    returnValue(c)
                                                    bottomSheet.dismiss()
                                                }
                                            }

                                            val bottomSheetDialog = AddExtraCommandsDialog(null, callback)
                                            bottomSheetDialog.show(parentFragmentManager, "extraCommands")
                                        }
                                    )
                                    bottomSheet.show()
                                    val displayMetrics = DisplayMetrics()
                                    requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
                                    bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
                                    bottomSheet.window!!.setLayout(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )

                                }
                                DownloadViewModel.Type.video -> {
                                    val bottomSheet = BottomSheetDialog(requireContext())
                                    bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
                                    bottomSheet.setContentView(R.layout.adjust_video)
                                    val sheetView = bottomSheet.findViewById<View>(android.R.id.content)!!
                                    sheetView.findViewById<View>(R.id.adjust).setPadding(padding)

                                    val items = withContext(Dispatchers.IO){
                                        downloadViewModel.getProcessingDownloads(listAdapter.getCheckedItemsOrNull())
                                    }

                                    UiUtil.configureVideo(
                                        sheetView,
                                        requireActivity(),
                                        items,
                                        embedSubsClicked = {checked ->
                                            items.forEach { it.videoPreferences.embedSubs = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        addChaptersClicked = {checked ->
                                            items.forEach { it.videoPreferences.addChapters = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        splitByChaptersClicked = { checked ->
                                            items.forEach { it.videoPreferences.splitByChapters = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        embedThumbnailClicked = {checked ->
                                            items.forEach { it.videoPreferences.embedThumbnail = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        saveThumbnailClicked = {checked ->
                                            items.forEach { it.SaveThumb = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        sponsorBlockItemsSet = { values, checkedItems ->
                                            items.forEach { it.videoPreferences.sponsorBlockFilters.clear() }
                                            for (i in checkedItems.indices) {
                                                if (checkedItems[i]) {
                                                    items.forEach { it.videoPreferences.sponsorBlockFilters.add(values[i]) }
                                                }
                                            }
                                            requireActivity().lifecycleScope.launch {
                                                items.forEach { downloadViewModel.updateDownload(it) }
                                            }
                                            bottomSheet.dismiss()
                                        },
                                        cutClicked = {},
                                        cutDisabledClicked = {},
                                        cutValueChanged = {},
                                        filenameTemplateSet = { checked ->
                                            items.forEach { it.customFileNameTemplate = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        saveSubtitlesClicked = {checked ->
                                            items.forEach { it.videoPreferences.writeSubs = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        saveAutoSubtitlesClicked = {checked ->
                                            items.forEach { it.videoPreferences.writeAutoSubs = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        subtitleLanguagesSet = {value ->
                                            items.forEach { it.videoPreferences.subsLanguages = value }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        removeAudioClicked = {checked ->
                                            items.forEach { it.videoPreferences.removeAudio = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        recodeVideoClicked = {checked ->
                                            items.forEach { it.videoPreferences.recodeVideo = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        compatibilityModeClicked = { checked ->
                                            items.forEach {
                                                it.videoPreferences.compatibilityMode = checked
                                                if(checked) {
                                                    it.container = "mkv"
                                                }
                                            }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        alsoDownloadAsAudioClicked = {},
                                        extraCommandsClicked = { returnValue ->
                                            val callback = object : ExtraCommandsListener {
                                                override fun onChangeExtraCommand(c: String) {
                                                    items.forEach { it.extraCommands = c }
                                                    CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                                    returnValue(c)
                                                    bottomSheet.dismiss()
                                                }
                                            }

                                            val bottomSheetDialog = AddExtraCommandsDialog(null, callback)
                                            bottomSheetDialog.show(parentFragmentManager, "extraCommands")
                                        },
                                        waitForVideo = {b, i -> },
                                        liveFromStart = {}
                                    )

                                    bottomSheet.show()
                                    val displayMetrics = DisplayMetrics()
                                    requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
                                    bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
                                    bottomSheet.window!!.setLayout(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                                DownloadViewModel.Type.command -> {
                                    val bottomSheet = BottomSheetDialog(requireContext())
                                    bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
                                    bottomSheet.setContentView(R.layout.adjust_command)
                                    val sheetView = bottomSheet.findViewById<View>(android.R.id.content)!!
                                    sheetView.findViewById<View>(R.id.adjust).setPadding(padding)

                                    val items = withContext(Dispatchers.IO){
                                        downloadViewModel.getProcessingDownloads(listAdapter.getCheckedItemsOrNull())
                                    }

                                    UiUtil.configureCommand(
                                        sheetView,
                                        1,
                                        0,
                                        newTemplateClicked = {
                                            UiUtil.showCommandTemplateCreationOrUpdatingSheet(
                                                null, requireActivity(), sheetView.findViewTreeLifecycleOwner()!!, commandTemplateViewModel,
                                                newTemplate = { nt ->
                                                    items.forEach { it2 -> it2.format = Format(
                                                        nt.title,
                                                        "",
                                                        "",
                                                        "",
                                                        "",
                                                        0,
                                                        nt.content
                                                    ) }
                                                    CoroutineScope(Dispatchers.IO).launch {
                                                        commandTemplateViewModel.insert(nt)
                                                        items.forEach { downloadViewModel.updateDownload(it) }
                                                    }
                                                    preferences.edit().putString("lastCommandTemplateUsed", nt.content).apply()
                                                    bottomSheet.dismiss()
                                                },
                                                dismissed = {}
                                            )
                                        },
                                        editSelectedClicked = {
                                            val current = CommandTemplate(
                                                0,
                                                "",
                                                items[0].format.format_note,
                                                useAsExtraCommand = false, useAsExtraCommandAudio = false, useAsExtraCommandVideo = false, useAsExtraCommandDataFetching = false
                                            )

                                            UiUtil.showCommandTemplateCreationOrUpdatingSheet(
                                                current, requireActivity(), sheetView.findViewTreeLifecycleOwner()!!, commandTemplateViewModel,
                                                newTemplate = { nt ->
                                                    items.forEach { it2 -> it2.format = Format(
                                                        nt.title,
                                                        "",
                                                        "",
                                                        "",
                                                        "",
                                                        0,
                                                        nt.content
                                                    ) }
                                                    CoroutineScope(Dispatchers.IO).launch {
                                                        commandTemplateViewModel.insert(nt)
                                                        items.forEach { downloadViewModel.updateDownload(it) }
                                                    }
                                                    preferences.edit().putString("lastCommandTemplateUsed", nt.content).apply()
                                                    bottomSheet.dismiss()
                                                },
                                                dismissed = {}
                                            )
                                        },
                                        shortcutClicked = {}
                                    )

                                    bottomSheet.show()
                                    val displayMetrics = DisplayMetrics()
                                    requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
                                    bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
                                    bottomSheet.window!!.setLayout(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }

                                else -> {}
                            }
                        }
                    }


                }
            }
            true
        }

        containerTextView.setOnClickListener {
            lifecycleScope.launch {
                val res = withContext(Dispatchers.IO){
                    downloadViewModel.checkIfAllProcessingItemsHaveSameType(listAdapter.getCheckedItemsOrNull())
                }
                if (!res.first){
                    Toast.makeText(requireContext(), getString(R.string.format_filtering_hint), Toast.LENGTH_SHORT).show()
                }else{
                    val popup = PopupMenu(activity, containerTextView)
                    when(res.second) {
                        DownloadViewModel.Type.audio -> resources.getStringArray(R.array.audio_containers)
                        //video
                        else -> resources.getStringArray(R.array.video_containers)
                    }.forEach {
                        popup.menu.add(it)
                    }

                    popup.setOnMenuItemClickListener { mm ->
                        val container = mm.title
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO){
                                downloadViewModel.updateProcessingContainer(listAdapter.getCheckedItemsOrNull(), container.toString())
                            }
                            if (container == "gif") {
                                val items = withContext(Dispatchers.IO){
                                    downloadViewModel.getProcessingDownloads(listAdapter.getCheckedItemsOrNull())
                                }
                                items.forEach {
                                    it.videoPreferences.removeAudio = true
                                    it.videoPreferences.recodeVideo = true
                                }
                                CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                            }
                        }
                        setContainerText(container.toString())
                        true
                    }

                    popup.show()
                }
            }
        }

        selectItemsOpenBtn = view.findViewById(R.id.selectItemsOpenBtn)
        selectItemsOpenBtn.setOnClickListener {
            multipleSelectHeader.apply {
                isVisible = true
            }
            view.findViewById<ConstraintLayout>(R.id.downloadHeader).isVisible = false
            filesize.isVisible = false
            listAdapter.initCheckingItems(processingDownloadIDs)
            selectRangeBtn.isVisible = true
            count.text = "0 ${getString(R.string.selected)}"
            selectItemsOpenBtn.isVisible = false
        }

        multipleSelectHeader = view.findViewById(R.id.multipleSelectHeader)
        selectItemsMenuBtn = view.findViewById(R.id.selectItemsMenu)
        selectItemsMenuBtn.setOnClickListener {
            val popup = PopupMenu(activity, it)
            popup.menuInflater.inflate(R.menu.select_multiple_items_menu_context, popup.menu)
            if (Build.VERSION.SDK_INT > 27) popup.menu.setGroupDividerEnabled(true)

            val selectedItems = listAdapter.getCheckedItemsOrNull() ?: listOf()
            popup.menu.findItem(R.id.delete).isVisible = selectedItems.isNotEmpty()
            popup.menu.findItem(R.id.select_between).apply {
                if (selectedItems.size == 2) {
                    val firstIndex = processingDownloadIDs.indexOf(selectedItems.first())
                    val secondIndex = processingDownloadIDs.indexOf(selectedItems.last())

                    isVisible = abs(firstIndex - secondIndex) > 1
                }else{
                    isVisible = false
                }
            }

            popup.setOnMenuItemClickListener { m: MenuItem ->
                when(m.itemId) {
                    R.id.select_between -> {
                        val firstIndex = processingDownloadIDs.indexOf(selectedItems.first())
                        val secondIndex = processingDownloadIDs.indexOf(selectedItems.last())

                        val itemsBetween = processingDownloadIDs.filterIndexed { index, _ -> index >= firstIndex && index <= secondIndex }
                        listAdapter.selectItems(itemsBetween)
                        count.text = "${itemsBetween.size} ${getString(R.string.selected)}"
                    }
                    R.id.delete -> {
                        UiUtil.showGenericDeleteAllDialog(requireContext()) {
                            lifecycleScope.launch {
                                val deletedAll = processingItemsCount == listAdapter.getCheckedItemsSize()
                                val toDelete = listAdapter.getCheckedItemsOrNull() ?: listOf()
                                withContext(Dispatchers.IO) {
                                    downloadViewModel.deleteAllWithID(toDelete)
                                }
                                listAdapter.removeItemsFromCheckList(toDelete)
                                val checkedSize = listAdapter.getCheckedItemsSize()
                                count.text = "$checkedSize ${getString(R.string.selected)}"
                                if (deletedAll) dismiss()
                            }
                        }
                    }
                    R.id.select_all -> {
                        listAdapter.checkAll()
                        val checkedSize = listAdapter.getCheckedItemsSize()
                        count.text = "$checkedSize ${getString(R.string.selected)}"
                    }
                    R.id.invert_selected -> {
                        listAdapter.invertSelected()
                        val checkedSize = listAdapter.getCheckedItemsSize()
                        count.text = "$checkedSize ${getString(R.string.selected)}"
                    }
                }
                true
            }

            popup.show()
        }

        selectRangeBtn = view.findViewById(R.id.selectRangeBtn)
        selectRangeBtn.setOnClickListener {
            UiUtil.showSelectRangeDialog(requireActivity(), processingDownloadIDs.size) {
                val itemsBetween = processingDownloadIDs.filterIndexed { index, _ -> index >= it.first && index <= it.second }
                listAdapter.selectItems(itemsBetween)
                selectItemsMenuBtn.isVisible = true
                count.text = "${itemsBetween.size} ${getString(R.string.selected)}"
            }
        }

        val editSelectedOkBtn = view.findViewById<MaterialButton>(R.id.bottomsheet_ok_button)
        editSelectedOkBtn.setOnClickListener {
            multipleSelectHeader.isVisible = false
            view.findViewById<ConstraintLayout>(R.id.downloadHeader).isVisible = true
            selectItemsMenuBtn.isVisible = false
            filesize.isVisible = itemsFileSize > 0
            count.text = "${processingDownloadIDs.size} ${getString(R.string.selected)}"
            selectRangeBtn.isVisible = false
            selectItemsOpenBtn.isVisible = true
            listAdapter.clearCheckedItems()
            updateBottomAppBarMenuItemsVisibility()
        }


        val sortBtn = view.findViewById<MaterialButton>(R.id.sortBtn)
        sortBtn.setOnClickListener {
            lifecycleScope.launch {
                val newSort = withContext(Dispatchers.IO) {
                    downloadViewModel.toggleProcessingSort()
                }

                when(newSort) {
                    "ASC" -> sortBtn.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_down)
                    "DESC" -> sortBtn.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_up)
                }
                recyclerView.scrollTo(0, 0)
            }
        }

    }

    override fun onResume() {
        super.onResume()
        ViewCompat.setOnApplyWindowInsetsListener(bottomAppBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Prevent extra bottom padding
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun toggleLoading(loading: Boolean){
        shimmerTitle.isVisible = loading
        title.isVisible = !loading
        shimmerSubtitle.isVisible = loading
        subtitle.isVisible = !loading

        if (loading){
            shimmerTitle.startShimmer()
            shimmerSubtitle.startShimmer()
        }else{
            shimmerTitle.stopShimmer()
            shimmerSubtitle.stopShimmer()
        }

        scheduleBtn.isEnabled = !loading
        downloadBtn.isEnabled = !loading
        bottomAppBar.menu.children.forEach { m -> m.isEnabled = !loading }
    }

    private fun updateFileSize(items: List<DownloadItemConfigureMultiple>){
        val fileSizes = mutableListOf<Long>()
        items.forEach {
            if (it.type == DownloadViewModel.Type.video){
                println(it.format.filesize)
                if (it.format.filesize <= 5L) {
                    fileSizes.add(0)
                }else{
                    val preferredAudioFormatIDs = it.videoPreferences.audioFormatIDs
                    val audioFormatSize = if (it.videoPreferences.removeAudio) {
                        0
                    }else{
                        it.allFormats
                            .filter { f -> preferredAudioFormatIDs.contains(f.format_id) }
                            .sumOf { f -> f.filesize }
                    }
                    fileSizes.add(it.format.filesize + audioFormatSize)
                }
            }else if (it.type == DownloadViewModel.Type.audio){
                fileSizes.add(it.format.filesize)
            }
        }

        if (fileSizes.all { it > 5L }){
            val filesizeRaw = fileSizes.sum()
            val size = FileUtil.convertFileSize(filesizeRaw)
            itemsFileSize = fileSizes.sum()
            filesize.isVisible = size != "?" && !listAdapter.isCheckingItems()
            filesize.text = "${getString(R.string.file_size)}: >~ $size"
            formatViewModel.checkFreeSpace(filesizeRaw, Environment.getExternalStorageDirectory().path)
        }else{
            filesize.visibility = View.GONE
        }
    }

    private var pathResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            CoroutineScope(Dispatchers.IO).launch {
                downloadViewModel.updateProcessingDownloadPath(listAdapter.getCheckedItemsOrNull(), result.data?.data.toString())
            }

            val path = FileUtil.formatPath(result.data!!.data.toString())
            Toast.makeText(requireContext(),getString(R.string.changed_path_for_everyone_to) + " " + path, Toast.LENGTH_LONG).show()
        }
    }

    override fun onButtonClick(id: Long) {
        lifecycleScope.launch {
            var item = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(id)
            }

            val bottomSheet = BottomSheetDialog(requireContext())
            bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
            bottomSheet.setContentView(R.layout.download_type_sheet)

            // BUTTON ----------------------------------
            val audio = bottomSheet.findViewById<TextView>(R.id.audio)
            val video = bottomSheet.findViewById<TextView>(R.id.video)
            val command = bottomSheet.findViewById<TextView>(R.id.command)

            withContext(Dispatchers.IO){
                val nr = commandTemplateViewModel.getTotalNumber()
                if(nr == 0){
                    command!!.visibility = View.GONE
                }else{
                    command!!.visibility = View.VISIBLE
                }
            }

            audio!!.setOnClickListener {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO){
                        item = downloadViewModel.switchDownloadType(listOf(item), DownloadViewModel.Type.audio).first()
                        downloadViewModel.updateDownload(item)
                    }
                    bottomSheet.cancel()
                }
            }

            video!!.setOnClickListener {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO){
                        item = downloadViewModel.switchDownloadType(listOf(item), DownloadViewModel.Type.video).first()
                        downloadViewModel.updateDownload(item)
                    }
                    bottomSheet.cancel()
                }
            }

            command!!.setOnClickListener {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO){
                        item = downloadViewModel.switchDownloadType(listOf(item), DownloadViewModel.Type.command).first()
                        downloadViewModel.updateDownload(item)
                    }
                    bottomSheet.cancel()
                }
            }

            bottomSheet.show()
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
            bottomSheet.window!!.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    override fun onCardClick(id: Long) {
        lifecycleScope.launch{

            val downloadItem = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(id)
            }

            if (parentFragmentManager.findFragmentByTag("configureDownloadSingleSheet") == null){
                val bottomSheet = ConfigureDownloadBottomSheetDialog(downloadItem, this@DownloadMultipleBottomSheetDialog)
                bottomSheet.show(parentFragmentManager, "configureDownloadSingleSheet")
            }
        }
    }

    override fun onCardChecked(id: Long) {
        selectItemsMenuBtn.isVisible = true
        val checkedSize = listAdapter.getCheckedItemsSize()
        count.text = "$checkedSize ${getString(R.string.selected)}"
        updateBottomAppBarMenuItemsVisibility()
    }

    override fun onCardUnChecked(id: Long) {
        val checkedSize = listAdapter.getCheckedItemsSize()
        if (checkedSize == 0) {
            selectItemsMenuBtn.isVisible = false
        }
        count.text = "$checkedSize ${getString(R.string.selected)}"
        updateBottomAppBarMenuItemsVisibility()
    }

    override fun onDelete(id: Long) {
        lifecycleScope.launch {
            val deletedItem = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(id)
            } ?: return@launch

            UiUtil.showGenericDeleteDialog(requireContext(), deletedItem.title){
                lifecycleScope.launch {
                    processingItemsCount--
                    downloadViewModel.deleteDownload(id)

                    if (processingItemsCount > 0){
                        Snackbar.make(recyclerView, getString(R.string.you_are_going_to_delete) + ": " + deletedItem.title, Snackbar.LENGTH_INDEFINITE)
                            .setAction(getString(R.string.undo)) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    processingItemsCount++
                                    downloadViewModel.insert(deletedItem)
                                }
                            }.show()
                    }else{
                        dismiss()
                    }

                }
            }
        }


    }

    override fun onClick(p0: View?) {
    }

    override fun onDownloadItemUpdate(item: DownloadItem) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO){
                downloadViewModel.updateDownload(item)
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO){
                downloadViewModel.processingItemsJob?.cancel(CancellationException())
                downloadViewModel.processingItemsJob = null
                downloadViewModel.deleteProcessing()
            }
        }

        super.onDismiss(dialog)
    }

    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView,viewHolder: RecyclerView.ViewHolder,target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val itemID = viewHolder.itemView.tag.toString().toLong()
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        lifecycleScope.launch {
                            val deletedItem = withContext(Dispatchers.IO){
                                downloadViewModel.getItemByID(itemID)
                            }
                            processingItemsCount--
                            withContext(Dispatchers.IO){
                                downloadViewModel.deleteDownload(deletedItem.id)
                            }


                            if (processingItemsCount > 0) {
                                Snackbar.make(recyclerView, getString(R.string.you_are_going_to_delete) + ": " + deletedItem.title, Snackbar.LENGTH_INDEFINITE)
                                    .setAction(getString(R.string.undo)) {
                                        processingItemsCount++
                                        downloadViewModel.insert(deletedItem)
                                    }.show()
                            }else{
                                dismiss()
                            }
                        }
                    }

                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                RecyclerViewSwipeDecorator.Builder(
                    requireContext(),
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
                    .addSwipeLeftBackgroundColor(Color.RED)
                    .addSwipeLeftActionIcon(R.drawable.baseline_delete_24)
                    .addSwipeRightBackgroundColor(
                        MaterialColors.getColor(
                            requireContext(),
                            R.attr.colorOnSurfaceInverse, Color.TRANSPARENT
                        )
                    )
                    .create()
                    .decorate()
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }

    private fun handleDuplicatesAndDismiss(res: List<DownloadViewModel.AlreadyExistsIDs>) {
        if (activity is ShareActivity && res.isNotEmpty()) {
            //let the lifecycle listener handle it
        }else{
            dismiss()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setContainerText(cont: String?) {
        if (cont == null) {
            containerBtn.isVisible = false
            return
        }

        if (cont == getString(R.string.defaultValue) || cont.isEmpty()) {
            containerTextView.text = ".ext"
            return
        }else{
            containerTextView.text = cont
        }
        containerBtn.isVisible = true
    }

    private fun updateBottomAppBarMenuItemsVisibility(item: DownloadItemConfigureMultiple? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val haveSameType = downloadViewModel.checkIfAllProcessingItemsHaveSameType(listAdapter.getCheckedItemsOrNull())

            withContext(Dispatchers.Main){
                if (haveSameType.first) {
                    formatBtn.icon?.alpha = 255
                    moreBtn.icon?.alpha = 255
                }else {
                    formatBtn.icon?.alpha = 30
                    moreBtn.icon?.alpha = 30
                }
            }

            val haveSameContainer = downloadViewModel.checkIfAllProcessingItemsHaveSameContainer(listAdapter.getCheckedItemsOrNull())

            withContext(Dispatchers.Main) {
                if (haveSameContainer.first && item != null) {
                    setContainerText(item.container)
                }else {
                    setContainerText("")
                }
                containerBtn.isVisible = haveSameContainer.first && haveSameType.first && haveSameType.second != DownloadViewModel.Type.command
            }
        }
    }

}

