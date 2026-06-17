package com.example.ui.adapters

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.util.LruCache
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.request.ImageRequest
import com.example.data.db.MediaEntity
import com.example.data.repository.MediaFolder
import kotlinx.coroutines.*
import java.io.File

sealed class BrowserItem {
    data class FolderItem(val folder: MediaFolder) : BrowserItem()
    data class MediaFileItem(val media: MediaEntity) : BrowserItem()

    val path: String
        get() = when (this) {
            is FolderItem -> folder.path
            is MediaFileItem -> media.path
        }
}

class FolderBrowserAdapter(
    private val context: Context,
    private val onFolderClicked: (MediaFolder) -> Unit,
    private val onMediaClicked: (MediaEntity, List<MediaEntity>, Int) -> Unit
) : RecyclerView.Adapter<FolderBrowserAdapter.ViewHolder>() {

    private val TAG = "FolderBrowserAdapter"
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val diffCallback = object : DiffUtil.ItemCallback<BrowserItem>() {
        override fun areItemsTheSame(oldItem: BrowserItem, newItem: BrowserItem): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: BrowserItem, newItem: BrowserItem): Boolean {
            return oldItem == newItem
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    init {
        setHasStableIds(true)
    }

    fun submitList(list: List<BrowserItem>) {
        differ.submitList(list)
    }

    override fun getItemId(position: Int): Long {
        val item = differ.currentList[position]
        return item.path.hashCode().toLong()
    }

    override fun getItemCount(): Int = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Construct visual programmatic view hierarchy matching Material 3 specification
        val density = context.resources.displayMetrics.density
        val dp = { value: Int -> (value * density).toInt() }

        val rootView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val paddingV = dp(12)
            val paddingH = dp(16)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            
            // Apply native Ripple backgrounds
            val outVal = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outVal, true)
            setBackgroundResource(outVal.resourceId)
            isClickable = true
            isFocusable = true
            gravity = Gravity.CENTER_VERTICAL
        }

        // Left ImageView Thumbnail
        val thumbnailView = ImageView(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                marginEnd = dp(14)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        rootView.addView(thumbnailView)

        // Middle Text Details Layout block
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        }

        val titleView = TextView(context).apply {
            id = View.generateViewId()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textContainer.addView(titleView)

        val subtitleView = TextView(context).apply {
            id = View.generateViewId()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.GRAY)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        }
        textContainer.addView(subtitleView)

        rootView.addView(textContainer)

        // Right Action Icon indicator
        val actionIconView = ImageView(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                marginStart = dp(12)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        rootView.addView(actionIconView)

        return ViewHolder(rootView, thumbnailView, titleView, subtitleView, actionIconView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = differ.currentList[position]
        holder.bind(item, position)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelLoading()
    }

    inner class ViewHolder(
        view: View,
        val thumbnail: ImageView,
        val title: TextView,
        val subtitle: TextView,
        val actionIcon: ImageView
    ) : RecyclerView.ViewHolder(view) {

        private var loadJob: Job? = null
        private val dp = { valD: Int -> (valD * context.resources.displayMetrics.density).toInt() }

        fun bind(item: BrowserItem, pos: Int) {
            cancelLoading()

            // Setup programmatic image placeholder with smooth theme colors
            val defaultDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(0x1F7F7F7F) // Semi-transparent overlay surface color
            }
            thumbnail.setImageDrawable(defaultDrawable)

            // Setup right indicator icons
            val drawSelect = TypedValue()
            when (item) {
                is BrowserItem.FolderItem -> {
                    title.text = item.folder.name
                    subtitle.text = "${item.folder.totalItems} elements"
                    
                    // Folder icon
                    context.theme.resolveAttribute(android.R.attr.colorPrimary, drawSelect, true)
                    itemView.setOnClickListener {
                        onFolderClicked(item.folder)
                    }

                    // Use Android's standard folder drawable dynamically via Resource lookup
                    val folderResId = context.resources.getIdentifier("ic_menu_archive", "drawable", "android")
                    if (folderResId != 0) {
                        thumbnail.setImageResource(folderResId)
                        thumbnail.setColorFilter(0xFFBD93F9.toInt()) // Lavender theme accent
                    } else {
                        thumbnail.setBackgroundColor(0xFF2E2E2E.toInt())
                        thumbnail.setColorFilter(Color.GRAY)
                    }
                    thumbnail.clearColorFilter()

                    // Right chevron icon helper
                    val chevronResId = context.resources.getIdentifier("ic_menu_moreoverflow_normal_holo_dark", "drawable", "android")
                    if (chevronResId != 0) {
                        actionIcon.setImageResource(chevronResId)
                        actionIcon.setColorFilter(Color.GRAY)
                    }
                }
                is BrowserItem.MediaFileItem -> {
                    val media = item.media
                    title.text = media.title
                    
                    val sizeFormatted = formatFileSize(media.size)
                    val durationFormatted = formatDuration(media.duration)
                    subtitle.text = if (media.isVideo) "Video • $durationFormatted • $sizeFormatted" else "${media.artist} • $durationFormatted"

                    // File click actions
                    itemView.setOnClickListener {
                        // Gather list of files in parent folder to support next/prev deck commands
                        val playlist = differ.currentList.filterIsInstance<BrowserItem.MediaFileItem>().map { it.media }
                        val fileIndex = playlist.indexOfFirst { it.path == media.path }.coerceAtLeast(0)
                        onMediaClicked(media, playlist, fileIndex)
                    }

                    // Right play icon
                    val playIconResId = context.resources.getIdentifier("ic_media_play", "drawable", "android")
                    if (playIconResId != 0) {
                        actionIcon.setImageResource(playIconResId)
                        actionIcon.setColorFilter(0xFFBD93F9.toInt())
                    }

                    // Asynchronously load thumbnail using Coil + ContentResolver loadThumbnail
                    loadThumbnailAsync(media)
                }
            }
        }

        private fun loadThumbnailAsync(media: MediaEntity) {
            loadJob = adapterScope.launch {
                val cached = ThumbsCache.get(media.path)
                if (cached != null) {
                    thumbnail.setImageBitmap(cached)
                    return@launch
                }

                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        if (media.path.startsWith("asset:///")) {
                            null // Use default icon placeholder fallback
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            fetchContentResolverThumbnail(media)
                        } else {
                            fetchCoilThumbnail(media)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Thumbnail extraction failed: ${e.message}")
                        null
                    }
                }

                if (bitmap != null) {
                    ThumbsCache.put(media.path, bitmap)
                    thumbnail.setImageBitmap(bitmap)
                } else {
                    // Fallback icons
                    val fallbackRes = if (media.isVideo) {
                        context.resources.getIdentifier("ic_menu_slideshow", "drawable", "android")
                    } else {
                        context.resources.getIdentifier("ic_media_play", "drawable", "android")
                    }
                    if (fallbackRes != 0) {
                        thumbnail.setImageResource(fallbackRes)
                        thumbnail.setColorFilter(Color.GRAY)
                    }
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun fetchContentResolverThumbnail(media: MediaEntity): Bitmap? {
            return try {
                val file = File(media.path)
                if (!file.exists()) return null
                val uri = if (media.isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                
                // Form content Uri from MediaStore values
                val contentUri = ContentUris.withAppendedId(uri, getMediaStoreId(media))
                context.contentResolver.loadThumbnail(contentUri, Size(128, 128), null)
            } catch (e: Exception) {
                // Return null so we fall back to Coil or older systems
                null
            }
        }

        private suspend fun fetchCoilThumbnail(media: MediaEntity): Bitmap? {
            return try {
                val request = ImageRequest.Builder(context)
                    .data(File(media.path))
                    .size(128, 128)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } catch (e: Exception) {
                null
            }
        }

        private fun getMediaStoreId(media: MediaEntity): Long {
            // Find ID associated with filesystem path via fast cursor query or extract from path hash
            var mediaStoreId = -1L
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val uri = if (media.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    "${MediaStore.MediaColumns.DATA} = ?",
                    arrayOf(media.path),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                        if (idCol != -1) {
                            mediaStoreId = cursor.getLong(idCol)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error looking up MediaStore file ID: ${e.message}")
            }
            return if (mediaStoreId == -1L) media.path.hashCode().toLong() else mediaStoreId
        }

        fun cancelLoading() {
            loadJob?.cancel()
            loadJob = null
        }

        private fun formatFileSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }

        private fun formatDuration(duration: Long): String {
            val sec = (duration / 1000) % 60
            val min = (duration / (1000 * 60)) % 60
            val hrs = (duration / (1000 * 60 * 60))
            return if (hrs > 0) {
                String.format("%d:%02d:%02d", hrs, min, sec)
            } else {
                String.format("%02d:%02d", min, sec)
            }
        }
    }
}

object ThumbsCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // Use 1/8th of available App memory
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(key: String): Bitmap? = memoryCache.get(key)
    fun put(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
    }
}
