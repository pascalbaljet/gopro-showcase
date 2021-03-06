package com.aajtech.mobile.goproshowcase.service

import android.annotation.TargetApi
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileDescriptor
import java.io.IOException
import java.net.*
import java.util.regex.Pattern

/**
 * Created by pablo.biagioli on 7/20/16.
 */
object MagicPacket {
    private val TAG = "MagicPacket"

    //val BROADCAST = "192.168.1.255"
    val PORT = 9
    val SEPARATOR = ':'

    @Throws(UnknownHostException::class, SocketException::class, IOException::class, IllegalArgumentException::class)
    @JvmOverloads fun send(mac: String, ip: String, port: Int = PORT): String {
        // validate MAC and chop into array
        val hex = validateMac(mac)

        // convert to base16 bytes
        val macBytes = ByteArray(6)
        for (i in 0..5) {
            macBytes[i] = Integer.parseInt(hex[i], 16).toByte()
        }

        val bytes = ByteArray(102)

        // fill first 6 bytes
        for (i in 0..5) {
            bytes[i] = 0xff.toByte()
        }
        // fill remaining bytes with target MAC
        var i = 6
        while (i < bytes.size) {
            System.arraycopy(macBytes, 0, bytes, i, macBytes.size)
            i += macBytes.size
        }

        // create socket to IP
        val address = InetAddress.getByName(ip)
        val packet = DatagramPacket(bytes, bytes.size, address, port)
        val socket = DatagramSocket()
        when(Build.VERSION.SDK_INT){
            Build.VERSION_CODES.LOLLIPOP ->{ bindSocketToWiFiAPI21(socket) }
            Build.VERSION_CODES.LOLLIPOP_MR1 -> { bindSocketToWiFiAPI22(socket) }
            Build.VERSION_CODES.M ->{ bindSocketToWiFiAPI23(socket) }
        }
        socket.send(packet)
        socket.close()

        return hex[0] + SEPARATOR + hex[1] + SEPARATOR + hex[2] + SEPARATOR + hex[3] + SEPARATOR + hex[4] + SEPARATOR + hex[5]
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun bindSocketToWiFiAPI22(socket: DatagramSocket){
        wifiNetwork?.bindSocket(socket)
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun bindSocketToWiFiAPI23(socket: DatagramSocket){
        wifiNetwork?.bindSocket(socket)
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun bindSocketToWiFiAPI21(socket: DatagramSocket){
        //wifiNetwork?.bindSocket(socket)
        val networkUtils = Class.forName("android.net.NetworkUtils")
        val method = networkUtils.declaredMethods.find { it.name.equals("bindSocketToNetwork",true) }
        val result = method?.invoke(null, getFileDescriptor(socket))
        Log.d(TAG, "Result bind datagram to network is " + result)
        if (result != 0){
            Log.d(TAG,"Error binding socket to network")
        }
    }

    @Throws(SocketException::class)
    private fun getFileDescriptor(socket: DatagramSocket): Int {
        try {
            val implField = DatagramSocket::class.java.getDeclaredField("impl")
            implField.isAccessible = true

            val implValue = implField.get(socket) as DatagramSocketImpl
            val fdField = DatagramSocketImpl::class.java.getDeclaredField("fd")
            fdField.isAccessible = true

            val fdValue = fdField.get(implValue) as FileDescriptor
            val descField = FileDescriptor::class.java.getDeclaredField("descriptor")
            descField.isAccessible = true

            return descField.getInt(fdValue)

        } catch (e: NoSuchFieldException) {
            e.printStackTrace()
            throw AssertionError(e)
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
            throw AssertionError(e)
        }

    }

    @Throws(IllegalArgumentException::class)
    fun cleanMac(mac: String): String {
        val hex = validateMac(mac)

        var sb = StringBuffer()
        var isMixedCase = false

        // check for mixed case
        for (i in 0..5) {
            sb.append(hex[i])
        }
        val testMac = sb.toString()
        if (testMac.toLowerCase() == testMac == false && testMac.toUpperCase() == testMac == false) {
            isMixedCase = true
        }

        sb = StringBuffer()
        for (i in 0..5) {
            // convert mixed case to lower
            if (isMixedCase == true) {
                sb.append(hex[i].toLowerCase())
            } else {
                sb.append(hex[i])
            }
            if (i < 5) {
                sb.append(SEPARATOR)
            }
        }
        return sb.toString()
    }

    @Throws(IllegalArgumentException::class)
    private fun validateMac(macAddress: String): Array<String> {
        var mac = macAddress
        // error handle semi colons
        mac = mac.replace(";", ":")

        // attempt to assist the user a little
        var newMac = ""

        if (mac.matches("([a-zA-Z0-9]){12}".toRegex())) {
            // expand 12 chars into a valid mac address
            for (i in 0..mac.length - 1) {
                if (i > 1 && i % 2 == 0) {
                    newMac += ":"
                }
                newMac += mac[i]
            }
        } else {
            newMac = mac
        }

        // regexp pattern match a valid MAC address
        val pat = Pattern.compile("((([0-9a-fA-F]){2}[-:]){5}([0-9a-fA-F]){2})")
        val m = pat.matcher(newMac)

        if (m.find()) {
            val result = m.group()
            return result.split("(\\:|\\-)".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        } else {
            throw IllegalArgumentException("Invalid MAC address")
        }
    }

}