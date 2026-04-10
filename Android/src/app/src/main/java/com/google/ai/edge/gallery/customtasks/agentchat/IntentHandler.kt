/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ai.edge.gallery.customtasks.agentchat

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId

@JsonClass(generateAdapter = true)
data class SendEmailParams(
  val extra_email: String,
  val extra_subject: String,
  val extra_text: String,
)

@JsonClass(generateAdapter = true)
data class SendSmsParams(val phone_number: String, val sms_body: String)

@JsonClass(generateAdapter = true)
data class CreateContactParams(
  val name: String,
  val phone: String = "",
  val email: String = "",
)

@JsonClass(generateAdapter = true)
data class ShowLocationParams(val location: String)

@JsonClass(generateAdapter = true)
data class CalendarEventParams(val title: String, val datetime: String)

object IntentHandler {
  private const val TAG = "IntentHandler"

  fun handleAction(context: Context, action: String, parameters: String): Boolean {
    return try {
      when (action) {
        "send_email" -> handleSendEmail(context, parameters)
        "send_sms" -> handleSendSms(context, parameters)
        "flashlight_on" -> handleFlashlight(context, true)
        "flashlight_off" -> handleFlashlight(context, false)
        "create_contact" -> handleCreateContact(context, parameters)
        "show_location" -> handleShowLocation(context, parameters)
        "open_wifi_settings" -> handleOpenWifiSettings(context)
        "create_calendar_event" -> handleCreateCalendarEvent(context, parameters)
        else -> {
          Log.w(TAG, "Unknown action: $action")
          false
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to handle action '$action'", e)
      false
    }
  }

  private fun handleSendEmail(context: Context, parameters: String): Boolean {
    val moshi = Moshi.Builder().build()
    val params = moshi.adapter(SendEmailParams::class.java).fromJson(parameters) ?: return false
    val intent = Intent(Intent.ACTION_SEND).apply {
      data = "mailto:".toUri()
      type = "text/plain"
      putExtra(Intent.EXTRA_EMAIL, arrayOf(params.extra_email))
      putExtra(Intent.EXTRA_SUBJECT, params.extra_subject)
      putExtra(Intent.EXTRA_TEXT, params.extra_text)
    }
    context.startActivity(intent)
    return true
  }

  private fun handleSendSms(context: Context, parameters: String): Boolean {
    val moshi = Moshi.Builder().build()
    val params = moshi.adapter(SendSmsParams::class.java).fromJson(parameters) ?: return false
    val intent = Intent(Intent.ACTION_SENDTO, "smsto:${params.phone_number}".toUri()).apply {
      putExtra("sms_body", params.sms_body)
    }
    context.startActivity(intent)
    return true
  }

  private fun handleFlashlight(context: Context, enabled: Boolean): Boolean {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
      cameraManager.getCameraCharacteristics(id)
        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    } ?: return false
    cameraManager.setTorchMode(cameraId, enabled)
    return true
  }

  private fun handleCreateContact(context: Context, parameters: String): Boolean {
    val moshi = Moshi.Builder().build()
    val params = moshi.adapter(CreateContactParams::class.java).fromJson(parameters) ?: return false
    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
      type = ContactsContract.RawContacts.CONTENT_TYPE
      putExtra(ContactsContract.Intents.Insert.NAME, params.name)
      if (params.email.isNotBlank()) {
        putExtra(ContactsContract.Intents.Insert.EMAIL, params.email)
        putExtra(ContactsContract.Intents.Insert.EMAIL_TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
      }
      if (params.phone.isNotBlank()) {
        putExtra(ContactsContract.Intents.Insert.PHONE, params.phone)
        putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
      }
    }
    context.startActivity(intent)
    return true
  }

  private fun handleShowLocation(context: Context, parameters: String): Boolean {
    val moshi = Moshi.Builder().build()
    val params = moshi.adapter(ShowLocationParams::class.java).fromJson(parameters) ?: return false
    val encoded = URLEncoder.encode(params.location, StandardCharsets.UTF_8.toString())
    val intent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=$encoded".toUri())
    context.startActivity(intent)
    return true
  }

  private fun handleOpenWifiSettings(context: Context): Boolean {
    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    return true
  }

  private fun handleCreateCalendarEvent(context: Context, parameters: String): Boolean {
    val moshi = Moshi.Builder().build()
    val params = moshi.adapter(CalendarEventParams::class.java).fromJson(parameters) ?: return false
    var ms = System.currentTimeMillis()
    try {
      val ldt = LocalDateTime.parse(params.datetime)
      ms = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to parse datetime: '${params.datetime}'", e)
    }
    val intent = Intent(Intent.ACTION_INSERT).apply {
      data = CalendarContract.Events.CONTENT_URI
      putExtra(CalendarContract.Events.TITLE, params.title)
      putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ms)
      putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ms + 3600000)
    }
    context.startActivity(intent)
    return true
  }
}
