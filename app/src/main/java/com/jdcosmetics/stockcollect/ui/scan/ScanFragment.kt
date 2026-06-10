package com.jdcosmetics.stockcollect.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController

import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.jdcosmetics.stockcollect.R
import com.jdcosmetics.stockcollect.databinding.FragmentScanBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) demarrerCamera()
        else binding.tvPermissionRefusee.isVisible = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupListeners()
        observeViewModel()
        verifierPermissionCamera()
    }

    private fun setupListeners() {
        binding.etCodeBarre.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val cb = binding.etCodeBarre.text?.toString()?.trim() ?: ""
                if (cb.isNotBlank()) viewModel.onSaisieManuelle(cb)
                true
            } else false
        }

        binding.btnRechercheManuele.setOnClickListener {
            val dialog = RechercheArticleDialogFragment()
            dialog.show(parentFragmentManager, "RechercheArticle")
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { event ->
            event.consume()?.let { state ->
                when (state) {
                    is ScanUiState.Loading -> {
                        binding.progressScan.isVisible = true
                    }
                    is ScanUiState.Resolu -> {
                        binding.progressScan.isVisible = false
                        vibrer()
                        val action = ScanFragmentDirections
                            .actionScanToResultat(state.result.codeBarre)
                        findNavController().navigate(action)
                    }
                    is ScanUiState.NonTrouve -> {
                        binding.progressScan.isVisible = false
                        vibrer(longue = true)
                        val action = ScanFragmentDirections
                            .actionScanToResultat(state.codeBarre)
                        findNavController().navigate(action)
                    }
                    else -> binding.progressScan.isVisible = false
                }
            }
        }
    }

    private fun verifierPermissionCamera() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED -> demarrerCamera()
            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun demarrerCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            if (_binding == null) return@addListener
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        analyserImage(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun analyserImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let { codeBarre ->
                    viewModel.onCodeBarreDetecte(codeBarre)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun vibrer(longue: Boolean = false) {
        val vibrator = requireContext().getSystemService(Vibrator::class.java)
        val duree = if (longue) 300L else 80L
        vibrator?.vibrate(VibrationEffect.createOneShot(duree, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}
