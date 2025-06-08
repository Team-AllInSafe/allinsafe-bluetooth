package com.example.bluetoothtest
import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission

/**
 * 블루투스 관리 기능
 * - 관리: 신뢰/차단 목록 (SharedPreferences)
 * - 감지: 미등록·차단 기기 페어링 시도
 * - 처리: 신뢰/차단/무시 알림, 자동 차단, 페어링 거부
 */
class BluetoothMainActivity : ComponentActivity() {

    // 블루투스 권한 목록
    private val bluetoothPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    // 2. 권한 요청 결과 처리
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = bluetoothPermissions.all { permissions[it] == true }
        if (allGranted) {
            initializeBluetoothFeature()
        } else {
            Toast.makeText(this, "블루투스 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // 3. 블루투스 기능 변수/리스트
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val trustedDevices = mutableSetOf<String>() // 신뢰 기기 주소
    private val blockedDevices = mutableSetOf<String>() // 차단 기기 주소
    private var isReceiverRegistered = false

    private val PREFS = "bluetooth_security_prefs"
    private val TRUSTED_KEY = "trusted"
    private val BLOCKED_KEY = "blocked"


    /** 페어링 시도 감지/자동 차단/선택 알림 브로드캐스트 리시버 */
    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_PAIRING_REQUEST == intent.action) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                device?.let {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                        != PackageManager.PERMISSION_GRANTED
                    ) return

                    val addr = it.address
                    val name = it.name ?: "알 수 없음"
                    when {
                        addr in blockedDevices -> {
                            rejectPairing(it)
                            Toast.makeText(
                                context,
                                "차단된 기기: $name ($addr)\n자동 차단됨",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.i("BluetoothSecurity", "자동 차단: $name ($addr)")
                        }

                        addr in trustedDevices -> {
                            Log.i("BluetoothSecurity", "신뢰 기기: $name ($addr) - 허용")
                        }

                        else -> {
                            showPairingAlert(context, it)
                        }
                    }
                }
            }
        }
    }


    /** 미등록 기기 페어링 시도시 AlertDialog(신뢰 추가/차단/무시) 표시 */
    private fun showPairingAlert(context: Context, device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        AlertDialog.Builder(context)
            .setTitle("미등록 기기 페어링 시도 감지")
            .setMessage("기기명: ${device.name ?: "알 수 없음"}\n주소: ${device.address}")
            .setPositiveButton("신뢰 기기 추가") { _, _ ->
                addTrustedDevice(device.address)
                Toast.makeText(context, "신뢰 기기 추가", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("차단") { _, _ ->
                addBlockedDevice(device.address)
                rejectPairing(device)
                Toast.makeText(context, "차단 및 연결 거부", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("무시") { _, _ ->
                Log.i("BluetoothSecurity", "무시(임시 허용): ${device.address}")
            }
            .setCancelable(false)
            .show()
    }

    /** 페어링 취소(거부) 반영 */
    private fun rejectPairing(device: BluetoothDevice) {
        try {
            device.javaClass.getMethod("cancelBondProcess").invoke(device)
        } catch (e: Exception) {
            Log.e("BluetoothSecurity", "페어링 취소 실패", e)
        }
    }

    /** 신뢰 기기 목록에 추가하고 저장 */
    private fun addTrustedDevice(address: String) {
        trustedDevices.add(address)
        saveDeviceLists()
        Log.i("BluetoothSecurity", "신뢰 기기 등록: $address")
    }

    /** 차단 기기 목록에 추가하고 저장 */
    private fun addBlockedDevice(address: String) {
        blockedDevices.add(address)
        saveDeviceLists()
        Log.i("BluetoothSecurity", "차단 기기 등록: $address")
    }

    /** 목록 저장 */
    private fun saveDeviceLists() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit {
            putStringSet(TRUSTED_KEY, trustedDevices)
            putStringSet(BLOCKED_KEY, blockedDevices)
        }
    }

    /** 목록 불러오기 */
    private fun loadDeviceLists() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        trustedDevices.clear()
        blockedDevices.clear()
        prefs.getStringSet(TRUSTED_KEY, emptySet())?.let { trustedDevices.addAll(it) }
        prefs.getStringSet(BLOCKED_KEY, emptySet())?.let { blockedDevices.addAll(it) }
    }

    private fun hasAllPermissions(): Boolean {
        return bluetoothPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 런타임 권한 요청
        if (!hasAllPermissions()) {
            permissionLauncher.launch(bluetoothPermissions)
            setContent { Surface { Text("블루투스 권한 요청 중...") } }
            return
        }

        // 권한 허용된 경우만 실행
        initializeBluetoothFeature()
    }

    private fun initializeBluetoothFeature() {
        // 앱 권한 체크
        if (!hasAllPermissions()) {
            Toast.makeText(this, "블루투스 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            return
        }
        loadDeviceLists()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothAdapter?.bondedDevices?.forEach { addTrustedDevice(it.address) }
            if (!isReceiverRegistered) {
                registerReceiver(
                    pairingReceiver,
                    IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST)
                )
                isReceiverRegistered = true
            }
            setContent {
                Surface {
                    Text("Bluetooth Security is running.")
                }
            }
            Toast.makeText(this, "블루투스 보호 모드 활성화", Toast.LENGTH_SHORT).show()
        }
    }

    /**리시버 해제 */
    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            unregisterReceiver(pairingReceiver)
            isReceiverRegistered = false
        }
    }
}

