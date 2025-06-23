package com.example.allinsafe_bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.edit

@Suppress("DEPRECATION")
class BluetoothMainActivity : ComponentActivity() {
    // 요청 권한 목록
    private val bluetoothPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    // Activity Result API 로 권한 요청
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (bluetoothPermissions.all { results[it] == true }) {
            initializeBluetoothFeature()
        } else {
            showToast("블루투스 권한이 필요합니다.")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 권한 요청 시작
        permissionLauncher.launch(bluetoothPermissions)
    }


    // SharedPreferences 키
    private val PREFS = "bluetooth_security_prefs"
    private val TRUSTED_KEY = "trusted"
    private val BLOCKED_KEY = "blocked"

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val trustedDevices = mutableSetOf<String>()
    private val blockedDevices = mutableSetOf<String>()

    // 페어링·ACL 이벤트 리시버
    private val pairingReceiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    // system pairing dialog 사용
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val dev =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            ?: return
                    addTrustedDevice(dev.address)
                    showToast("연결 감지 → 신뢰 추가: ${dev.address}")
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val dev =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            ?: return
                    showToast("기기 연결 해제: ${dev.address}")
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED->handleBondStateChanged(intent)
            }
        }
    }

       /** 시스템 페어링 결과(BOND_STATE_CHANGED) 처리 */
       private fun handleBondStateChanged(intent: Intent) {
           val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
           val prev = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
           val curr = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)

           if (prev == BluetoothDevice.BOND_BONDING && curr == BluetoothDevice.BOND_NONE) {
               addBlockedDevice(dev.address)
               showToast("차단된 기기: ${dev.address}")
           } else if (curr == BluetoothDevice.BOND_BONDED) {
               addTrustedDevice(dev.address)
               showToast("신뢰 기기 등록: ${dev.address}")
           }
       }
    private fun initializeBluetoothFeature() {
        // 1) 기존 bondedDevices → 신뢰 목록으로
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bluetoothAdapter?.bondedDevices
            ?.filter{it.address !in blockedDevices }
            ?.forEach{addTrustedDevice(it.address)}

        // 2) Receiver 등록 (applicationContext 에 등록해서 액티비티 finish 후에도 동작)
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        applicationContext.registerReceiver(pairingReceiver, filter)
        showToast("블루투스 보호 모드 활성화")

    }
    /** Activity 종료 시 리시버 해제 */
    override fun onDestroy() {
        super.onDestroy()
        applicationContext.unregisterReceiver(pairingReceiver)
    }


    private fun addTrustedDevice(address: String) {
        loadDeviceLists()
        if (trustedDevices.add(address)) {
            saveDeviceLists()
            Log.i("BluetoothSecurity", "신뢰 기기 등록: $address")
        }
    }

    private fun addBlockedDevice(address: String) {
        loadDeviceLists()
        if (blockedDevices.add(address)) {
            saveDeviceLists()
            Log.i("BluetoothSecurity", "차단 기기 등록: $address")
        }
    }

    private fun loadDeviceLists() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        trustedDevices.clear()
        blockedDevices.clear()
        prefs.getStringSet(TRUSTED_KEY, emptySet())?.let { trustedDevices.addAll(it) }
        prefs.getStringSet(BLOCKED_KEY, emptySet())?.let { blockedDevices.addAll(it) }
    }

    private fun saveDeviceLists() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit {
            putStringSet(TRUSTED_KEY, trustedDevices)
            putStringSet(BLOCKED_KEY, blockedDevices)
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
