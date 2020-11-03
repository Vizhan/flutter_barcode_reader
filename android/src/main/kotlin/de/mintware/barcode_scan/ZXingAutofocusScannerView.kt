package de.mintware.barcode_scan

import android.content.Context
import android.hardware.Camera
import androidx.annotation.Nullable
import me.dm7.barcodescanner.core.CameraWrapper

class ZXingAutofocusScannerView constructor(context: Context, @Nullable hint: String) : ZXingScannerView(context, hint) {
    constructor(context: Context) : this(context, "")

    private var callbackFocus = false
    private var autofocusPresence = false

    override fun setupCameraPreview(cameraWrapper: CameraWrapper?) {
        cameraWrapper?.mCamera?.parameters?.let { parameters ->
            try {
                autofocusPresence = parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO);
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                cameraWrapper.mCamera.parameters = parameters
            } catch (ex: Exception) {
                callbackFocus = true
            }
        }
        super.setupCameraPreview(cameraWrapper)
    }

    override fun setAutoFocus(state: Boolean) {
        //Fix to avoid crash on devices without autofocus (Issue #226)
        if (autofocusPresence) {
            super.setAutoFocus(callbackFocus)
        }
    }
}