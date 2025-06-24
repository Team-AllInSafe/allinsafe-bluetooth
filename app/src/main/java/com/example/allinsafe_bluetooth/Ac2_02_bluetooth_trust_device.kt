package com.example.allinsafe_bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat

data class TrustedDevice(val address: String, val name: String, val isConnected: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
class Ac2_02_bluetooth_trust_device : ComponentActivity() {
    private val PREFS = "bluetooth_security_prefs"
    private val TRUSTED_KEY = "trusted"
    private val BLOCKED_KEY = "blocked"

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    // Activity-level state so receiver can update it
    private var trustedDevices by mutableStateOf(listOf<TrustedDevice>())
    private var blockedDevices by mutableStateOf(listOf<TrustedDevice>())

    // Listen for system bond state changes
    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                loadDeviceLists { t, b ->
                    trustedDevices = t
                    blockedDevices = b
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TrustDeviceScreen(
                        trustedDevices = trustedDevices,
                        blockedDevices = blockedDevices,
                        onRemoveTrusted = { addr ->
                            removeFromTrustedList(addr)
                            loadDeviceLists { t, b ->
                                trustedDevices = t
                                blockedDevices = b
                            }
                        },
                        onRemoveBlocked = { addr ->
                            removeFromBlockedList(addr)
                            loadDeviceLists { t, b ->
                                trustedDevices = t
                                blockedDevices = b
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(bondStateReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        loadDeviceLists { t, b ->
            trustedDevices = t
            blockedDevices = b
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(bondStateReceiver)
    }

    @Composable
    private fun TrustDeviceScreen(
        trustedDevices: List<TrustedDevice>,
        blockedDevices: List<TrustedDevice>,
        onRemoveTrusted: (String) -> Unit,
        onRemoveBlocked: (String) -> Unit
    ) {
        var selectedTab by remember { mutableStateOf(0) }

        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("기기 관리", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )

            TabRow(selectedTab, Modifier.fillMaxWidth()) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("신뢰 기기 (${trustedDevices.size})", modifier = Modifier.padding(8.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("차단 기기 (${blockedDevices.size})", modifier = Modifier.padding(8.dp))
                }
            }

            val list = if (selectedTab == 0) trustedDevices else blockedDevices
            val removeCallback = if (selectedTab == 0) onRemoveTrusted else onRemoveBlocked
            val emptyMsg = if (selectedTab == 0) "등록된 신뢰 기기가 없습니다." else "차단된 기기가 없습니다."

            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(emptyMsg, fontSize = 16.sp, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list) { dev ->
                        DeviceRow(dev) { removeCallback(dev.address) }
                    }
                }
            }
        }
    }

    @Composable
    private fun DeviceRow(device: TrustedDevice, onRemove: () -> Unit) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(device.name, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(device.address, fontSize = 12.sp, color = Color.Gray)
                    if (device.isConnected) {
                        Text("연결됨", fontSize = 12.sp, color = Color.Green)
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "제거")
                }
            }
        }
    }

    private fun loadDeviceLists(callback: (List<TrustedDevice>, List<TrustedDevice>) -> Unit) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val trusted = prefs.getStringSet(TRUSTED_KEY, emptySet()) ?: emptySet()
        val blocked = prefs.getStringSet(BLOCKED_KEY, emptySet()) ?: emptySet()

        val bonded = if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) bluetoothAdapter?.bondedDevices ?: emptySet() else emptySet()

        val trustedList = trusted.map { addr ->
            val dev = bonded.find { it.address == addr }
            TrustedDevice(addr, dev?.name ?: "알 수 없는 기기", dev != null)
        }
        val blockedList = blocked.map { addr ->
            TrustedDevice(addr, "알 수 없는 기기", false)
        }

        callback(trustedList, blockedList)
    }

    private fun removeFromTrustedList(address: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val set = prefs.getStringSet(TRUSTED_KEY, mutableSetOf())!!.toMutableSet()
        if (set.remove(address)) {
            prefs.edit().putStringSet(TRUSTED_KEY, set).apply()
        }
    }

    private fun removeFromBlockedList(address: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val set = prefs.getStringSet(BLOCKED_KEY, mutableSetOf())!!.toMutableSet()
        if (set.remove(address)) {
            prefs.edit().putStringSet(BLOCKED_KEY, set).apply()
        }
    }
}
