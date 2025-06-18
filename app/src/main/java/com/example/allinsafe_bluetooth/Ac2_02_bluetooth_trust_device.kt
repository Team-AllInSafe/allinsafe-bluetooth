package com.example.allinsafe_bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.bluetoothtest.R
import java.util.jar.Manifest

data class TrustedDevice(
    val address: String,
    val name: String,
    val isConnected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
class Ac2_02_bluetooth_trust_device : ComponentActivity() {
    private val PREFS = "bluetooth_security_prefs"
    private val TRUSTED_KEY = "trusted"
    private val BLOCKED_KEY = "blocked"

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TrustDeviceScreen()
                }
            }
        }
    }

    @Composable
    private fun TrustDeviceScreen() {
        var trustedDevices by remember { mutableStateOf(listOf<TrustedDevice>()) }
        var blockedDevices by remember { mutableStateOf(listOf<TrustedDevice>()) }
        var selectedTab by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            loadDeviceLists { trusted, blocked ->
                trustedDevices = trusted
                blockedDevices = blocked
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = "기기 관리",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )

            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "신뢰 기기 (${trustedDevices.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "차단 기기 (${blockedDevices.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            when (selectedTab) {
                0 -> DeviceList(
                    devices = trustedDevices,
                    listType = "신뢰",
                    emptyMessage = "등록된 신뢰 기기가 없습니다.",
                    onDeviceRemoved = { address ->
                        removeFromTrustedList(address)
                        loadDeviceLists { trusted, blocked ->
                            trustedDevices = trusted
                            blockedDevices = blocked
                        }
                    }
                )
                1 -> DeviceList(
                    devices = blockedDevices,
                    listType = "차단",
                    emptyMessage = "차단된 기기가 없습니다.",
                    onDeviceRemoved = { address ->
                        removeFromBlockedList(address)
                        loadDeviceLists { trusted, blocked ->
                            trustedDevices = trusted
                            blockedDevices = blocked
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun DeviceList(
        devices: List<TrustedDevice>,
        listType: String,
        emptyMessage: String,
        onDeviceRemoved: (String) -> Unit
    ) {
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = emptyMessage,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (listType == "신뢰")
                            "블루투스 보안을 활성화하면 기기가 자동으로 등록됩니다."
                        else
                            "미등록 기기를 차단하면 여기에 표시됩니다.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    DeviceItem(
                        device = device,
                        listType = listType,
                        onRemoveClick = { onDeviceRemoved(device.address) }
                    )
                }
            }
        }
    }

    @Composable
    private fun DeviceItem(
        device: TrustedDevice,
        listType: String,
        onRemoveClick: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (listType == "신뢰")
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = device.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (device.isConnected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        Color.Green,
                                        shape = RoundedCornerShape(50)
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = device.address,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (device.isConnected) {
                        Text(
                            text = "연결됨",
                            fontSize = 10.sp,
                            color = Color.Green,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = onRemoveClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (listType == "신뢰")
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = if (listType == "신뢰") "신뢰 목록에서 제거" else "차단 해제"
                    )
                }
            }
        }
    }

    private fun loadDeviceLists(callback: (List<TrustedDevice>, List<TrustedDevice>) -> Unit) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val trustedAddresses = prefs.getStringSet(TRUSTED_KEY, emptySet()) ?: emptySet()
        val blockedAddresses = prefs.getStringSet(BLOCKED_KEY, emptySet()) ?: emptySet()

        val trustedDevices = mutableListOf<TrustedDevice>()
        val blockedDevices = mutableListOf<TrustedDevice>()

        // 연결된 기기 정보 가져오기
        val bondedDevices = if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothAdapter?.bondedDevices ?: emptySet()
        } else {
            emptySet()
        }

        // 신뢰 기기 목록 생성
        trustedAddresses.forEach { address ->
            val bondedDevice = bondedDevices.find { it.address == address }
            val deviceName = if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bondedDevice?.name ?: "알 수 없는 기기"
            } else {
                "알 수 없는 기기"
            }

            trustedDevices.add(
                TrustedDevice(
                    address = address,
                    name = deviceName,
                    isConnected = bondedDevice != null
                )
            )
        }

        // 차단 기기 목록 생성
        blockedAddresses.forEach { address ->
            val bondedDevice = bondedDevices.find { it.address == address }
            val deviceName = if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bondedDevice?.name ?: "알 수 없는 기기"
            } else {
                "알 수 없는 기기"
            }

            blockedDevices.add(
                TrustedDevice(
                    address = address,
                    name = deviceName,
                    isConnected = false
                )
            )
        }

        callback(trustedDevices.sortedBy { it.name }, blockedDevices.sortedBy { it.name })
    }

    private fun removeFromTrustedList(address: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val trustedSet = prefs.getStringSet(TRUSTED_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()

        if (trustedSet.remove(address)) {
            prefs.edit()
                .putStringSet(TRUSTED_KEY, trustedSet)
                .apply()
            Toast.makeText(this, "신뢰 목록에서 제거되었습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeFromBlockedList(address: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val blockedSet = prefs.getStringSet(BLOCKED_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()

        if (blockedSet.remove(address)) {
            prefs.edit()
                .putStringSet(BLOCKED_KEY, blockedSet)
                .apply()
            Toast.makeText(this, "차단이 해제되었습니다", Toast.LENGTH_SHORT).show()
        }
    }
}