package com.deniscerri.ytdl.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel

@Entity(tableName = "logs")
data class LogItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    var title: String,
    var content: String,
    var format: Format,
    var downloadType: DownloadViewModel.Type,
    var downloadTime: Long,
)
