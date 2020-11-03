package de.mintware.barcode_scan

import android.content.Context
import android.content.res.Configuration
import android.hardware.Camera
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import me.dm7.barcodescanner.core.BarcodeScannerView
import me.dm7.barcodescanner.core.DisplayUtils
import java.util.*

open class ZXingScannerView : BarcodeScannerView {
    interface ResultHandler {
        fun handleResult(rawResult: Result?)
    }

    private var mMultiFormatReader: MultiFormatReader? = null
    private var mFormats: List<BarcodeFormat>? = null
    private var mResultHandler: ResultHandler? = null

    companion object {
        private const val TAG = "ZXingScannerView"
        val ALL_FORMATS: MutableList<BarcodeFormat> = ArrayList()

        init {
            ALL_FORMATS.add(BarcodeFormat.AZTEC)
            ALL_FORMATS.add(BarcodeFormat.CODABAR)
            ALL_FORMATS.add(BarcodeFormat.CODE_39)
            ALL_FORMATS.add(BarcodeFormat.CODE_93)
            ALL_FORMATS.add(BarcodeFormat.CODE_128)
            ALL_FORMATS.add(BarcodeFormat.DATA_MATRIX)
            ALL_FORMATS.add(BarcodeFormat.EAN_8)
            ALL_FORMATS.add(BarcodeFormat.EAN_13)
            ALL_FORMATS.add(BarcodeFormat.ITF)
            ALL_FORMATS.add(BarcodeFormat.MAXICODE)
            ALL_FORMATS.add(BarcodeFormat.PDF_417)
            ALL_FORMATS.add(BarcodeFormat.QR_CODE)
            ALL_FORMATS.add(BarcodeFormat.RSS_14)
            ALL_FORMATS.add(BarcodeFormat.RSS_EXPANDED)
            ALL_FORMATS.add(BarcodeFormat.UPC_A)
            ALL_FORMATS.add(BarcodeFormat.UPC_E)
            ALL_FORMATS.add(BarcodeFormat.UPC_EAN_EXTENSION)
        }
    }

    constructor(context: Context?) : super(context) {
        initMultiFormatReader()
    }

    constructor(context: Context?, hint: String?) : super(context, hint) {
        initMultiFormatReader()
    }

    constructor(context: Context?, attributeSet: AttributeSet?) : super(context, attributeSet) {
        initMultiFormatReader()
    }

    fun setFormats(formats: List<BarcodeFormat>?) {
        mFormats = formats
        initMultiFormatReader()
    }

    fun setResultHandler(resultHandler: ResultHandler?) {
        mResultHandler = resultHandler
    }

    val formats: Collection<BarcodeFormat>
        get() = if (mFormats == null) {
            ALL_FORMATS
        } else mFormats as List<BarcodeFormat>

    private fun initMultiFormatReader() {
        val hints: MutableMap<DecodeHintType, Any?> = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
        hints[DecodeHintType.POSSIBLE_FORMATS] = formats
        mMultiFormatReader = MultiFormatReader()
        mMultiFormatReader!!.setHints(hints)
    }

    override fun onPreviewFrame(data: ByteArray, camera: Camera) {
        var data: ByteArray? = data
        if (mResultHandler == null) {
            return
        }
        try {
            val parameters = camera.parameters
            val size = parameters.previewSize
            var width = size.width
            var height = size.height
            if (DisplayUtils.getScreenOrientation(context) == Configuration.ORIENTATION_PORTRAIT) {
                val rotationCount = rotationCount
                if (rotationCount == 1 || rotationCount == 3) {
                    val tmp = width
                    width = height
                    height = tmp
                }
                data = getRotatedData(data, camera)
            }
            var rawResult: Result? = null
            val source = buildLuminanceSource(data, width, height)
            if (source != null) {
                var bitmap = BinaryBitmap(HybridBinarizer(source))
                try {
                    rawResult = mMultiFormatReader!!.decodeWithState(bitmap)
                } catch (re: ReaderException) {
                    // continue
                } catch (npe: NullPointerException) {
                    // This is terrible
                } catch (aoe: ArrayIndexOutOfBoundsException) {
                } finally {
                    mMultiFormatReader!!.reset()
                }
                if (rawResult == null) {
                    val invertedSource = source.invert()
                    bitmap = BinaryBitmap(HybridBinarizer(invertedSource))
                    try {
                        rawResult = mMultiFormatReader!!.decodeWithState(bitmap)
                    } catch (e: NotFoundException) {
                        // continue
                    } finally {
                        mMultiFormatReader!!.reset()
                    }
                }
            }
            val finalRawResult = rawResult
            if (finalRawResult != null) {
                val handler = Handler(Looper.getMainLooper())
                handler.post { // Stopping the preview can take a little long.
                    // So we want to set result handler to null to discard subsequent calls to
                    // onPreviewFrame.
                    val tmpResultHandler = mResultHandler
                    mResultHandler = null
                    stopCameraPreview()
                    tmpResultHandler?.handleResult(finalRawResult)
                }
            } else {
                camera.setOneShotPreviewCallback(this)
            }
        } catch (e: RuntimeException) {
            // TODO: Terrible hack. It is possible that this method is invoked after camera is released.
            Log.e(TAG, e.toString(), e)
        }
    }

    fun resumeCameraPreview(resultHandler: ResultHandler?) {
        mResultHandler = resultHandler
        super.resumeCameraPreview()
    }

    fun buildLuminanceSource(data: ByteArray?, width: Int, height: Int): PlanarYUVLuminanceSource? {
        val rect = getFramingRectInPreview(width, height) ?: return null
        // Go ahead and assume it's YUV rather than die.
        var source: PlanarYUVLuminanceSource? = null
        try {
            source = PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
                    rect.width(), rect.height(), false)
        } catch (e: Exception) {
        }
        return source
    }
}