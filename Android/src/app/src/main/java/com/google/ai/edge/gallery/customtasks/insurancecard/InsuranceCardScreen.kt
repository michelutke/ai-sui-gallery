package com.google.ai.edge.gallery.customtasks.insurancecard

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val TAG = "AGInsuranceCard"

private const val LLM_PROMPT =
  """Extract fields from this Swiss insurance card OCR text. Reply with ONLY a short JSON object, no markdown, no explanation. Keep values short. If a field is not found, use empty string.
Example output: {"name":"Muster","vorname":"Max","geburtsdatum":"01.01.1990","versichertennummer":"","ahvNummer":"756.1234.5678.90","versicherer":"CSS","kartenNummer":""}
OCR text: """

private enum class ScanState {
  CAMERA,
  OCR_PROCESSING,
  LLM_PROCESSING,
  RESULT,
  ERROR,
}

@Composable
fun InsuranceCardScreen(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  bottomPadding: Dp,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val model = modelManagerUiState.selectedModel
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[model.name]?.status

  if (curDownloadStatus != ModelDownloadStatusType.SUCCEEDED ||
    !modelManagerUiState.isModelInitialized(model = model)
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
          strokeWidth = 3.dp,
          modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
          stringResource(R.string.model_is_initializing_msg),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    return
  }

  CameraPermissionGate {
    InsuranceCardContent(
      modelManagerViewModel = modelManagerViewModel,
      bottomPadding = bottomPadding,
    )
  }
}

@Composable
private fun CameraPermissionGate(content: @Composable () -> Unit) {
  val context = LocalContext.current
  var permissionGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val launcher =
    androidx.activity.compose.rememberLauncherForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
      permissionGranted = granted
    }

  if (permissionGranted) {
    content()
  } else {
    Column(
      modifier = Modifier.fillMaxSize().wrapContentSize().widthIn(max = 480.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        stringResource(R.string.insurance_camera_permission),
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(16.dp))
      Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
        Text(stringResource(R.string.grant_permissions))
      }
    }
  }
}

@Composable
private fun InsuranceCardContent(
  modelManagerViewModel: ModelManagerViewModel,
  bottomPadding: Dp,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val model = modelManagerUiState.selectedModel
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()

  var scanState by remember { mutableStateOf(ScanState.CAMERA) }
  var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var ocrText by remember { mutableStateOf("") }
  var cardResult by remember { mutableStateOf<InsuranceCardResult?>(null) }
  var errorMessage by remember { mutableStateOf("") }

  val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
  val imageCapture = remember { ImageCapture.Builder().build() }
  val preview = remember { Preview.Builder().build() }
  var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

  LaunchedEffect(Unit) {
    cameraProvider = ProcessCameraProvider.awaitInstance(context.applicationContext)
  }

  DisposableEffect(Unit) {
    onDispose {
      cameraProvider?.unbindAll()
      cameraExecutor.shutdown()
    }
  }

  fun resetToCamera() {
    scanState = ScanState.CAMERA
    capturedBitmap = null
    ocrText = ""
    cardResult = null
    errorMessage = ""
  }

  fun captureAndProcess() {
    scanState = ScanState.OCR_PROCESSING
    imageCapture.takePicture(
      cameraExecutor,
      object : ImageCapture.OnImageCapturedCallback() {
        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun onCaptureSuccess(image: ImageProxy) {
          try {
            val bitmap = imageProxyToBitmap(image)
            val rotated = bitmap.rotate(image.imageInfo.rotationDegrees.toFloat())
            val cropped = cropToCardRegion(rotated)
            image.close()

            scope.launch {
              capturedBitmap = cropped
              // OCR
              recognizeText(
                cropped,
                onSuccess = { text ->
                  ocrText = text
                  if (text.isBlank()) {
                    errorMessage = "No text recognized from the card."
                    scanState = ScanState.ERROR
                    return@recognizeText
                  }
                  // LLM structuring
                  scanState = ScanState.LLM_PROCESSING
                  // Reset conversation to avoid context buildup
                  LlmChatModelHelper.resetConversation(model = model)
                  val llmAccumulator = StringBuilder()
                  var resultHandled = false
                  LlmChatModelHelper.runInference(
                    model = model,
                    input = LLM_PROMPT + text,
                    resultListener = { partialResult, done ->
                      if (resultHandled) return@runInference
                      llmAccumulator.append(partialResult)
                      // Try to parse early when we see closing brace
                      val soFar = llmAccumulator.toString()
                      val hasCompleteJson = soFar.contains("{") && soFar.contains("}")
                      if (done || hasCompleteJson) {
                        resultHandled = true
                        scope.launch {
                          Log.d(TAG, "LLM response: $soFar")
                          val result = parseResult(soFar)
                          if (result != null) {
                            cardResult = result
                            scanState = ScanState.RESULT
                          } else {
                            errorMessage = "Could not parse LLM response.\n\nRaw: $soFar"
                            scanState = ScanState.ERROR
                          }
                        }
                      }
                    },
                    cleanUpListener = {},
                    onError = { msg ->
                      scope.launch {
                        errorMessage = msg
                        scanState = ScanState.ERROR
                      }
                    },
                  )
                },
                onFailure = { e ->
                  scope.launch {
                    errorMessage = "OCR failed: ${e.message}"
                    scanState = ScanState.ERROR
                  }
                },
              )
            }
          } catch (e: Exception) {
            image.close()
            scope.launch {
              errorMessage = "Capture processing failed: ${e.message}"
              scanState = ScanState.ERROR
            }
          }
        }

        override fun onError(exception: ImageCaptureException) {
          Log.e(TAG, "Capture failed", exception)
          scope.launch {
            errorMessage = "Photo capture failed: ${exception.message}"
            scanState = ScanState.ERROR
          }
        }
      },
    )
  }

  Column(
    modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding),
  ) {
    when (scanState) {
      ScanState.CAMERA -> {
        Box(modifier = Modifier.weight(1f)) {
          CameraPreview(
            preview = preview,
            imageCapture = imageCapture,
            cameraProvider = cameraProvider,
            lifecycleOwner = lifecycleOwner,
          )
          // Card overlay guide
          val screenWidthDp = LocalConfiguration.current.screenWidthDp
          val overlayWidthDp = screenWidthDp - 64f
          val overlayHeightDp = overlayWidthDp * 4f / 13f
          Box(
            modifier =
              Modifier.align(Alignment.Center)
                .width(overlayWidthDp.dp)
                .height(overlayHeightDp.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary),
          )
        }
        Box(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          contentAlignment = Alignment.Center,
        ) {
          FloatingActionButton(
            onClick = { captureAndProcess() },
            shape = CircleShape,
          ) {
            Icon(
              imageVector = Icons.Default.Camera,
              contentDescription = stringResource(R.string.insurance_capture),
            )
          }
        }
      }

      ScanState.OCR_PROCESSING -> {
        ProcessingView(
          bitmap = capturedBitmap,
          statusText = stringResource(R.string.insurance_scanning),
        )
      }

      ScanState.LLM_PROCESSING -> {
        ProcessingView(
          bitmap = capturedBitmap,
          statusText = stringResource(R.string.insurance_analyzing),
        )
      }

      ScanState.RESULT -> {
        ResultView(
          result = cardResult,
          bitmap = capturedBitmap,
          onRetake = { resetToCamera() },
        )
      }

      ScanState.ERROR -> {
        ErrorView(
          message = errorMessage,
          onRetake = { resetToCamera() },
        )
      }
    }
  }
}

@Composable
private fun CameraPreview(
  preview: Preview,
  imageCapture: ImageCapture,
  cameraProvider: ProcessCameraProvider?,
  lifecycleOwner: androidx.lifecycle.LifecycleOwner,
) {
  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      PreviewView(ctx).apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
        scaleType = PreviewView.ScaleType.FILL_CENTER
      }
    },
    update = { previewView ->
      cameraProvider?.let { provider ->
        try {
          provider.unbindAll()
          preview.surfaceProvider = previewView.surfaceProvider
          provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture,
          )
        } catch (e: Exception) {
          Log.e(TAG, "Camera bind failed", e)
        }
      }
    },
  )
}

@Composable
private fun ProcessingView(bitmap: Bitmap?, statusText: String) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    bitmap?.let {
      Image(
        bitmap = it.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        contentScale = ContentScale.FillWidth,
      )
      Spacer(Modifier.height(24.dp))
    }
    CircularProgressIndicator(
      trackColor = MaterialTheme.colorScheme.surfaceVariant,
      strokeWidth = 3.dp,
      modifier = Modifier.size(32.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text(statusText, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
private fun ResultView(
  result: InsuranceCardResult?,
  bitmap: Bitmap?,
  onRetake: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    bitmap?.let {
      Image(
        bitmap = it.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        contentScale = ContentScale.FillWidth,
      )
    }

    result?.let { r ->
      val validation = remember(r) { InsuranceCardValidator.validate(r) }

      // Overall status
      Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
          containerColor = if (validation.allValid)
            MaterialTheme.colorScheme.primaryContainer
          else
            MaterialTheme.colorScheme.errorContainer
        ),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            if (validation.allValid) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (validation.allValid)
              MaterialTheme.colorScheme.primary
            else
              MaterialTheme.colorScheme.error,
          )
          Spacer(Modifier.width(8.dp))
          Text(
            if (validation.allValid) "Alle Pflichtfelder gültig" else "Einige Felder ungültig",
            style = MaterialTheme.typography.titleSmall,
          )
        }
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            stringResource(R.string.insurance_task_label),
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(Modifier.height(4.dp))
          ValidatedFieldRow(stringResource(R.string.insurance_field_versicherer), r.versicherer, validation.versicherer)
          ValidatedFieldRow(stringResource(R.string.insurance_field_name), r.name, validation.name)
          ValidatedFieldRow(stringResource(R.string.insurance_field_vorname), r.vorname, validation.vorname)
          ValidatedFieldRow(stringResource(R.string.insurance_field_geburtsdatum), r.geburtsdatum, validation.geburtsdatum)
          ValidatedFieldRow(stringResource(R.string.insurance_field_versichertennummer), r.versichertennummer, validation.versichertennummer)
          ValidatedFieldRow(stringResource(R.string.insurance_field_ahv_nummer), r.ahvNummer, validation.ahvNummer)
          ValidatedFieldRow(stringResource(R.string.insurance_field_kartennummer), r.kartenNummer, validation.kartenNummer)
        }
      }
    }

    Spacer(Modifier.height(16.dp))
    TextButton(onClick = onRetake) { Text(stringResource(R.string.insurance_retake)) }
  }
}

@Composable
private fun ValidatedFieldRow(label: String, value: String, validation: FieldValidation) {
  if (value.isBlank() && validation.isValid) return // skip empty optional fields
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(6.dp))
        Icon(
          imageVector = when {
            !validation.isValid -> Icons.Default.Error
            validation.message.isNotEmpty() -> Icons.Default.Warning
            else -> Icons.Default.CheckCircle
          },
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = when {
            !validation.isValid -> MaterialTheme.colorScheme.error
            validation.message.isNotEmpty() -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
          },
        )
      }
    }
    if (validation.message.isNotEmpty()) {
      Text(
        validation.message,
        style = MaterialTheme.typography.bodySmall,
        color = if (validation.isValid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
  }
}

@Composable
private fun ErrorView(message: String, onRetake: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().wrapContentSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(stringResource(R.string.error), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(message, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
    Spacer(Modifier.height(16.dp))
    Button(onClick = onRetake) { Text(stringResource(R.string.insurance_retake)) }
  }
}

// --- Utility functions ---

private fun recognizeText(
  bitmap: Bitmap,
  onSuccess: (String) -> Unit,
  onFailure: (Exception) -> Unit,
) {
  val inputImage = InputImage.fromBitmap(bitmap, 0)
  val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
  recognizer
    .process(inputImage)
    .addOnSuccessListener { visionText -> onSuccess(visionText.text) }
    .addOnFailureListener { e -> onFailure(e) }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
  if (image.format == ImageFormat.JPEG && image.planes.size == 1) {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
  }
  if (image.format == ImageFormat.YUV_420_888 && image.planes.size == 3) {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val yuvImage = android.graphics.YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
    val yuvBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(yuvBytes, 0, yuvBytes.size)
  }
  throw IllegalArgumentException("Unsupported ImageProxy format: ${image.format}")
}

private fun Bitmap.rotate(degrees: Float): Bitmap {
  if (degrees == 0f) return this
  val matrix = Matrix()
  matrix.postRotate(degrees)
  return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun cropToCardRegion(bitmap: Bitmap): Bitmap {
  val targetAspect = 13f / 4f
  var cropWidth = bitmap.width
  var cropHeight = (cropWidth / targetAspect).toInt()
  if (cropHeight > bitmap.height) {
    cropHeight = bitmap.height
    cropWidth = (cropHeight * targetAspect).toInt()
  }
  val left = (bitmap.width - cropWidth) / 2
  val top = (bitmap.height - cropHeight) / 2
  return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
}

private fun parseResult(llmResponse: String): InsuranceCardResult? {
  val jsonStr = extractJson(llmResponse)
  if (jsonStr != null) {
    try {
      val json = JSONObject(jsonStr)
      return InsuranceCardResult(
        name = json.optString("name", ""),
        vorname = json.optString("vorname", ""),
        geburtsdatum = json.optString("geburtsdatum", ""),
        versichertennummer = json.optString("versichertennummer", ""),
        ahvNummer = json.optString("ahvNummer", ""),
        versicherer = json.optString("versicherer", ""),
        kartenNummer = json.optString("kartenNummer", ""),
      )
    } catch (e: Exception) {
      Log.e(TAG, "JSON parse error", e)
    }
  }
  return null
}

private fun extractJson(text: String): String? {
  // Find first { and last }
  val start = text.indexOf('{')
  val end = text.lastIndexOf('}')
  if (start >= 0 && end > start) {
    return text.substring(start, end + 1)
  }
  return null
}
