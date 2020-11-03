package de.mintware.barcode_scan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result

class BarcodeScannerActivity : Activity(), ZXingScannerView.ResultHandler {

    init {
        title = ""
    }

    private lateinit var config: Protos.Configuration
    private var scannerView: ZXingScannerView? = null

    companion object {
        const val EXTRA_CONFIG = "config"
        const val EXTRA_RESULT = "scan_result"
        const val EXTRA_ERROR_CODE = "error_code"

        private val formatMap: Map<Protos.BarcodeFormat, BarcodeFormat> = mapOf(
                Protos.BarcodeFormat.aztec to BarcodeFormat.AZTEC,
                Protos.BarcodeFormat.code39 to BarcodeFormat.CODE_39,
                Protos.BarcodeFormat.code93 to BarcodeFormat.CODE_93,
                Protos.BarcodeFormat.code128 to BarcodeFormat.CODE_128,
                Protos.BarcodeFormat.dataMatrix to BarcodeFormat.DATA_MATRIX,
                Protos.BarcodeFormat.ean8 to BarcodeFormat.EAN_8,
                Protos.BarcodeFormat.ean13 to BarcodeFormat.EAN_13,
                Protos.BarcodeFormat.interleaved2of5 to BarcodeFormat.ITF,
                Protos.BarcodeFormat.pdf417 to BarcodeFormat.PDF_417,
                Protos.BarcodeFormat.qr to BarcodeFormat.QR_CODE,
                Protos.BarcodeFormat.upce to BarcodeFormat.UPC_E
        )

    }

    // region Activity lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = Protos.Configuration.parseFrom(intent.extras!!.getByteArray(EXTRA_CONFIG))
    }

    private fun setupScannerView() {
        if (scannerView != null) {
            return
        }
        scannerView = ZXingAutofocusScannerView(this, config.stringsMap["hint"].orEmpty()).apply {
            setAutoFocus(config.android.useAutoFocus)
            val restrictedFormats = mapRestrictedBarcodeTypes()
            if (restrictedFormats.isNotEmpty()) {
                setFormats(restrictedFormats)
            }

            // this parameter will make your HUAWEI phone works great!
            setAspectTolerance(config.android.aspectTolerance.toFloat())
            if (config.autoEnableFlash) {
                flash = config.autoEnableFlash
                invalidateOptionsMenu()
            }
        }

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.camera, null, false) as ConstraintLayout

        view.findViewById<TextView>(R.id.tvTitle).text = config.stringsMap["title"]
        view.findViewById<ImageView>(R.id.ivTorch).apply {
            setOnClickListener {
                val isTorchOn = scannerView?.flash == true
                scannerView?.flash = !isTorchOn

                val source = if (!isTorchOn) {
                    R.drawable.ic_torch_off
                } else {
                    R.drawable.ic_torch_on
                }
                setImageResource(source)
            }
        }

        view.findViewById<TextView>(R.id.tvCancel).apply {
            text = config.stringsMap["cancel"]
            setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
        }

        view.addView(scannerView, 0)
        setContentView(view)
    }

    override fun onPause() {
        super.onPause()
        scannerView?.stopCamera()
    }

    override fun onResume() {
        super.onResume()
        setupScannerView()
        scannerView?.setResultHandler(this)
        if (config.useCamera > -1) {
            scannerView?.startCamera(config.useCamera)
        } else {
            scannerView?.startCamera()
        }
    }
    // endregion

    override fun handleResult(result: Result?) {
        val intent = Intent()

        val builder = Protos.ScanResult.newBuilder()
        if (result == null) {

            builder.let {
                it.format = Protos.BarcodeFormat.unknown
                it.rawContent = "No data was scanned"
                it.type = Protos.ResultType.Error
            }
        } else {

            val format = (formatMap.filterValues { it == result.barcodeFormat }.keys.firstOrNull()
                    ?: Protos.BarcodeFormat.unknown)

            var formatNote = ""
            if (format == Protos.BarcodeFormat.unknown) {
                formatNote = result.barcodeFormat.toString()
            }

            builder.let {
                it.format = format
                it.formatNote = formatNote
                it.rawContent = result.text
                it.type = Protos.ResultType.Barcode
            }
        }
        val res = builder.build()
        intent.putExtra(EXTRA_RESULT, res.toByteArray())
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun mapRestrictedBarcodeTypes(): List<BarcodeFormat> {
        val types: MutableList<BarcodeFormat> = mutableListOf()

        this.config.restrictFormatList.filterNotNull().forEach {
            if (!formatMap.containsKey(it)) {
                print("Unrecognized")
                return@forEach
            }

            types.add(formatMap.getValue(it))
        }

        return types
    }
}
