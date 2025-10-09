package io.github.meesum.telemetry.collectors

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import io.github.meesum.telemetry.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge

/**
 * Collects network-related metrics
 */
class NetworkMetricsCollector(
    private val getCommonAttributes: () -> Attributes
) : MetricsCollector {
    
    private var networkUsageGauge: ObservableLongGauge? = null
    private var activeNetworkGauge: ObservableLongGauge? = null
    
    @RequiresApi(Build.VERSION_CODES.M)
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun setup(application: Application, meter: Meter) {
        // Network usage (bytes transferred)
        networkUsageGauge = meter.gaugeBuilder("network.bytes.transferred")
            .setDescription("Network bytes transferred (sent + received)")
            .setUnit("bytes")
            .ofLongs()
            .buildWithCallback { measurement ->
                val bytes = getNetworkBytesTransferred()
                if (bytes >= 0) {
                    measurement.record(bytes, getCommonAttributes().toOtelAttributes())
                }
            }
        
        // Active network type
        activeNetworkGauge = meter.gaugeBuilder("network.active")
            .setDescription("Active network type (0=none, 1=mobile, 2=wifi, 3=ethernet, 4=other)")
            .ofLongs()
            .buildWithCallback { measurement ->
                val networkType = getActiveNetworkType(application)
                measurement.record(networkType.toLong(), getCommonAttributes().toOtelAttributes())
            }
    }
    
    @RequiresApi(Build.VERSION_CODES.M)
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_NETWORK_STATE])
    private fun getActiveNetworkType(context: Context): Int {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivityManager?.activeNetwork ?: return 0
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return 0

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 2
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 1
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 3
                else -> 4 // Other
            }
        } catch (e: Exception) {
            Log.e("NetworkMetrics", "Failed to get network type", e)
            0
        }
    }
    
    private fun getNetworkBytesTransferred(): Long {
        return try {
            TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
        } catch (e: Exception) {
            Log.e("NetworkMetrics", "Failed to get network bytes transferred", e)
            -1
        }
    }
    
    override fun shutdown() {
        networkUsageGauge?.close()
        activeNetworkGauge?.close()
    }
}

