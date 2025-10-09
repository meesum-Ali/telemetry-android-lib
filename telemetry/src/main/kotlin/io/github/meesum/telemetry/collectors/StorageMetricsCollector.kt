package io.github.meesum.telemetry.collectors

import android.app.Application
import android.os.Environment
import android.os.StatFs
import android.util.Log
import io.github.meesum.telemetry.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge
import java.io.File

/**
 * Collects storage-related metrics
 */
class StorageMetricsCollector(
    private val getCommonAttributes: () -> Attributes
) : MetricsCollector {
    
    private var storageUsageGauge: ObservableLongGauge? = null
    private var cacheSizeGauge: ObservableLongGauge? = null
    private var dataSizeGauge: ObservableLongGauge? = null
    private var availableStorageGauge: ObservableLongGauge? = null
    
    override fun setup(application: Application, meter: Meter) {
        // Storage used
        storageUsageGauge = meter.gaugeBuilder("storage.used.bytes")
            .setDescription("Used storage space in bytes")
            .setUnit("bytes")
            .ofLongs()
            .buildWithCallback { measurement ->
                val usedSpace = getUsedStorageSpace()
                if (usedSpace >= 0) {
                    measurement.record(usedSpace, getCommonAttributes().toOtelAttributes())
                }
            }
        
        // App cache size
        cacheSizeGauge = meter.gaugeBuilder("app.storage.cache_size")
            .setDescription("App cache directory size in bytes")
            .setUnit("By")
            .ofLongs()
            .buildWithCallback { measurement ->
                try {
                    val cacheSize = getDirectorySize(application.cacheDir)
                    measurement.record(cacheSize, getCommonAttributes().toOtelAttributes())
                } catch (e: Exception) {
                    Log.e("StorageMetrics", "Failed to get cache size", e)
                }
            }
        
        // App data size
        dataSizeGauge = meter.gaugeBuilder("app.storage.data_size")
            .setDescription("App data directory size in bytes")
            .setUnit("By")
            .ofLongs()
            .buildWithCallback { measurement ->
                try {
                    @Suppress("DEPRECATION")
                    val dataDir = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        application.dataDir
                    } else {
                        application.applicationInfo.dataDir?.let { File(it) } ?: return@buildWithCallback
                    }
                    val dataSize = getDirectorySize(dataDir)
                    measurement.record(dataSize, getCommonAttributes().toOtelAttributes())
                } catch (e: Exception) {
                    Log.e("StorageMetrics", "Failed to get data size", e)
                }
            }
        
        // Available storage
        availableStorageGauge = meter.gaugeBuilder("device.storage.available")
            .setDescription("Available internal storage in bytes")
            .setUnit("By")
            .ofLongs()
            .buildWithCallback { measurement ->
                try {
                    val stat = StatFs(Environment.getDataDirectory().path)
                    val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
                    measurement.record(availableBytes, getCommonAttributes().toOtelAttributes())
                } catch (e: Exception) {
                    Log.e("StorageMetrics", "Failed to get available storage", e)
                }
            }
    }
    
    private fun getUsedStorageSpace(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            (totalBlocks - availableBlocks) * blockSize
        } catch (e: Exception) {
            Log.e("StorageMetrics", "Failed to get storage usage", e)
            -1
        }
    }
    
    private fun getDirectorySize(directory: File): Long {
        var size = 0L
        try {
            if (directory.exists()) {
                val files = directory.listFiles()
                files?.forEach { file ->
                    size += if (file.isDirectory) {
                        getDirectorySize(file)
                    } else {
                        file.length()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("StorageMetrics", "Error calculating directory size", e)
        }
        return size
    }
    
    override fun shutdown() {
        storageUsageGauge?.close()
        cacheSizeGauge?.close()
        dataSizeGauge?.close()
        availableStorageGauge?.close()
    }
}

